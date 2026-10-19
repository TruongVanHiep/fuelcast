package fitiuh.com.fuelcast_core.observability;

import fitiuh.com.fuelcast_core.service.CycleQueryService;
import fitiuh.com.fuelcast_core.service.NoCycleDataException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Số đo "bản tin giá mới nhất có hiệu lực lúc nào", để cảnh báo khi quá lâu không có bản tin.
 *
 * Đo THỜI ĐIỂM (timestamp) chứ không đo "số giây đã trôi qua": giá trị đã trôi qua chỉ đúng
 * lúc tính, còn timestamp thì đúng mãi và để Grafana tự trừ (`time() - metric`). Cũng nhờ đó cảnh
 * báo vẫn đúng khi việc quét Prometheus bị trễ.
 *
 * Lấy từ DB (kỳ gần nhất, chính là thứ API /cycles/current trả) chứ không từ trạng thái của bộ
 * quét: ứng dụng khởi động lại không làm số đo về 0, và việc nạp lại lịch sử (backfill) không làm
 * bản tin trông như mới. Số đo này vì vậy trả lời đúng câu hỏi nghiệp vụ, "MOIT đã công bố gì và
 * khi nào", còn "bộ quét có còn chạy không" do số đo riêng của bộ quét trả lời.
 */
@Component
public class BulletinFreshnessMetrics {

    private static final Logger log = LoggerFactory.getLogger(BulletinFreshnessMetrics.class);

    static final String NAME = "fuelcast.bulletin.last_effective.timestamp.seconds";

    /**
     * Prometheus quét mỗi 15 giây, mà bản tin chỉ đổi mỗi tuần: không cần truy vấn DB ở từng
     * lần quét. Một phút là đủ tươi.
     */
    static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final CycleQueryService cycles;
    private final Clock clock;

    private volatile double cachedValue = Double.NaN;
    private volatile Instant cachedAt;

    public BulletinFreshnessMetrics(MeterRegistry registry, CycleQueryService cycles, Clock clock) {
        this.cycles = cycles;
        this.clock = clock;
        Gauge.builder(NAME, this, BulletinFreshnessMetrics::lastEffectiveEpochSeconds)
                .description("Thời điểm có hiệu lực của bản tin điều hành giá mới nhất, tính bằng giây epoch")
                .register(registry);
    }

    /**
     * NaN khi chưa có bản tin nào trong DB, hoặc chưa từng đọc được: Prometheus coi NaN là "không
     * có số liệu", khác với 0 là "bản tin năm 1970". DB lỗi giữa chừng thì giữ giá trị đọc được
     * lần trước thay vì nhảy về NaN.
     */
    double lastEffectiveEpochSeconds() {
        Instant now = clock.instant();
        Instant readAt = cachedAt;
        if (readAt != null && Duration.between(readAt, now).compareTo(CACHE_TTL) < 0) {
            return cachedValue;
        }
        try {
            cachedValue = cycles.currentCycle().lastEffectiveAt().toEpochSecond();
            cachedAt = now;
        } catch (NoCycleDataException e) {
            cachedValue = Double.NaN;
            cachedAt = now;
        } catch (RuntimeException e) {
            // Không cập nhật cachedAt: lần quét sau thử lại.
            log.warn("Không đọc được bản tin mới nhất để làm số đo: {}", e.toString());
        }
        return cachedValue;
    }
}
