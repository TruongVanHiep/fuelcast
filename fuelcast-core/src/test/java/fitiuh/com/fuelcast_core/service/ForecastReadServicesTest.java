package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.api.NextForecastsDto;
import fitiuh.com.fuelcast_core.api.ScorecardDto;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter.LiveRow;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter.ReplayRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phía đọc của dự báo chạy trên PostgreSQL thật (SQL là logic ở đây, mock không kiểm được gì).
 * Mỗi test chạy trong một giao dịch và bị huỷ khi xong, và chỉ nhìn vào mặt hàng TESTFUEL do
 * chính test tạo, nên không đụng vào dữ liệu thật trong DB phát triển.
 */
@SpringBootTest
@Transactional
class ForecastReadServicesTest {

    private static final String JAVA_MODEL = "lagged-world-ols-v1";
    private static final String PYTHON_MODEL = "rule-lgbm-residual-v1";
    private static final OffsetDateTime T = OffsetDateTime.of(2099, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @Autowired ForecastQueryService forecastQuery;
    @Autowired ScorecardService scorecard;
    @Autowired ForecastWriter writer;
    @Autowired JdbcTemplate jdbc;

    private short productId;
    private long cycleId;

    @BeforeEach
    void seed() {
        productId = jdbc.queryForObject("""
                INSERT INTO fuel_product (code, name_vi, unit, is_gasoline, display_order)
                VALUES ('TESTFUEL', 'Mặt hàng thử', 'lít', true, 99) RETURNING id
                """, Short.class);
        cycleId = jdbc.queryForObject("""
                INSERT INTO adjustment_cycle (cycle_start, cycle_end, status)
                VALUES ('2099-01-01', '2099-01-08', 'OPEN') RETURNING id
                """, Long.class);
    }

    private void live(String model, OffsetDateTime createdAt, int horizon, String delta) {
        BigDecimal d = new BigDecimal(delta);
        writer.insertLive(new LiveRow(cycleId, productId, createdAt, model, horizon,
                new BigDecimal("20000").add(d), d, d.signum() > 0 ? "UP" : "DOWN", "{}", "{}"));
    }

    private void replay(String model, int horizon, OffsetDateTime at) {
        writer.insertReplay(new ReplayRow(cycleId, productId, at, model, horizon,
                new BigDecimal("20100"), new BigDecimal("100"), "UP", "{}", "{}",
                new BigDecimal("20300"), new BigDecimal("200"), new BigDecimal("0.9852"), true));
    }

    private NextForecastsDto.Item[] nextForTestProduct() {
        return forecastQuery.next().items().stream()
                .filter(i -> i.product().equals("TESTFUEL"))
                .toArray(NextForecastsDto.Item[]::new);
    }

    /**
     * Lỗi gốc: SQL cũ giữ một dòng cho mỗi mặt hàng (mới nhất), nên khi mô hình Python tạo
     * dự báo SAU mô hình Java, dòng của Python lặng lẽ thay dòng của mô hình chính.
     */
    @Test
    void keepsOneRowPerModelAndNeverLetsALaterModelReplaceThePrimaryOne() {
        live(JAVA_MODEL, T, 3, "-120");
        live(PYTHON_MODEL, T.plusHours(2), 3, "150");      // tạo SAU

        NextForecastsDto.Item[] items = nextForTestProduct();

        assertThat(items).extracting(NextForecastsDto.Item::modelVersion)
                .containsExactly(JAVA_MODEL, PYTHON_MODEL);     // mô hình chính đứng đầu
        assertThat(items).extracting(NextForecastsDto.Item::primary).containsExactly(true, false);
        assertThat(items[0].predictedDeltaVnd()).isEqualTo(-120);
        assertThat(items[1].predictedDeltaVnd()).isEqualTo(150);
    }

    @Test
    void keepsOnlyTheLatestForecastOfEachModel() {
        // Cùng mô hình, hai lần dự báo ở hai horizon khác nhau (khoá LIVE gồm cả horizon).
        live(JAVA_MODEL, T, 6, "-100");
        live(JAVA_MODEL, T.plusDays(3), 3, "-80");         // tạo sau, gần giờ công bố hơn

        NextForecastsDto.Item[] items = nextForTestProduct();

        assertThat(items).hasSize(1);
        assertThat(items[0].predictedDeltaVnd()).isEqualTo(-80);    // bản mới nhất của mô hình đó
    }

    @Test
    void aForecastThatHasBeenGradedNoLongerCountsAsPending() {
        live(JAVA_MODEL, T, 3, "-120");
        jdbc.update("""
                INSERT INTO forecast_evaluation (forecast_id, actual_vnd, abs_error, pct_error, direction_hit, evaluated_at)
                SELECT id, 19900, 20, 0.1, true, now() FROM forecast WHERE product_id = ?
                """, productId);

        assertThat(nextForTestProduct()).isEmpty();
    }

    @Test
    void theScorecardShowsBothModelsAndHidesTheSupersededVersion() {
        replay("passthrough-ols-v1", 0, T);            // đã bị thay thế
        replay("passthrough-ols-v2", 0, T);
        replay(PYTHON_MODEL, 7, T);

        var rows = scorecard.scorecard().rows().stream()
                .filter(r -> r.product().equals("TESTFUEL")).toList();

        assertThat(rows).extracting(ScorecardDto.Row::modelVersion)
                .containsExactlyInAnyOrder("passthrough-ols-v2", PYTHON_MODEL)
                .doesNotContain("passthrough-ols-v1");
        assertThat(rows).filteredOn(r -> r.modelVersion().equals(PYTHON_MODEL))
                .singleElement().extracting(ScorecardDto.Row::horizonDays).isEqualTo(7);
    }
}
