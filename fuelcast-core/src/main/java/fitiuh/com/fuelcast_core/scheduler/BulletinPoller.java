package fitiuh.com.fuelcast_core.scheduler;

import fitiuh.com.fuelcast_core.entity.IngestionStatus;
import fitiuh.com.fuelcast_core.forecast.LiveForecastService;
import fitiuh.com.fuelcast_core.repository.IngestionRunRepository;
import fitiuh.com.fuelcast_core.service.BulletinIngestionService;
import fitiuh.com.fuelcast_core.service.scraper.BulletinLink;
import fitiuh.com.fuelcast_core.service.scraper.ListingPage;
import fitiuh.com.fuelcast_core.service.scraper.MoitBulletinScraper;
import fitiuh.com.fuelcast_core.stream.StreamEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Tự đi hỏi MOIT xem có bản tin mới và nạp nó, dùng lại đúng đường ống của
 * BulletinIngestionService (tải, lưu bản gốc, parse, ghi giá).
 *
 * Mặc định TẮT. Bật bằng fuelcast.ingestion.poll.enabled=true. Lý do để tắt: nếu
 * bật sẵn thì mọi lần chạy test và mọi lệnh chạy một lần (backfill, reprocess...)
 * cũng sẽ lén gọi mạng.
 *
 * Giới hạn đã biết: chỉ an toàn khi chạy MỘT bản ứng dụng. Hai bản cùng quét sẽ
 * tranh nhau nạp một bản tin; khoá tự nhiên chặn dữ liệu nhân đôi nhưng một bên sẽ
 * ghi nhận thất bại. Tài liệu thiết kế dùng Quartz với JDBC JobStore để giải bài
 * toán nhiều bản; chưa cần cho tới khi có nhiều hơn một bản.
 */
@Component
@ConditionalOnProperty(name = "fuelcast.ingestion.poll.enabled", havingValue = "true")
public class BulletinPoller {

    private static final Logger log = LoggerFactory.getLogger(BulletinPoller.class);

    /**
     * Một bài khớp bộ lọc tiêu đề mà không phải bản tin giá (ví dụ bài tổng kết
     * công tác điều hành) sẽ thất bại mãi. Quá số lần này thì thôi, không thử mỗi
     * 10 phút cho tới hết đời.
     */
    static final int MAX_FAILED_ATTEMPTS = 3;

    private final MoitBulletinScraper scraper;
    private final BulletinIngestionService ingestion;
    private final IngestionRunRepository runs;
    private final LiveForecastService liveForecasts;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration requestGap;

    private volatile Instant lastPolledAt;

    /** Kết quả một lượt hỏi. skipped = đã nạp rồi hoặc đã thử quá số lần cho phép. */
    public record PollResult(int found, int skipped, int ingested, int failed) { }

    public BulletinPoller(MoitBulletinScraper scraper,
                          BulletinIngestionService ingestion,
                          IngestionRunRepository runs,
                          LiveForecastService liveForecasts,
                          ApplicationEventPublisher events,
                          Clock clock,
                          @Value("${fuelcast.ingestion.request-gap:PT5S}") Duration requestGap) {
        this.scraper = scraper;
        this.ingestion = ingestion;
        this.runs = runs;
        this.liveForecasts = liveForecasts;
        this.events = events;
        this.clock = clock;
        this.requestGap = requestGap;
    }

