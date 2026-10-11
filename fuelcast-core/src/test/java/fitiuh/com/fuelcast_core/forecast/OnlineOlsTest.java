package fitiuh.com.fuelcast_core.forecast;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OnlineOlsTest {

    @Test
    void fitsAnExactLine() {
        OnlineOls ols = new OnlineOls();
        ols.add(1, 3);
        ols.add(2, 5);
        ols.add(3, 7);          // y = 1 + 2x

        assertThat(ols.slope()).isCloseTo(2.0, within(1e-9));
        assertThat(ols.intercept()).isCloseTo(1.0, within(1e-9));
        assertThat(ols.predict(4)).isCloseTo(9.0, within(1e-9));
    }

    /**
     * Dữ liệu không nằm trên một đường thẳng. Đáp án 1,9 và 0 được đối chiếu với
     * regr_slope / regr_intercept của PostgreSQL trên cùng bốn điểm.
     */
    @Test
    void matchesPostgresRegressionOnNoisyPoints() {
        OnlineOls ols = new OnlineOls();
        ols.add(1, 2);
        ols.add(2, 4);
        ols.add(3, 5);
        ols.add(4, 8);

        assertThat(ols.slope()).isCloseTo(1.9, within(1e-9));
        assertThat(ols.intercept()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void cannotPredictWithFewerThanTwoPoints() {
        OnlineOls ols = new OnlineOls();
        assertThat(ols.canPredict()).isFalse();

        ols.add(1, 5);
        assertThat(ols.canPredict()).isFalse();
    }

    /** Mọi x bằng nhau thì không có đường thẳng nào duy nhất: không được chia cho 0. */
    @Test
    void cannotPredictWhenXNeverChanges() {
        OnlineOls ols = new OnlineOls();
        ols.add(2, 3);
        ols.add(2, 9);

        assertThat(ols.canPredict()).isFalse();
    }

    @Test
    void countsPoints() {
        OnlineOls ols = new OnlineOls();
        ols.add(1, 1);
        ols.add(2, 2);

        assertThat(ols.count()).isEqualTo(2);
    }
}
