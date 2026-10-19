package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.api.NextForecastsDto;
import fitiuh.com.fuelcast_core.forecast.LiveForecastService;
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
     * Dự báo LIVE chưa có đáp án (chưa bản tin nào sau nó), mới nhất của từng mặt hàng
     * VÀ từng mô hình. DISTINCT ON giữ một dòng cho mỗi (product_id, model_version) theo
     * thứ tự created_at giảm. Phải gồm model_version: chỉ theo product_id thì khi có hai mô
     * hình, dòng của mô hình tạo sau sẽ lặng lẽ thay dòng của mô hình chính.
     */
    private static final String SQL = """
            SELECT DISTINCT ON (f.product_id, f.model_version)
                   p.code, p.name_vi, p.unit, p.display_order,
                   f.predicted_vnd, f.predicted_delta, f.direction,
                   f.horizon_days, f.model_version, f.created_at
            FROM forecast f
            JOIN fuel_product p ON p.id = f.product_id
            WHERE f.kind = 'LIVE'
              AND NOT EXISTS (SELECT 1 FROM forecast_evaluation e WHERE e.forecast_id = f.id)
            ORDER BY f.product_id, f.model_version, f.created_at DESC
            """;

    /** Mô hình chính của hệ thống; các mô hình khác chạy song song để so sánh. */
    static final String PRIMARY_MODEL = LiveForecastService.MODEL_VERSION;

    private final JdbcTemplate jdbc;

    public ForecastQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Row(int displayOrder, NextForecastsDto.Item item) { }

    @Transactional(readOnly = true)
    public NextForecastsDto next() {
        List<Row> rows = jdbc.query(SQL, (rs, i) -> {
            String modelVersion = rs.getString("model_version");
            return new Row(
                    rs.getInt("display_order"),
                    new NextForecastsDto.Item(
                            rs.getString("code"),
                            rs.getString("name_vi"),
                            rs.getString("unit"),
                            rs.getBigDecimal("predicted_vnd").setScale(0, RoundingMode.HALF_UP).longValue(),
                            rs.getBigDecimal("predicted_delta").setScale(0, RoundingMode.HALF_UP).longValue(),
                            rs.getString("direction"),
                            rs.getInt("horizon_days"),
                            modelVersion,
                            rs.getObject("created_at", OffsetDateTime.class),
                            PRIMARY_MODEL.equals(modelVersion)));
        });

        // Theo thứ tự hiển thị của mặt hàng; trong mỗi mặt hàng, mô hình chính đứng đầu.
        List<NextForecastsDto.Item> items = rows.stream()
                .sorted(Comparator.comparingInt(Row::displayOrder)
                        .thenComparing(r -> !r.item().primary())
                        .thenComparing(r -> r.item().modelVersion()))
                .map(Row::item)
                .toList();
        return new NextForecastsDto(NOTICE, items);
    }
}
