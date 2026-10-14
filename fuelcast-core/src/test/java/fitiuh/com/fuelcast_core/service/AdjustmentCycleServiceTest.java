package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.entity.AdjustmentCycle;
import fitiuh.com.fuelcast_core.entity.CycleStatus;
import fitiuh.com.fuelcast_core.repository.AdjustmentCycleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdjustmentCycleServiceTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime THU_8_10 = OffsetDateTime.of(2026, 10, 8, 15, 0, 0, 0, VN);
    private static final LocalDate END = LocalDate.of(2026, 10, 8);
    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    private final AdjustmentCycleRepository repo = mock(AdjustmentCycleRepository.class);
    private final AdjustmentCycleService service = new AdjustmentCycleService(repo);

    // ───────────────────────── openCycleFor ─────────────────────────

    @Test
    void opensACycleForTheExpectedDateWhenNoneExists() {
        when(repo.findByCycleStartAndCycleEnd(START, END)).thenReturn(Optional.empty());
        when(repo.save(any(AdjustmentCycle.class))).thenAnswer(inv -> inv.getArgument(0));

        AdjustmentCycle c = service.openCycleFor(THU_8_10);

        assertThat(c.getStatus()).isEqualTo(CycleStatus.OPEN);
        assertThat(c.getCycleStart()).isEqualTo(START);
        assertThat(c.getCycleEnd()).isEqualTo(END);
        assertThat(c.getAnnouncedAt()).isNull();
    }

    @Test
    void reusesTheCycleThatAlreadyExistsForThatDate() {
        AdjustmentCycle existing = AdjustmentCycle.builder().id(7L).cycleStart(START).cycleEnd(END)
                .status(CycleStatus.OPEN).build();
        when(repo.findByCycleStartAndCycleEnd(START, END)).thenReturn(Optional.of(existing));

        assertThat(service.openCycleFor(THU_8_10)).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    // ───────────── bản tin tới đúng ngày dự kiến: thăng cấp, không tạo trùng ─────────────

    /**
     * Dự báo LIVE đã trỏ vào kỳ OPEN. Khi bản tin tới đúng ngày thì phải thăng cấp
     * chính dòng đó lên ANNOUNCED, nếu không sẽ có hai dòng cho cùng một kỳ và vi
     * phạm ràng buộc duy nhất (cycle_start, cycle_end).
     */
    @Test
    void promotesTheOpenCycleWhenTheBulletinArrivesOnTheExpectedDay() {
        AdjustmentCycle open = AdjustmentCycle.builder().id(7L).cycleStart(START).cycleEnd(END)
                .status(CycleStatus.OPEN).build();
        when(repo.findByCycleStartAndCycleEnd(START, END)).thenReturn(Optional.of(open));
        when(repo.save(any(AdjustmentCycle.class))).thenAnswer(inv -> inv.getArgument(0));

        AdjustmentCycle c = service.findOrCreateAdjustmentCycle(THU_8_10);

        assertThat(c.getId()).isEqualTo(7L);                       // cùng dòng
        assertThat(c.getStatus()).isEqualTo(CycleStatus.ANNOUNCED);
        assertThat(c.getAnnouncedAt()).isEqualTo(THU_8_10);
        assertThat(c.getEffectiveFrom()).isEqualTo(THU_8_10);
    }

    @Test
    void leavesAnAlreadyAnnouncedCycleUntouched() {
        AdjustmentCycle announced = AdjustmentCycle.builder().id(8L).cycleStart(START).cycleEnd(END)
                .status(CycleStatus.ANNOUNCED).build();
        when(repo.findByCycleStartAndCycleEnd(START, END)).thenReturn(Optional.of(announced));

        AdjustmentCycle c = service.findOrCreateAdjustmentCycle(THU_8_10);

        assertThat(c).isSameAs(announced);
        verify(repo, never()).save(any());
    }

    @Test
    void createsAnAnnouncedCycleWhenThereIsNoneForThatDay() {
        when(repo.findByCycleStartAndCycleEnd(START, END)).thenReturn(Optional.empty());
        when(repo.save(any(AdjustmentCycle.class))).thenAnswer(inv -> inv.getArgument(0));

        AdjustmentCycle c = service.findOrCreateAdjustmentCycle(THU_8_10);

        ArgumentCaptor<AdjustmentCycle> saved = ArgumentCaptor.forClass(AdjustmentCycle.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CycleStatus.ANNOUNCED);
        assertThat(c.getEffectiveFrom()).isEqualTo(THU_8_10);
    }
}
