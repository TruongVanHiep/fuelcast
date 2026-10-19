package fitiuh.com.fuelcast_core.observability;

import fitiuh.com.fuelcast_core.api.CurrentCycleDto;
import fitiuh.com.fuelcast_core.service.CycleQueryService;
import fitiuh.com.fuelcast_core.service.NoCycleDataException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulletinFreshnessMetricsTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime LAST = OffsetDateTime.of(2026, 10, 15, 15, 0, 0, 0, VN);

    private final CycleQueryService cycles = mock(CycleQueryService.class);
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock(LAST.plusDays(1).toInstant());

    private final BulletinFreshnessMetrics metrics = new BulletinFreshnessMetrics(registry, cycles, clock);

    /** Đồng hồ chạy được: để thử việc cache hết hạn mà không phải ngủ thật. */
    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advance(Duration d) {
            now.updateAndGet(i -> i.plus(d));
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    private static CurrentCycleDto cycleEffectiveAt(OffsetDateTime at) {
        return new CurrentCycleDto(at, at.toLocalDate().minusDays(7), at.toLocalDate(),
                at.plusWeeks(1), 0, false);
    }

    private double value() {
        return registry.get(BulletinFreshnessMetrics.NAME).gauge().value();
    }

    @Test
    void reportsTheEffectiveTimeOfTheLatestBulletinAsEpochSeconds() {
        when(cycles.currentCycle()).thenReturn(cycleEffectiveAt(LAST));

        assertThat(value()).isEqualTo(LAST.toEpochSecond());
    }

    /** NaN là "không có số liệu"; 0 sẽ bị đọc là "bản tin năm 1970" và làm cảnh báo kêu oan. */
    @Test
    void isNaNNotZeroWhenThereIsNoBulletinYet() {
        when(cycles.currentCycle()).thenThrow(new NoCycleDataException());

        assertThat(value()).isNaN();
    }

    /** Prometheus quét mỗi 15 giây mà bản tin đổi mỗi tuần: không được truy vấn DB ở từng lần quét. */
    @Test
    void doesNotHitTheDatabaseOnEveryScrape() {
        when(cycles.currentCycle()).thenReturn(cycleEffectiveAt(LAST));

        value();
        clock.advance(Duration.ofSeconds(15));
        value();
        clock.advance(Duration.ofSeconds(15));
        value();

        verify(cycles, times(1)).currentCycle();
    }

    @Test
    void readsAgainOnceTheCacheHasExpired() {
        when(cycles.currentCycle()).thenReturn(cycleEffectiveAt(LAST));
        value();

        OffsetDateTime newer = LAST.plusWeeks(1);
        when(cycles.currentCycle()).thenReturn(cycleEffectiveAt(newer));
        clock.advance(BulletinFreshnessMetrics.CACHE_TTL.plusSeconds(1));

        assertThat(value()).isEqualTo(newer.toEpochSecond());
        verify(cycles, times(2)).currentCycle();
    }

    /** DB lỗi thoáng qua: giữ giá trị đọc được lần trước, không nhảy về NaN làm biểu đồ đứt. */
    @Test
    void keepsTheLastKnownValueWhenTheDatabaseFails() {
        when(cycles.currentCycle()).thenReturn(cycleEffectiveAt(LAST));
        value();

        when(cycles.currentCycle()).thenThrow(new IllegalStateException("DB sập"));
        clock.advance(BulletinFreshnessMetrics.CACHE_TTL.plusSeconds(1));

        assertThat(value()).isEqualTo(LAST.toEpochSecond());
    }

    @Test
    void retriesOnTheNextScrapeAfterAFailure() {
        when(cycles.currentCycle()).thenThrow(new IllegalStateException("DB sập"));
        value();

        // doReturn thay vì when(...): when() gọi chính hàm đang bị cài ném lỗi nên ném ngay lúc cài.
        doReturn(cycleEffectiveAt(LAST)).when(cycles).currentCycle();

        // Không tăng đồng hồ: một lần đọc lỗi không được đóng băng kết quả trong cả phút cache.
        assertThat(value()).isEqualTo(LAST.toEpochSecond());
    }
}
