package fitiuh.com.fuelcast_core.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Chuỗi giá của một mặt hàng trong một khoảng ngày.
 *
 * Thông tin mặt hàng nằm ở đây một lần thay vì lặp lại trên từng điểm. from và to
 * là khoảng NGÀY ĐÃ ÁP DỤNG, nên khi client bỏ trống tham số, nó thấy mặc định
 * nào đã được dùng.
 *
 * @param points theo thứ tự thời gian tăng dần, sẵn cho biểu đồ
 */
public record PriceHistoryDto(
        String product,
        String name,
        String unit,
        short region,
        LocalDate from,
        LocalDate to,
        List<PricePointDto> points
) { }
