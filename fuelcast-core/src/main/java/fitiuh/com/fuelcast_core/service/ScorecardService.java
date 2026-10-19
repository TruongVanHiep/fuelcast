package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.api.ScorecardDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/**
 * Bảng điểm: tổng hợp forecast_evaluation theo mặt hàng, loại dự báo, mô hình và
 * horizon. Tính bằng SQL vì đó là phép gộp thuần, không có logic nghiệp vụ.
 */
@Service
public class ScorecardService {

    static final String NOTICE =
            "Dòng REPLAY là mô hình được phát lại trên lịch sử: nó chỉ học từ các kỳ trước kỳ đang "
          + "đoán, nhưng được chọn sau khi đã nhìn dữ liệu, nên lạc quan hơn dự báo thật (LIVE). "
          + "horizonDays = 0 dùng giá thế giới trung bình của chính kỳ đó, nên chưa phải dự báo trước "
          + "sự kiện; horizonDays = 7 dùng giá thế giới của kỳ trước, đúng thông tin mà dự báo thật có. "
          + "Đây là thông tin tham khảo, không phải tư vấn đầu tư.";

    /**
     * Phiên bản mô hình đã bị thay thế vì lỗi dữ liệu học: các dòng của chúng vẫn nằm trong
     * forecast (bảng chỉ thêm, không sửa) nhưng không được hiện trên bảng điểm. passthrough-ols-v1
     * học cả trên các kỳ bị thiếu bản tin giữa chừng; v2 đã thêm kiểm tra liên tục.
     */
    static final List<String> SUPERSEDED_MODELS = List.of("passthrough-ols-v1");

    /**
     * Sai số của đường cơ sở "không đổi" suy ra từ chính dòng dự báo: giá kỳ trước
     * = predicted_vnd − predicted_delta, nên đoán "không đổi" sai |actual − kỳ trước|.
     */
    private static final String SQL = """
            SELECT p.code, p.name_vi, f.kind, f.model_version, f.horizon_days,
                   count(*)                                                       AS n,
                   avg(e.abs_error)                                               AS mae_model,
                   avg(abs(e.actual_vnd - (f.predicted_vnd - f.predicted_delta))) AS mae_naive,
                   avg(CASE WHEN e.direction_hit THEN 1.0 ELSE 0.0 END)           AS hit_rate,
                   min(f.created_at)                                              AS first_at,
                   max(f.created_at)                                              AS last_at
            FROM forecast f
            JOIN forecast_evaluation e ON e.forecast_id = f.id
            JOIN fuel_product p        ON p.id = f.product_id
            WHERE f.model_version NOT IN (%s)
            GROUP BY p.code, p.name_vi, p.display_order, f.kind, f.model_version, f.horizon_days
            ORDER BY f.kind, f.model_version, f.horizon_days, p.display_order
            """;

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;

    public ScorecardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ScorecardDto scorecard() {
        // Mỗi phiên bản bị thay thế là một dấu "?"; tên đi qua tham số, không nối vào câu SQL.
        String sql = SQL.formatted(String.join(", ", Collections.nCopies(SUPERSEDED_MODELS.size(), "?")));

        List<ScorecardDto.Row> rows = jdbc.query(sql, (rs, i) -> {
            double maeModel = rs.getDouble("mae_model");
            double maeNaive = rs.getDouble("mae_naive");
            double improvement = maeNaive == 0 ? 0 : (1 - maeModel / maeNaive) * 100;

            return new ScorecardDto.Row(
                    rs.getString("code"),
                    rs.getString("name_vi"),
                    rs.getString("kind"),
                    rs.getString("model_version"),
                    rs.getInt("horizon_days"),
                    rs.getInt("n"),
                    round1(maeModel),
                    round1(maeNaive),
                    round1(improvement),
                    round1(rs.getDouble("hit_rate") * 100),
                    maeModel < maeNaive,
                    localDate(rs.getObject("first_at", java.time.OffsetDateTime.class)),
                    localDate(rs.getObject("last_at", java.time.OffsetDateTime.class)));
        }, SUPERSEDED_MODELS.toArray());
        return new ScorecardDto(NOTICE, rows);
    }

    private static LocalDate localDate(java.time.OffsetDateTime at) {
        return at == null ? null : at.atZoneSameInstant(VN).toLocalDate();
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
