package fitiuh.com.fuelcast_core.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Bảng điểm độ chính xác của mô hình.
 *
 * @param notice lời cảnh báo đi kèm MỌI phản hồi: giải thích REPLAY khác LIVE thế
 *               nào và đây không phải tư vấn đầu tư.
 */
public record ScorecardDto(String notice, List<Row> rows) {

    /**
     * Một dòng = một mặt hàng ở một loại dự báo, một mô hình và một horizon.
     *
     * @param kind              LIVE (dự báo thật, trước sự kiện) hoặc REPLAY (phát lại lịch sử)
     * @param horizonDays       0 nghĩa là dùng giá thế giới trung bình cùng kỳ (đã biết đủ)
     * @param maeModelVnd       sai số tuyệt đối trung bình của mô hình, đồng
     * @param maeNaiveVnd       sai số của đường cơ sở "giá không đổi", đồng
     * @param improvementPct    % sai số giảm được so với đường cơ sở; âm là tệ hơn
     * @param directionHitPct   % lần đoán đúng chiều tăng/giảm
     * @param beatsNaive        false nghĩa là mô hình không có giá trị cho mặt hàng này
     */
    public record Row(
            String product,
            String name,
            String kind,
            String modelVersion,
            int horizonDays,
            int evaluations,
            double maeModelVnd,
            double maeNaiveVnd,
            double improvementPct,
            double directionHitPct,
            boolean beatsNaive,
            LocalDate from,
            LocalDate to
    ) { }
}
