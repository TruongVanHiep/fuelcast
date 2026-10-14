package fitiuh.com.fuelcast_core.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật cơ chế @Scheduled, và chỉ khi bộ quét được bật. Tách riêng để toàn bộ ứng
 * dụng không có luồng lập lịch nào chạy ngầm khi tắt.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "fuelcast.ingestion.poll.enabled", havingValue = "true")
public class SchedulingConfig {
}
