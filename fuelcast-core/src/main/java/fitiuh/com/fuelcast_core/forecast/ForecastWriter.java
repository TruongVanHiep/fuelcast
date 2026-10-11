package fitiuh.com.fuelcast_core.forecast;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Ghi một dự báo kèm kết quả chấm điểm của nó vào forecast và forecast_evaluation.
 *
 * Dùng JDBC thay vì entity JPA vì hai lẽ: bảng forecast bị trigger cấm UPDATE nên
 * không có vòng đời "nạp rồi sửa" mà entity phục vụ, và hai cột jsonb là dữ liệu
 * thuần chỉ ghi một lần. Một câu INSERT duy nhất ghi cả hai bảng, và ON CONFLICT
 * khiến chạy lại không nhân đôi.
 */
@Repository
public class ForecastWriter {

    /** Một dự báo REPLAY đã biết đáp án, sẵn sàng ghi. */
    public record ReplayRow(
            long cycleId, short productId, OffsetDateTime createdAt,
            String modelVersion, int horizonDays,
            BigDecimal predictedVnd, BigDecimal predictedDelta, String direction,
            String featuresJson, String explanationJson,
            BigDecimal actualVnd, BigDecimal absError, BigDecimal pctError, boolean directionHit) { }

    private static final String INSERT_REPLAY = """
            WITH ins AS (
                INSERT INTO forecast (cycle_id, product_id, created_at, horizon_days,
                                      predicted_vnd, predicted_delta, direction,
                                      model_version, features, explanation, kind)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), 'REPLAY')
                ON CONFLICT (cycle_id, product_id, model_version, horizon_days)
                    WHERE kind = 'REPLAY' DO NOTHING
                RETURNING id
            )
            INSERT INTO forecast_evaluation (forecast_id, actual_vnd, abs_error, pct_error,
                                             direction_hit, evaluated_at)
            SELECT id, ?, ?, ?, ?, ? FROM ins
            """;

    private final JdbcTemplate jdbc;

    public ForecastWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true nếu đã ghi một dòng mới, false nếu kỳ này đã được phát lại trước đó */
    public boolean insertReplay(ReplayRow r) {
        // Số dòng bị ảnh hưởng là số dòng chèn vào forecast_evaluation: 1 hoặc 0.
        int rows = jdbc.update(INSERT_REPLAY,
                r.cycleId(), r.productId(), r.createdAt(), r.horizonDays(),
                r.predictedVnd(), r.predictedDelta(), r.direction(),
                r.modelVersion(), r.featuresJson(), r.explanationJson(),
                r.actualVnd(), r.absError(), r.pctError(), r.directionHit(), r.createdAt());
        return rows == 1;
    }
}
