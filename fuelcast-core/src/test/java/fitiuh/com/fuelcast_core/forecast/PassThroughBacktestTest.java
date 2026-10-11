package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Observation;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Result;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PassThroughBacktestTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2024, 1, 4, 15, 0, 0, 0, ZoneOffset.ofHours(7));

    /** n kỳ với giá bán lẻ = slope · Δusd + intercept chính xác (không nhiễu). */
    private static List<Observation> exactSeries(int n, double slope, double intercept) {
        List<Observation> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double usd = (i % 7) - 3 + i * 0.1;          // thay đổi, có cả âm lẫn dương
            out.add(new Observation(START.plusWeeks(i), slope * usd + intercept, usd));
        }
        return out;
    }

    @Test
    void anExactRelationshipIsPredictedWithNoError() {
        Result r = PassThroughBacktest.run(exactSeries(60, 170.0, 5.0), 20);

        assertThat(r.nTest()).isEqualTo(40);
        assertThat(r.maeModel()).isCloseTo(0.0, within(1e-6));
        assertThat(r.hitRate()).isEqualTo(1.0);
        assertThat(r.beatsNaive()).isTrue();
        assertThat(r.gain()).isCloseTo(1.0, within(1e-6));
    }

    /**
     * walkForward trả từng lần đoán kèm hệ số đã dùng, để lưu và giải thích được.
     * Trên quan hệ chính xác y = 170x + 5, hệ số học được phải đúng như vậy và
     * số kỳ đã học tăng dần theo từng bước.
     */
    @Test
    void walkForwardReportsTheCoefficientsAndTrainingSizeUsedForEachStep() {
        List<PassThroughBacktest.Step> steps = PassThroughBacktest.walkForward(exactSeries(25, 170.0, 5.0), 20);

        assertThat(steps).hasSize(5);
        assertThat(steps.get(0).trainPeriods()).isEqualTo(20);
        assertThat(steps.get(4).trainPeriods()).isEqualTo(24);
        assertThat(steps.get(0).slope()).isCloseTo(170.0, within(1e-6));
        assertThat(steps.get(0).intercept()).isCloseTo(5.0, within(1e-6));
        assertThat(steps.get(0).predictedDeltaVnd())
                .isCloseTo(steps.get(0).observation().deltaVnd(), within(1e-6));
    }

    @Test
    void doesNotPredictBeforeEnoughHistory() {
        Result r = PassThroughBacktest.run(exactSeries(25, 170.0, 0.0), 20);

        assertThat(r.nTest()).isEqualTo(5);          // 25 − 20 kỳ dùng để học
    }

    @Test
    void tooFewObservationsGiveAnEmptyResult() {
        Result r = PassThroughBacktest.run(exactSeries(10, 170.0, 0.0), 20);

        assertThat(r.nTest()).isZero();
        assertThat(r.beatsNaive()).isFalse();
    }

    /**
     * Chống leakage. 25 kỳ theo đường y = 2x rồi một kỳ ngoại lai y = 1000. Nếu mô
     * hình đã "thấy" kỳ cuối trước khi đoán thì hệ số bị kéo lệch và sai số nhỏ đi.
     * Đoán đúng quy tắc thì sai số bằng chính |1000 − 2x|.
     */
    @Test
    void usesOnlyThePastWhenPredictingAPeriod() {
        List<Observation> series = new ArrayList<>(exactSeries(25, 2.0, 0.0));
        double lastUsd = 4.0;
        series.add(new Observation(START.plusWeeks(25), 1000.0, lastUsd));

        Result r = PassThroughBacktest.run(series, 25);   // chỉ kỳ cuối là kỳ kiểm tra

        assertThat(r.nTest()).isEqualTo(1);
        assertThat(r.maeModel()).isCloseTo(Math.abs(1000.0 - 2.0 * lastUsd), within(1e-6));
    }

    /**
     * Quan hệ đảo chiều giữa chừng (30 kỳ đầu +3, 30 kỳ sau −3). Mô hình còn mang
     * hệ số cũ nên sai nhiều hơn việc đoán "không đổi", và phải được báo là không
     * thắng chứ không được che đi.
     */
    @Test
    void reportsWhenTheModelDoesNotBeatTheNaiveBaseline() {
        List<Observation> out = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            double usd = (i % 2 == 0) ? 10 : -10;
            double vnd = (i < 30) ? 3.0 * usd : -3.0 * usd;       // quan hệ đảo chiều giữa chừng
            out.add(new Observation(START.plusWeeks(i), vnd, usd));
        }

        Result r = PassThroughBacktest.run(out, 20);

        assertThat(r.beatsNaive()).isFalse();
    }
}
