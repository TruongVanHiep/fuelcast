package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.RetailPriceId;
import fitiuh.com.fuelcast_core.entity.WorldPrice;
import fitiuh.com.fuelcast_core.entity.WorldPriceId;
import fitiuh.com.fuelcast_core.forecast.BacktestService.Period;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter.ReplayRow;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Observation;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Step;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import fitiuh.com.fuelcast_core.repository.WorldPriceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForecastReplayServiceTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime T0 = OffsetDateTime.of(2024, 1, 4, 15, 0, 0, 0, VN);

    private final FuelProductRepository products = mock(FuelProductRepository.class);
    private final RetailPriceRepository prices = mock(RetailPriceRepository.class);
    private final WorldPriceRepository worldPrices = mock(WorldPriceRepository.class);
    private final ForecastWriter writer = mock(ForecastWriter.class);

    private final ForecastReplayService service =
            new ForecastReplayService(products, prices, worldPrices, writer);

    // ───────────────────────── dựng một dòng dự báo ─────────────────────────

    private static Step step(double predictedDelta, double actualDelta, double deltaUsd) {
        return new Step(new Observation(T0, actualDelta, deltaUsd), predictedDelta, 170.0, 5.0, 25);
    }

    private static Period period(String previous, String actual) {
        return new Period(null, 77L, new BigDecimal(actual), new BigDecimal(previous));
    }

    private static FuelProduct product() {
        return FuelProduct.builder().id((short) 2).code("RON95").nameVi("Xăng RON95").unit("lít")
                .displayOrder((short) 2).build();
    }

    @Test
    void predictedPriceIsPreviousPricePlusPredictedChange() {
        ReplayRow row = ForecastReplayService.toRow(
                step(300.0, 340.0, 2.0), period("21000.00", "21340.00"), product());

        assertThat(row.predictedDelta()).isEqualByComparingTo("300.00");
        assertThat(row.predictedVnd()).isEqualByComparingTo("21300.00");
        assertThat(row.actualVnd()).isEqualByComparingTo("21340.00");
        assertThat(row.absError()).isEqualByComparingTo("40.00");
        assertThat(row.cycleId()).isEqualTo(77L);
        assertThat(row.productId()).isEqualTo((short) 2);
        assertThat(row.horizonDays()).isZero();
        assertThat(row.modelVersion()).isEqualTo("passthrough-ols-v2");
    }

    /** pct_error = sai số / giá thật · 100, cột NUMERIC(8,4). */
    @Test
    void percentErrorIsRelativeToTheActualPrice() {
        ReplayRow row = ForecastReplayService.toRow(
                step(300.0, 340.0, 2.0), period("20000.00", "20340.00"), product());

        // |20340 − 20300| / 20340 · 100 = 0,1967...
        assertThat(row.pctError()).isEqualByComparingTo("0.1967");
    }

    @Test
    void directionHitComparesSignsOfPredictedAndActualChange() {
        assertThat(ForecastReplayService.toRow(step(300, 340, 2), period("1000", "1340"), product())
                .directionHit()).isTrue();
        assertThat(ForecastReplayService.toRow(step(300, -50, 2), period("1000", "950"), product())
                .directionHit()).isFalse();
        assertThat(ForecastReplayService.toRow(step(-120, -150, -1), period("1000", "850"), product())
                .directionHit()).isTrue();
    }

    @Test
    void directionIsFlatForATinyPredictedChange() {
        assertThat(ForecastReplayService.directionOf(0.4)).isEqualTo("FLAT");
        assertThat(ForecastReplayService.directionOf(-0.99)).isEqualTo("FLAT");
        assertThat(ForecastReplayService.directionOf(1.0)).isEqualTo("UP");
        assertThat(ForecastReplayService.directionOf(-250.0)).isEqualTo("DOWN");
    }

    /** features đủ để dựng lại đúng con số dự báo; explanation tách đóng góp. */
    @Test
    void storesTheInputsAndTheBreakdownAsJson() {
        Step s = step(345.0, 340.0, 2.0);        // 5 + 170·2 = 345

        assertThat(ForecastReplayService.featuresJson(s))
                .isEqualTo("{\"deltaUsd\":2.0,\"slope\":170.0,\"intercept\":5.0,\"trainPeriods\":25}");
        assertThat(ForecastReplayService.explanationJson(s))
                .isEqualTo("{\"world_effect\":340.0,\"intercept\":5.0,\"net_delta\":345.0}");
    }

    // ───────────────────────── luồng phát lại ─────────────────────────

    private static List<RetailPrice> weeklyRetail(int n) {
        List<RetailPrice> out = new ArrayList<>();
        BigDecimal price = new BigDecimal(20000);
        for (int i = 0; i < n; i++) {
            OffsetDateTime at = T0.plusWeeks(i);
            double usd = (i % 5) - 2 + i * 0.1;
            BigDecimal delta = new BigDecimal(Math.round(170 * usd));
            // Giá phải tích luỹ đúng các delta: ghép dữ liệu kiểm tra tính liên tục
            // (mức đổi bản tin = chênh lệch giá) và loại kỳ nào không khớp.
            price = price.add(delta);
            out.add(new RetailPrice(new RetailPriceId(at, (short) 2, (short) 1, (short) 1),
                    price, delta, 100L + i));
        }
        return out;
    }

    private static List<WorldPrice> weeklyWorld(int n) {
        List<WorldPrice> out = new ArrayList<>();
        double usd = 80;
        for (int i = 0; i < n; i++) {
            double d = (i % 5) - 2 + i * 0.1;
            usd += (i == 0 ? 0 : d);
            out.add(new WorldPrice(new WorldPriceId(T0.plusWeeks(i), "MOPS_RON95"),
                    BigDecimal.valueOf(usd), "USD/thùng", "MOIT_CYCLE_AVG"));
        }
        return out;
    }

    @Test
    void replaysEveryPredictableWeekAndCountsInsertedAndExisting() {
        when(products.findAll()).thenReturn(List.of(product()));
        when(prices.findHistory(any(Short.class), any(), any())).thenReturn(weeklyRetail(30));
        when(worldPrices.findByIdSymbolOrderByIdObservedAtAsc("MOPS_RON95")).thenReturn(weeklyWorld(30));
        // dòng đầu đã có sẵn từ lần chạy trước, các dòng còn lại là mới
        when(writer.insertReplay(any())).thenReturn(false, true);

        ForecastReplayService.ReplayResult r = service.replay();

        // 30 kỳ → 29 quan sát (kỳ đầu không có kỳ trước) → bỏ 20 kỳ học đầu → 9 lần đoán
        assertThat(r.products()).isEqualTo(1);
        assertThat(r.inserted() + r.alreadyPresent()).isEqualTo(9);
        assertThat(r.alreadyPresent()).isEqualTo(1);
        assertThat(r.inserted()).isEqualTo(8);

        ArgumentCaptor<ReplayRow> rows = ArgumentCaptor.forClass(ReplayRow.class);
        verify(writer, atLeastOnce()).insertReplay(rows.capture());
        assertThat(rows.getAllValues()).allSatisfy(row -> {
            assertThat(row.horizonDays()).isZero();
            assertThat(row.modelVersion()).isEqualTo(ForecastReplayService.MODEL_VERSION);
            assertThat(row.cycleId()).isGreaterThanOrEqualTo(100L);
        });
    }

    @Test
    void skipsProductsWithNoWorldPriceSymbol() {
        FuelProduct unknown = FuelProduct.builder().id((short) 9).code("MYSTERY").nameVi("?").unit("lít")
                .displayOrder((short) 9).build();
        when(products.findAll()).thenReturn(List.of(unknown));

        ForecastReplayService.ReplayResult r = service.replay();

        assertThat(r.products()).isZero();
        verify(writer, never()).insertReplay(any());
    }
}
