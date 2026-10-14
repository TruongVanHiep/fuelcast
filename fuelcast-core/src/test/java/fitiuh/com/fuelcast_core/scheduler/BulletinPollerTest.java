package fitiuh.com.fuelcast_core.scheduler;

import fitiuh.com.fuelcast_core.entity.IngestionStatus;
import fitiuh.com.fuelcast_core.forecast.LiveForecastService;
import fitiuh.com.fuelcast_core.repository.IngestionRunRepository;
import fitiuh.com.fuelcast_core.scheduler.BulletinPoller.PollResult;
import fitiuh.com.fuelcast_core.service.BulletinIngestionService;
import fitiuh.com.fuelcast_core.service.BulletinIngestionService.Outcome;
import fitiuh.com.fuelcast_core.service.scraper.BulletinLink;
import fitiuh.com.fuelcast_core.service.scraper.ListingPage;
import fitiuh.com.fuelcast_core.service.scraper.MoitBulletinScraper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulletinPollerTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final String NEW_URL = "https://moit.gov.vn/tin-tuc/new.html";
    private static final String OLD_URL = "https://moit.gov.vn/tin-tuc/old.html";

    private final MoitBulletinScraper scraper = mock(MoitBulletinScraper.class);
    private final BulletinIngestionService ingestion = mock(BulletinIngestionService.class);
    private final IngestionRunRepository runs = mock(IngestionRunRepository.class);

    private final LiveForecastService live = mock(LiveForecastService.class);

    private BulletinPoller pollerAt(OffsetDateTime now) {
        return new BulletinPoller(scraper, ingestion, runs, live,
                Clock.fixed(now.toInstant(), VN), Duration.ZERO);
    }

    private static BulletinLink link(String url, int day) {
        return new BulletinLink(url, "Thông tin điều hành giá xăng dầu", LocalDate.of(2026, 10, day));
    }

    private static ListingPage page(BulletinLink... links) {
        return new ListingPage(List.of(links), LocalDate.of(2026, 9, 1), 100);
    }

    private static Outcome ok() {
        return new Outcome(IngestionStatus.SUCCESS, 9, null);
    }

    private static Outcome failed() {
        return new Outcome(IngestionStatus.FAILED, 0, "Parser không đọc được");
    }

    private static final OffsetDateTime THURSDAY_3PM = OffsetDateTime.of(2026, 10, 8, 15, 0, 0, 0, VN);

    // ───────────────────────── poll() ─────────────────────────

    @Test
    void ingestsABulletinThatIsNotStoredYet() throws Exception {
        when(scraper.fetchListingPage(1)).thenReturn(page(link(NEW_URL, 8)));
        when(ingestion.ingest(NEW_URL)).thenReturn(ok());

        PollResult r = pollerAt(THURSDAY_3PM).poll();

        assertThat(r.found()).isEqualTo(1);
        assertThat(r.ingested()).isEqualTo(1);
        assertThat(r.skipped()).isZero();
        verify(ingestion).ingest(NEW_URL);
    }

    /** Ca bình thường 99% thời gian: không có gì mới, và phải không đụng tới đường ống. */
    @Test
    void skipsBulletinsAlreadyStoredSuccessfully() throws Exception {
        when(scraper.fetchListingPage(1)).thenReturn(page(link(OLD_URL, 1)));
        when(runs.existsByTargetUrlAndStatus(OLD_URL, IngestionStatus.SUCCESS)).thenReturn(true);

        PollResult r = pollerAt(THURSDAY_3PM).poll();

        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.ingested()).isZero();
        verify(ingestion, never()).ingest(anyString());
    }

    /**
     * Một bài khớp bộ lọc tiêu đề mà không phải bản tin giá sẽ thất bại mãi. Sau
     * số lần cho phép thì thôi, không thử lại mỗi 10 phút tới hết đời.
     */
    @Test
    void givesUpOnAnUrlThatKeepsFailing() throws Exception {
        when(scraper.fetchListingPage(1)).thenReturn(page(link(NEW_URL, 8)));
        when(runs.countByTargetUrlAndStatus(NEW_URL, IngestionStatus.FAILED))
                .thenReturn((long) BulletinPoller.MAX_FAILED_ATTEMPTS);

        PollResult r = pollerAt(THURSDAY_3PM).poll();

        assertThat(r.skipped()).isEqualTo(1);
        verify(ingestion, never()).ingest(anyString());
    }

    @Test
    void stillRetriesAnUrlThatFailedFewerTimesThanTheLimit() throws Exception {
        when(scraper.fetchListingPage(1)).thenReturn(page(link(NEW_URL, 8)));
        when(runs.countByTargetUrlAndStatus(NEW_URL, IngestionStatus.FAILED))
                .thenReturn((long) BulletinPoller.MAX_FAILED_ATTEMPTS - 1);
        when(ingestion.ingest(NEW_URL)).thenReturn(ok());

        assertThat(pollerAt(THURSDAY_3PM).poll().ingested()).isEqualTo(1);
    }

    @Test
    void ingestsOldestFirstAndCountsAFailureWithoutStopping() throws Exception {
        when(scraper.fetchListingPage(1)).thenReturn(page(link(NEW_URL, 8), link(OLD_URL, 1)));
        when(ingestion.ingest(OLD_URL)).thenReturn(failed());
        when(ingestion.ingest(NEW_URL)).thenReturn(ok());

        PollResult r = pollerAt(THURSDAY_3PM).poll();

        InOrder order = inOrder(ingestion);
        order.verify(ingestion).ingest(OLD_URL);
        order.verify(ingestion).ingest(NEW_URL);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.ingested()).isEqualTo(1);
    }

    @Test
    void readsOnlyTheFirstListingPage() throws Exception {
        when(scraper.fetchListingPage(anyInt())).thenReturn(page());

        pollerAt(THURSDAY_3PM).poll();

        verify(scraper).fetchListingPage(1);
        verify(scraper, times(1)).fetchListingPage(anyInt());
    }

    // ───────────────────────── tick() ─────────────────────────

    @Test
    void tickPollsOnTheFirstCall() throws Exception {
        when(scraper.fetchListingPage(1)).thenReturn(page());

        pollerAt(THURSDAY_3PM).tick();

        verify(scraper).fetchListingPage(1);
    }

    /** Hai tick liên tiếp cùng một thời điểm: lần hai chưa đến nhịp nên không được gọi mạng. */
    @Test
    void tickDoesNotPollAgainBeforeTheIntervalHasPassed() throws Exception {
        when(scraper.fetchListingPage(1)).thenReturn(page());
        BulletinPoller poller = pollerAt(THURSDAY_3PM);

        poller.tick();
        poller.tick();

        verify(scraper, times(1)).fetchListingPage(1);
    }

    /**
     * Làm mới dự báo LIVE chạy sau MỖI lượt quét, kể cả khi không có bản tin nào
     * mới: chấm điểm và tạo dự báo cho kỳ tới phải tự sửa nếu lần trước lỡ dở.
     */
    @Test
    void refreshesLiveForecastsAfterEveryPollEvenWhenNothingIsNew() throws Exception {
        when(scraper.fetchListingPage(1)).thenReturn(page());

        pollerAt(THURSDAY_3PM).tick();

        verify(live).refresh();
    }

    /** Lỗi khi làm mới dự báo không được ném ra khỏi @Scheduled. */
    @Test
    void aFailingLiveRefreshDoesNotEscapeTick() throws Exception {
        when(scraper.fetchListingPage(1)).thenReturn(page());
        when(live.refresh()).thenThrow(new IllegalStateException("DB lỗi"));

        pollerAt(THURSDAY_3PM).tick();       // không được ném

        verify(live).refresh();
    }

    /**
     * Lỗi mạng không được làm chết bộ lập lịch (một exception thoát ra khỏi
     * @Scheduled có thể huỷ luôn các lần chạy sau) và không được biến thành vòng
     * lặp hỏi dồn dập: mốc thời gian vẫn được ghi.
     */
    @Test
    void aFailedPollDoesNotThrowAndIsNotRetriedImmediately() throws Exception {
        when(scraper.fetchListingPage(1)).thenThrow(new IOException("MOIT trả HTTP 503"));
        BulletinPoller poller = pollerAt(THURSDAY_3PM);

        poller.tick();      // không được ném
        poller.tick();      // vẫn cùng thời điểm: chưa đến nhịp, không thử lại

        verify(scraper, times(1)).fetchListingPage(1);
    }
}
