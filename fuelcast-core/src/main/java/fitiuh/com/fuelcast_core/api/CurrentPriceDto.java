package fitiuh.com.fuelcast_core.api;

import java.time.OffsetDateTime;

/**
 * Giá bán lẻ hiện hành của một mặt hàng, đúng như API trả ra.
 *
 * Cố ý không trả thẳng entity RetailPrice: entity là mô hình lưu trữ (đổi cột là
 * đổi API), và nó chỉ có productId chứ không có tên mặt hàng.
 *
 * @param product     mã mặt hàng, ví dụ RON95
 * @param priceVnd    đồng/đơn vị; kiểu long vì VND không có phần lẻ
 * @param deltaVnd    chênh lệch so với kỳ trước, âm là giảm; null khi bản tin không nêu
 * @param effectiveAt thời điểm giá có hiệu lực. Mặt hàng đã ngừng công bố vẫn
 *                    xuất hiện với effectiveAt cũ, nên client dựa vào trường này
 *                    để biết giá có còn mới hay không.
 */
public record CurrentPriceDto(
        String product,
        String name,
        String unit,
        short region,
        long priceVnd,
        Long deltaVnd,
        OffsetDateTime effectiveAt
) { }
