package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.api.CurrentCycleDto;
import fitiuh.com.fuelcast_core.entity.AdjustmentCycle;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CycleQueryServiceTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime LAST = OffsetDateTime.of(2026, 10, 8, 15, 0, 0, 0, VN);

    private final AdjustmentCycleService cycles = mock(AdjustmentCycleService.class);

    private CycleQueryService serviceAt(OffsetDateTime now) {
        return new CycleQueryService(cycles, Clock.fixed(now.toInstant(), VN));
    }

    private static AdjustmentCycle lastCycle() {
        return AdjustmentCycle.builder()
                .cycleStart(LocalDate.of(2026, 10, 1)).cycleEnd(LocalDate.of(2026, 10, 8))
                .effectiveFrom(LAST).build();
    }

    @Test
    void countsDownToNextThursdayAfternoon() {
        when(cycles.findLatestAnnounced()).thenReturn(Optional.of(lastCycle()));
        // Chủ nhật 11/10 lúc 15:00: còn đúng 4 ngày tới thứ Năm 15/10 15:00
        CurrentCycleDto dto = serviceAt(OffsetDateTime.of(2026, 10, 11, 15, 0, 0, 0, VN)).currentCycle();

        assertThat(dto.lastEffectiveAt()).isEqualTo(LAST);
        assertThat(dto.lastCycleStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(dto.lastCycleEnd()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(dto.expectedNextAt()).isEqualTo(OffsetDateTime.of(2026, 10, 15, 15, 0, 0, 0, VN));
        assertThat(dto.secondsUntilNext()).isEqualTo(4L * 24 * 3600);
        assertThat(dto.overdue()).isFalse();
    }

    /**
     * Đã quá giờ dự kiến mà chưa có bản tin mới (ví dụ dịp lễ dời ngày): không
     * được trả số giây âm, mà báo quá hạn.
     */
    @Test
    void reportsOverdueWithZeroSecondsInsteadOfNegative() {
        when(cycles.findLatestAnnounced()).thenReturn(Optional.of(lastCycle()));
        // Thứ Năm 15/10 lúc 17:00, đã quá 15:00 hai tiếng mà DB chưa có kỳ mới
        CurrentCycleDto dto = serviceAt(OffsetDateTime.of(2026, 10, 15, 17, 0, 0, 0, VN)).currentCycle();

        assertThat(dto.overdue()).isTrue();
        assertThat(dto.secondsUntilNext()).isZero();
    }

    @Test
    void notOverdueExactlyAtTheExpectedInstant() {
        when(cycles.findLatestAnnounced()).thenReturn(Optional.of(lastCycle()));

        CurrentCycleDto dto = serviceAt(OffsetDateTime.of(2026, 10, 15, 15, 0, 0, 0, VN)).currentCycle();

        assertThat(dto.overdue()).isFalse();
        assertThat(dto.secondsUntilNext()).isZero();
    }

    /** effective_from được phép null trong schema: lùi về announced_at. */
    @Test
    void fallsBackToAnnouncedAtWhenEffectiveFromIsMissing() {
        AdjustmentCycle cycle = AdjustmentCycle.builder()
                .cycleStart(LocalDate.of(2026, 10, 1)).cycleEnd(LocalDate.of(2026, 10, 8))
                .announcedAt(LAST).build();
        when(cycles.findLatestAnnounced()).thenReturn(Optional.of(cycle));

        CurrentCycleDto dto = serviceAt(OffsetDateTime.of(2026, 10, 9, 8, 0, 0, 0, VN)).currentCycle();

        assertThat(dto.lastEffectiveAt()).isEqualTo(LAST);
    }

    /** Thiếu cả hai: dùng 15:00 giờ VN của cycle_end. */
    @Test
    void fallsBackToThreePmOfCycleEndWhenBothTimesAreMissing() {
        AdjustmentCycle cycle = AdjustmentCycle.builder()
                .cycleStart(LocalDate.of(2026, 10, 1)).cycleEnd(LocalDate.of(2026, 10, 8)).build();
        when(cycles.findLatestAnnounced()).thenReturn(Optional.of(cycle));

        CurrentCycleDto dto = serviceAt(OffsetDateTime.of(2026, 10, 9, 8, 0, 0, 0, VN)).currentCycle();

        assertThat(dto.lastEffectiveAt()).isEqualTo(LAST);
    }

    @Test
    void failsWhenNoCycleHasBeenAnnouncedYet() {
        when(cycles.findLatestAnnounced()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAt(LAST).currentCycle()).isInstanceOf(NoCycleDataException.class);
    }
}
