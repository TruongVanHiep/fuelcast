package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.stream.StreamHub;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Luồng sự kiện trực tiếp (Server-Sent Events). Mỏng như các controller khác.
 *
 * Các sự kiện, tên trong trường "event":
 *   ready     vừa kết nối xong
 *   prices    vừa nạp bản tin mới: giá và kỳ điều hành đã đổi
 *   forecasts dự báo LIVE vừa được tạo hoặc chấm điểm
 * Dữ liệu chỉ là tín hiệu; client gọi lại các endpoint REST để lấy số liệu mới.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StreamController {

    StreamHub hub;

    @GetMapping(path = "/stream/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter prices() {
        return hub.subscribe();
    }
}
