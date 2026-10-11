package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.RetailPriceId;
import fitiuh.com.fuelcast_core.entity.WorldPrice;
import fitiuh.com.fuelcast_core.entity.WorldPriceId;
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

    private static RetailPrice retail(OffsetDateTime at, String deltaVnd) {
        return new RetailPrice(new RetailPriceId(at, (short) 1, (short) 1, (short) 1),
                new BigDecimal("20000"), deltaVnd == null ? null : new BigDecimal(deltaVnd), 1L);
    }

    private static WorldPrice world(OffsetDateTime at, String usd) {
        return new WorldPrice(new WorldPriceId(at, "MOPS_RON92"), new BigDecimal(usd), "USD/thùng", "MOIT_CYCLE_AVG");
    }

    @Test
    void buildsDeltasBetweenConsecutiveWeeklyPeriods() {
        List<RetailPrice> retail = List.of(
                retail(T0, "10"), retail(T0.plusWeeks(1), "340"), retail(T0.plusWeeks(2), "-120"));
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
        List<RetailPrice> retail = List.of(retail(T0, "10"), retail(t1, "340"));
        List<WorldPrice> world = List.of(
                world(T0.withOffsetSameInstant(ZoneOffset.UTC), "80.0"),
                world(t1.withOffsetSameInstant(ZoneOffset.UTC), "82.0"));

        assertThat(BacktestService.alignObservations(retail, world)).hasSize(1);
    }

    /**
     * Thiếu bản tin giữa chừng (khoảng cách 4 tuần): Δ giá thế giới qua khoảng
     * trống không còn cùng nghĩa với Δ giá bán lẻ, nên cặp đó phải bị bỏ. Cặp liền
     * kề sau khoảng trống thì giữ lại.
     */
    @Test
    void dropsThePairThatSpansAGapButKeepsTheNextOne() {
        OffsetDateTime afterGap = T0.plusWeeks(5);                 // cách T0 35 ngày
        List<RetailPrice> retail = List.of(
                retail(T0, "10"), retail(afterGap, "500"), retail(afterGap.plusWeeks(1), "60"));
        List<WorldPrice> world = List.of(
                world(T0, "80.0"), world(afterGap, "90.0"), world(afterGap.plusWeeks(1), "91.0"));

        List<Observation> out = BacktestService.alignObservations(retail, world);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).deltaVnd()).isEqualTo(60.0);
    }

    @Test
    void skipsAPeriodWhoseRetailDeltaIsUnknownButKeepsTheChain() {
        List<RetailPrice> retail = List.of(
                retail(T0, "10"), retail(T0.plusWeeks(1), null), retail(T0.plusWeeks(2), "200"));
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
        List<RetailPrice> retail = List.of(retail(T0, "10"), retail(T0.plusWeeks(1), "340"));

        assertThat(BacktestService.alignObservations(retail, List.of())).isEmpty();
    }

    @Test
    void mapsEveryRetailProductToItsWorldSymbol() {
        assertThat(WorldSymbols.forProduct("RON95")).contains("MOPS_RON95");
        assertThat(WorldSymbols.forProduct("FO_180")).contains("MOPS_FO_180");
        assertThat(WorldSymbols.forProduct("UNKNOWN")).isEmpty();
    }
}
