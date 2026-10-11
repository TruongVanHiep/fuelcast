package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.RetailPriceId;
import fitiuh.com.fuelcast_core.entity.WorldPrice;
import fitiuh.com.fuelcast_core.entity.WorldPriceId;
import fitiuh.com.fuelcast_core.forecast.BacktestService.Period;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Observation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BacktestServiceTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime T0 = OffsetDateTime.of(2024, 1, 4, 15, 0, 0, 0, VN);

    /** Giá và delta phải nhất quán với nhau: delta = giá − giá kỳ trước. */
    private static RetailPrice retail(OffsetDateTime at, String priceVnd, String deltaVnd) {
        return new RetailPrice(new RetailPriceId(at, (short) 1, (short) 1, (short) 1),
                new BigDecimal(priceVnd), deltaVnd == null ? null : new BigDecimal(deltaVnd), 1L);
    }

    private static WorldPrice world(OffsetDateTime at, String usd) {
        return new WorldPrice(new WorldPriceId(at, "MOPS_RON92"), new BigDecimal(usd), "USD/thùng", "MOIT_CYCLE_AVG");
    }

    @Test
    void buildsDeltasBetweenConsecutiveWeeklyPeriods() {
        List<RetailPrice> retail = List.of(
                retail(T0, "20000", "10"),
                retail(T0.plusWeeks(1), "20340", "340"),
                retail(T0.plusWeeks(2), "20220", "-120"));
        List<WorldPrice> world = List.of(
                world(T0, "80.0"), world(T0.plusWeeks(1), "82.0"), world(T0.plusWeeks(2), "81.0"));

        List<Observation> out = BacktestService.alignObservations(retail, world);

        // kỳ đầu không có kỳ trước nên không tạo quan sát
        assertThat(out).hasSize(2);
        assertThat(out.get(0).deltaVnd()).isEqualTo(340.0);
        assertThat(out.get(0).deltaUsd()).isCloseTo(2.0, within(1e-9));
        assertThat(out.get(1).deltaVnd()).isEqualTo(-120.0);
        assertThat(out.get(1).deltaUsd()).isCloseTo(-1.0, within(1e-9));
    }

    /**
     * DB trả thời điểm ở offset UTC cho bảng này, +07:00 cho bảng kia. Cùng một
     * khoảnh khắc nhưng OffsetDateTime.equals() là false, nên phải ghép theo Instant.
     */
    @Test
    void matchesRetailAndWorldByInstantNotByOffset() {
        OffsetDateTime t1 = T0.plusWeeks(1);
        List<RetailPrice> retail = List.of(retail(T0, "20000", "10"), retail(t1, "20340", "340"));
        List<WorldPrice> world = List.of(
                world(T0.withOffsetSameInstant(ZoneOffset.UTC), "80.0"),
                world(t1.withOffsetSameInstant(ZoneOffset.UTC), "82.0"));

        assertThat(BacktestService.alignObservations(retail, world)).hasSize(1);
    }

    /**
     * Thiếu bản tin giữa chừng (khoảng cách 5 tuần): Δ giá thế giới qua khoảng
     * trống không còn cùng nghĩa với Δ giá bán lẻ, nên cặp đó phải bị bỏ. Cặp liền
     * kề sau khoảng trống thì giữ lại.
     */
    @Test
    void dropsThePairThatSpansAGapButKeepsTheNextOne() {
        OffsetDateTime afterGap = T0.plusWeeks(5);                 // cách T0 35 ngày
        List<RetailPrice> retail = List.of(
                retail(T0, "20000", "10"),
                retail(afterGap, "20500", "500"),
                retail(afterGap.plusWeeks(1), "20560", "60"));
        List<WorldPrice> world = List.of(
                world(T0, "80.0"), world(afterGap, "90.0"), world(afterGap.plusWeeks(1), "91.0"));

        List<Observation> out = BacktestService.alignObservations(retail, world);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).deltaVnd()).isEqualTo(60.0);
    }

    /**
     * Ca thật (25/3 và 2/7/2026): khoảng cách ngày vẫn trông bình thường nhưng
     * giữa hai kỳ còn một lần điều chỉnh mà ta không có. Dấu hiệu duy nhất là mức
     * đổi bản tin nêu (−1055) không bằng chênh lệch giá trong chuỗi (+371), vì bản
     * tin so với giá của kỳ bị thiếu.
     */
    @Test
    void dropsAPeriodWhoseStatedChangeDisagreesWithThePriceDifference() {
        List<RetailPrice> retail = List.of(
                retail(T0, "19359", "30"),
                retail(T0.plusWeeks(1), "19730", "-1055"),          // 19730 − 19359 = 371 ≠ −1055
                retail(T0.plusWeeks(2), "19760", "30"));            // 19760 − 19730 = 30: liên tục
        List<WorldPrice> world = List.of(
                world(T0, "80.0"), world(T0.plusWeeks(1), "85.0"), world(T0.plusWeeks(2), "86.0"));

        List<Observation> out = BacktestService.alignObservations(retail, world);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).deltaVnd()).isEqualTo(30.0);
        // Δ thế giới của kỳ giữ lại tính từ kỳ liền trước (85 → 86), không phải từ kỳ đầu
        assertThat(out.get(0).deltaUsd()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void skipsAPeriodWhoseRetailDeltaIsUnknownButKeepsTheChain() {
        List<RetailPrice> retail = List.of(
                retail(T0, "20000", "10"),
                retail(T0.plusWeeks(1), "20100", null),
                retail(T0.plusWeeks(2), "20300", "200"));
        List<WorldPrice> world = List.of(
                world(T0, "80.0"), world(T0.plusWeeks(1), "82.0"), world(T0.plusWeeks(2), "85.0"));

        List<Observation> out = BacktestService.alignObservations(retail, world);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).deltaVnd()).isEqualTo(200.0);
        // Δ thế giới tính từ kỳ liền trước (82 → 85), không phải từ kỳ đầu
        assertThat(out.get(0).deltaUsd()).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void ignoresRetailPeriodsThatHaveNoWorldPrice() {
        List<RetailPrice> retail = List.of(retail(T0, "20000", "10"), retail(T0.plusWeeks(1), "20340", "340"));

        assertThat(BacktestService.alignObservations(retail, List.of())).isEmpty();
    }

    /** Bối cảnh của kỳ đi cùng quan sát: cần để lưu một dự báo (kỳ, giá thật, giá kỳ trước). */
    @Test
    void periodCarriesCycleActualPriceAndPreviousPrice() {
        List<RetailPrice> retail = List.of(retail(T0, "20000", "10"), retail(T0.plusWeeks(1), "20340", "340"));
        List<WorldPrice> world = List.of(world(T0, "80.0"), world(T0.plusWeeks(1), "82.0"));

        List<Period> periods = BacktestService.alignPeriods(retail, world);

        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).priceVnd()).isEqualByComparingTo("20340");
        assertThat(periods.get(0).previousPriceVnd()).isEqualByComparingTo("20000");
        assertThat(periods.get(0).cycleId()).isEqualTo(1L);
    }

    @Test
    void mapsEveryRetailProductToItsWorldSymbol() {
        assertThat(WorldSymbols.forProduct("RON95")).contains("MOPS_RON95");
        assertThat(WorldSymbols.forProduct("FO_180")).contains("MOPS_FO_180");
        assertThat(WorldSymbols.forProduct("UNKNOWN")).isEmpty();
    }
}
