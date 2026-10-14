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

    /** Một dự báo LIVE: chưa biết đáp án, nên chưa có phần chấm điểm. */
    public record LiveRow(
            long cycleId, short productId, OffsetDateTime createdAt,
            String modelVersion, int horizonDays,
            BigDecimal predictedVnd, BigDecimal predictedDelta, String direction,
            String featuresJson, String explanationJson) { }

    private static final String INSERT_LIVE = """
            INSERT INTO forecast (cycle_id, product_id, created_at, horizon_days,
                                  predicted_vnd, predicted_delta, direction,
                                  model_version, features, explanation, kind)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), 'LIVE')
            ON CONFLICT (cycle_id, product_id, model_version, horizon_days)
                WHERE kind = 'LIVE' DO NOTHING
            """;

    /** @return true nếu đã ghi, false nếu dự báo cùng kỳ, mô hình và horizon đã có */
    public boolean insertLive(LiveRow r) {
        int rows = jdbc.update(INSERT_LIVE,
                r.cycleId(), r.productId(), r.createdAt(), r.horizonDays(),
                r.predictedVnd(), r.predictedDelta(), r.direction(),
                r.modelVersion(), r.featuresJson(), r.explanationJson());
        return rows == 1;
    }

    /**
     * Chấm điểm các dự báo LIVE chưa chấm: đáp án là bản tin ĐẦU TIÊN của đúng mặt
     * hàng đó sau lúc dự báo được tạo.
     *
     * Ghép theo thời gian chứ không theo cycle_id, vì bản tin có thể bị dời sang
     * ngày khác với ngày dự kiến (khi đó dòng kỳ OPEN thành mồ côi), mà dự báo vẫn
     * phải được chấm bằng bản tin thật kế tiếp.
     *
     * Sai số "không đổi" suy ra từ chính dòng dự báo: giá kỳ trước = predicted_vnd
     * - predicted_delta. Idempotent: NOT EXISTS cộng khoá chính của bảng chấm điểm.
     */
    private static final String GRADE_LIVE = """
            INSERT INTO forecast_evaluation (forecast_id, actual_vnd, abs_error, pct_error,
                                             direction_hit, evaluated_at)
            SELECT f.id, a.price_vnd,
                   abs(a.price_vnd - f.predicted_vnd),
                   abs(a.price_vnd - f.predicted_vnd) * 100 / a.price_vnd,
                   sign(a.price_vnd - (f.predicted_vnd - f.predicted_delta)) = sign(f.predicted_delta),
                   now()
            FROM forecast f
            JOIN LATERAL (
                SELECT r.price_vnd
                FROM retail_price r
                WHERE r.product_id = f.product_id AND r.region = 1 AND r.observed_at > f.created_at
                ORDER BY r.observed_at ASC
                LIMIT 1
            ) a ON true
            WHERE f.kind = 'LIVE'
              AND NOT EXISTS (SELECT 1 FROM forecast_evaluation e WHERE e.forecast_id = f.id)
            """;

    /** @return số dự báo vừa được chấm */
    public int gradePendingLive() {
        return jdbc.update(GRADE_LIVE);
    }
}