    /**
     * Chạy mỗi phút nhưng phần lớn các phút chỉ kiểm tra "đã đến lúc chưa" rồi thôi,
     * vì nhịp thật (10 phút hay 6 giờ) do PollSchedule quyết định theo giờ hiện tại.
     */
    @Scheduled(initialDelayString = "${fuelcast.ingestion.poll.initial-delay:PT30S}",
               fixedDelayString = "${fuelcast.ingestion.poll.tick:PT1M}")
    public void tick() {
        Instant now = clock.instant();
        if (!PollSchedule.due(now, lastPolledAt)) {
            return;
        }
        // Đặt mốc NGAY khi bắt đầu, kể cả khi lượt này lỗi: lỗi mạng không được biến
        // thành vòng lặp hỏi dồn dập, mà phải chờ đúng nhịp như lượt thường.
        lastPolledAt = now;

        try {
            PollResult r = poll();
            if (r.ingested() > 0 || r.failed() > 0) {
                log.info("Quét bản tin: thấy {}, bỏ qua {}, nạp mới {}, lỗi {}",
                        r.found(), r.skipped(), r.ingested(), r.failed());
            } else {
                log.debug("Quét bản tin: thấy {}, không có gì mới", r.found());
            }
            if (r.ingested() > 0) {
                // Đã commit xong trong ingestion.ingest: trình duyệt hỏi lại sẽ thấy giá mới.
                publish(new StreamEvents.PricesUpdated(OffsetDateTime.now(clock), r.ingested()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (IOException | RuntimeException e) {
            // Không ném ra ngoài: một lần lỗi không được làm chết bộ lập lịch.
            log.warn("Quét bản tin thất bại, sẽ thử lại đúng nhịp: {}", e.toString());
        }
        refreshLiveForecasts();
    }

    /**
     * Chấm các dự báo LIVE đã có đáp án và đảm bảo kỳ tới có dự báo. Chạy sau MỖI
     * lượt quét chứ không chỉ khi có bản tin mới, để tự sửa nếu lần trước lỡ dở.
     * Tách riêng try/catch: lỗi ở đây không được che đi việc nạp bản tin đã xong.
     */
    private void refreshLiveForecasts() {
        try {
            LiveForecastService.RefreshResult r = liveForecasts.refresh();
            if (r.graded() > 0 || r.created() > 0) {
                log.info("Dự báo LIVE: chấm {}, tạo mới {}, đã có {}, không dự báo được {}",
                        r.graded(), r.created(), r.existing(), r.skipped());
                publish(new StreamEvents.ForecastsUpdated(OffsetDateTime.now(clock), r.created(), r.graded()));
            }
        } catch (RuntimeException e) {
            log.warn("Làm mới dự báo LIVE thất bại: {}", e.toString());
        }
    }

    /**
     * Báo cho các trình duyệt đang theo dõi. Lỗi ở đây (một bộ lắng nghe hỏng) không được làm
     * hỏng lượt quét đã xong; SSE là phần phụ, việc nạp dữ liệu mới là phần chính.
     */
    private void publish(Object event) {
        try {
            events.publishEvent(event);
        } catch (RuntimeException e) {
            log.warn("Không đẩy được sự kiện {}: {}", event.getClass().getSimpleName(), e.toString());
        }
    }

    /**
     * Chỉ đọc trang đầu của danh sách (100 bài mới nhất, đủ phủ vài tuần): bản tin
     * mới luôn nằm ở đó. Không cần lùi sâu như đợt backfill.
     */
    PollResult poll() throws IOException, InterruptedException {
        ListingPage page = scraper.fetchListingPage(1);

        List<BulletinLink> todo = page.bulletins().stream()
                .filter(l -> !runs.existsByTargetUrlAndStatus(l.url(), IngestionStatus.SUCCESS))
                .filter(this::withinAttemptLimit)
                .sorted(Comparator.comparing(BulletinLink::publishedOn,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();

        int ingested = 0;
        int failed = 0;
        boolean first = true;
        for (BulletinLink link : todo) {
            if (!first) {
                Thread.sleep(requestGap.toMillis());
            }
            first = false;

            BulletinIngestionService.Outcome outcome = ingestion.ingest(link.url());
            if (outcome.status() == IngestionStatus.SUCCESS) {
                ingested++;
            } else {
                failed++;
            }
        }
        return new PollResult(page.bulletins().size(), page.bulletins().size() - todo.size(), ingested, failed);
    }

    private boolean withinAttemptLimit(BulletinLink link) {
        long attempts = runs.countByTargetUrlAndStatus(link.url(), IngestionStatus.FAILED);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            log.debug("Bỏ qua {}: đã thất bại {} lần", link.url(), attempts);
            return false;
        }
        return true;
    }
}
