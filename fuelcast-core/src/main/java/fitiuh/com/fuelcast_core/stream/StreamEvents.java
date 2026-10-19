package fitiuh.com.fuelcast_core.stream;

import java.time.OffsetDateTime;

/**
 * Sự kiện nội bộ mà bộ quét phát ra qua ApplicationEventPublisher và StreamHub đẩy xuống các
 * trình duyệt đang kết nối. Tách bằng sự kiện để bộ quét không biết gì về SSE, và ngược lại.
 *
 * Dữ liệu trong sự kiện CỐ Ý nhỏ: chỉ là tín hiệu "có gì đó vừa đổi". Trình duyệt nhận được thì
 * gọi lại API REST như bình thường; REST vẫn là nguồn sự thật duy nhất, SSE chỉ báo lúc nào nên hỏi.
 */
public final class StreamEvents {

    private StreamEvents() { }

    /** Vừa nạp xong bản tin mới: giá bán lẻ và kỳ điều hành đã đổi. */
    public record PricesUpdated(OffsetDateTime at, int ingested) { }

    /** Dự báo LIVE vừa được tạo hoặc chấm điểm: dự báo kỳ tới và bảng điểm đã đổi. */
    public record ForecastsUpdated(OffsetDateTime at, int created, int graded) { }
}
