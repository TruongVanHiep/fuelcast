package fitiuh.com.fuelcast_core.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Dự báo LIVE cho kỳ sắp tới: được tạo TRƯỚC khi bản tin ra, chỉ bằng thông tin
 * có sẵn lúc đó.
 *
 * @param notice lời cảnh báo đi kèm MỌI phản hồi: dự báo này hiện chưa hơn việc đoán
 *               "giá không đổi", và không phải tư vấn đầu tư.
 */
public record NextForecastsDto(String notice, List<Item> items) {

    /**
     * @param predictedVnd      giá dự đoán, làm tròn tới đồng
     * @param predictedDeltaVnd mức đổi dự đoán so với kỳ gần nhất, âm là giảm
     * @param direction         UP, DOWN hoặc FLAT (dưới 1 đồng)
     * @param horizonDays       còn bao nhiêu ngày tới giờ công bố dự kiến lúc tạo dự báo
     * @param createdAt         lúc dự báo được tạo, để biết nó có trước bản tin hay không
     * @param primary           true cho mô hình chính của hệ thống. Khi có nhiều mô hình
     *                          chạy song song, mỗi mặt hàng có một dòng cho từng mô hình;
     *                          giao diện chỉ nêu dòng primary ở chỗ nổi bật và để các dòng
     *                          còn lại ở trang dự báo chi tiết.
     */
    public record Item(
            String product,
            String name,
            String unit,
            long predictedVnd,
            long predictedDeltaVnd,
            String direction,
            int horizonDays,
            String modelVersion,
            OffsetDateTime createdAt,
            boolean primary
    ) { }
}
