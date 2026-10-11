package fitiuh.com.fuelcast_core.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Trạng thái kỳ điều hành hiện tại: lần công bố gần nhất và lần kế tiếp dự kiến.
 *
 * @param lastEffectiveAt   thời điểm giá của lần công bố gần nhất có hiệu lực
 * @param lastCycleStart    ngày đầu của kỳ đó (đầu khoảng lấy bình quân giá)
 * @param lastCycleEnd      ngày cuối của kỳ đó
 * @param expectedNextAt    thời điểm công bố kế tiếp DỰ KIẾN: thứ Năm 15:00 theo
 *                          thói quen, không phải cam kết. Khoảng 6% kỳ lệch.
 * @param secondsUntilNext  số giây còn lại; 0 khi đã quá hạn (không bao giờ âm)
 * @param overdue           true nếu đã quá giờ dự kiến mà chưa có bản tin mới, ví
 *                          dụ dịp lễ dời ngày. Client nên hiện "chưa công bố".
 */
public record CurrentCycleDto(
        OffsetDateTime lastEffectiveAt,
        LocalDate lastCycleStart,
        LocalDate lastCycleEnd,
        OffsetDateTime expectedNextAt,
        long secondsUntilNext,
        boolean overdue
) { }
