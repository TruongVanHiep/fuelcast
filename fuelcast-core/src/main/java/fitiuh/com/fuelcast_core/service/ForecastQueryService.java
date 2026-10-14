package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.api.NextForecastsDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/** Phía đọc của dự báo LIVE. Chỉ đọc. */
@Service
public class ForecastQueryService {

    static final String NOTICE =
            "Dự báo LIVE chỉ dùng thông tin có trước giờ công bố (giá thế giới của kỳ vừa rồi). "
          + "Trên lịch sử, cách này chưa hơn việc đoán \"giá không đổi\" một cách đáng kể: "
          + "xem bảng điểm LIVE để biết kết quả thực tế. Đây là thông tin tham khảo, "
          + "không phải tư vấn đầu tư.";

    /**
     * Dự báo LIVE chưa có đáp án (chưa bản tin nào sau nó), mới nhất của từng mặt
     * hàng. DISTINCT ON giữ một dòng cho mỗi product_id theo thứ tự created_at giảm.
     */
    private static final String SQL = """
            SELECT DISTINCT ON (f.product_id)
                   p.code, p.name_vi, p.unit, p.display_order,
                   f.predicted_vnd, f.predicted_delta, f.direction,
                   f.horizon_days, f.model_version, f.created_at
            FROM forecast f
            JOIN fuel_product p ON p.id = f.product_id
            WHERE f.kind = 'LIVE'
              AND NOT EXISTS (SELECT 1 FROM forecast_evaluation e WHERE e.forecast_id = f.id)
            ORDER BY f.product_id, f.created_at DESC
            """;

    private final JdbcTemplate jdbc;

    public ForecastQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Row(int displayOrder, NextForecastsDto.Item item) { }

    @Transactional(readOnly = true)
    public NextForecastsDto next() {
        List<Row> rows = jdbc.query(SQL, (rs, i) -> new Row(
                rs.getInt("display_order"),
                new NextForecastsDto.Item(
                        rs.getString("code"),
                        rs.getString("name_vi"),
                        rs.getString("unit"),
                        rs.getBigDecimal("predicted_vnd").setScale(0, RoundingMode.HALF_UP).longValue(),
                        rs.getBigDecimal("predicted_delta").setScale(0, RoundingMode.HALF_UP).longValue(),
                        rs.getString("direction"),
                        rs.getInt("horizon_days"),
                        rs.getString("model_version"),
                        rs.getObject("created_at", OffsetDateTime.class))));

        List<NextForecastsDto.Item> items = rows.stream()
                .sorted(Comparator.comparingInt(Row::displayOrder))
                .map(Row::item)
                .toList();
        return new NextForecastsDto(NOTICE, items);
    }
}
