package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.api.CurrentCycleDto;
import fitiuh.com.fuelcast_core.entity.AdjustmentCycle;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/** Phía đọc của kỳ điều hành: lần công bố gần nhất và lần kế tiếp dự kiến. */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CycleQueryService {

    AdjustmentCycleService cycles;
    Clock clock;

    /**
     * DB không có kỳ OPEN nào (mọi kỳ đều được tạo ở trạng thái ANNOUNCED lúc nạp
     * bản tin), nên "kỳ hiện tại" được suy ra từ lần công bố gần nhất: kỳ đang chạy
     * là khoảng từ đó tới lần công bố kế tiếp.
     */
    @Transactional(readOnly = true)
    public CurrentCycleDto currentCycle() {
        AdjustmentCycle last = cycles.findLatestAnnounced().orElseThrow(NoCycleDataException::new);

        OffsetDateTime lastEffectiveAt = effectiveAtOf(last);
        OffsetDateTime expectedNext = AnnouncementSchedule.nextAfter(lastEffectiveAt);

        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean overdue = now.isAfter(expectedNext);
        long secondsUntilNext = overdue ? 0 : Duration.between(now, expectedNext).getSeconds();

        return new CurrentCycleDto(lastEffectiveAt, last.getCycleStart(), last.getCycleEnd(),
                expectedNext, secondsUntilNext, overdue);
    }

    /**
     * Thời điểm giá có hiệu lực. effective_from và announced_at đều cho phép null
     * trong schema; thiếu cả hai thì lấy 15:00 giờ VN của cycle_end, đúng quy ước
     * mà nạp CSV dùng khi dựng kỳ.
     */
    private static OffsetDateTime effectiveAtOf(AdjustmentCycle cycle) {
        if (cycle.getEffectiveFrom() != null) {
            return cycle.getEffectiveFrom();
        }
        if (cycle.getAnnouncedAt() != null) {
            return cycle.getAnnouncedAt();
        }
        return cycle.getCycleEnd().atTime(AnnouncementSchedule.HOUR, 0)
                .atZone(AnnouncementSchedule.VN).toOffsetDateTime();
    }
}
