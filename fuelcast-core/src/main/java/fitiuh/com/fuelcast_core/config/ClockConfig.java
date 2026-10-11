package fitiuh.com.fuelcast_core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Đồng hồ của ứng dụng, theo giờ Việt Nam. Inject Clock thay vì gọi
 * LocalDate.now() để test đặt được "hôm nay" cố định, và để "hôm nay" luôn là
 * ngày theo giờ VN dù máy chủ đặt múi giờ nào.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
