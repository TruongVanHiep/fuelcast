package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.forecast.BacktestService.Period;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter.ReplayRow;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Observation;
import fitiuh.com.fuelcast_core.forecast.PythonForecastClient.BacktestStep;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PythonReplayServiceTest {

    private static final OffsetDateTime AT = OffsetDateTime.of(2026, 3, 12, 15, 0, 0, 0, ZoneOffset.ofHours(7));
    private static final FuelProduct PRODUCT = FuelProduct.builder().id((short) 2).code("RON95")
            .nameVi("Xăng RON95").unit("lít").displayOrder((short) 2).build();

    /** Kỳ: giá kỳ trước 20.000, kỳ này 20.300 (đổi +300). */
    private static Period period() {
        return new Period(new Observation(AT, 300, 2.0), 77L, new BigDecimal("20300"), new BigDecimal("20000"));
    }

    private static BacktestStep step(double predicted, double rule) {
        return new BacktestStep(AT.toString(), 300, rule, predicted, 25, true);
    }

    @Test
    void storesTheStepAsAGradedReplayRow() {
        ReplayRow row = PythonReplayService.toRow(step(250, 210), period(), PRODUCT, "rule-lgbm-residual-v1");

        assertThat(row.cycleId()).isEqualTo(77L);
        assertThat(row.productId()).isEqualTo((short) 2);
        assertThat(row.createdAt()).isEqualTo(AT);
        assertThat(row.modelVersion()).isEqualTo("rule-lgbm-residual-v1");
        assertThat(row.predictedDelta()).isEqualByComparingTo("250.00");
        assertThat(row.predictedVnd()).isEqualByComparingTo("20250.00");      // giá kỳ trước + mức đổi dự đoán
        assertThat(row.actualVnd()).isEqualByComparingTo("20300");
        assertThat(row.absError()).isEqualByComparingTo("50.00");             // |20300 - 20250|
        assertThat(row.pctError()).isEqualByComparingTo("0.2463");            // 50 / 20300 * 100
        assertThat(row.direction()).isEqualTo("UP");
        assertThat(row.directionHit()).isTrue();
    }

    /**
     * Horizon 7, không phải 0: mô hình dùng giá thế giới của kỳ TRƯỚC, thứ biết từ khoảng một
     * kỳ trước. Horizon 0 dành riêng cho "giá thế giới cùng kỳ" của mô hình Java, thứ chỉ có
     * khi đã biết kết quả; ghi 0 ở đây là gắn nhãn sai và làm bảng điểm đọc nhầm là giải thích.
     */
    @Test
    void usesHorizonSevenNotZero() {
        ReplayRow row = PythonReplayService.toRow(step(250, 210), period(), PRODUCT, "v");

        assertThat(row.horizonDays()).isEqualTo(7).isNotEqualTo(ForecastReplayService.HORIZON_DAYS);
    }

    @Test
    void aWrongDirectionIsNotAHit() {
        ReplayRow row = PythonReplayService.toRow(step(-80, -60), period(), PRODUCT, "v");

        assertThat(row.direction()).isEqualTo("DOWN");
        assertThat(row.directionHit()).isFalse();
    }

    @Test
    void explanationSplitsTheRuleFromTheResidualAndAddsUp() {
        ReplayRow row = PythonReplayService.toRow(step(250, 210), period(), PRODUCT, "v");

        assertThat(row.explanationJson())
                .contains("\"rule_effect\":210.0")
                .contains("\"residual_model\":40.0")
                .contains("\"net_delta\":250.0");
        assertThat(row.featuresJson()).contains("\"mode\":\"lagged\"").contains("\"trainRows\":25")
                .contains("\"usedResidual\":true");
    }
}
