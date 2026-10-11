package fitiuh.com.fuelcast_core.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Quy luật công bố giá: chiều thứ Năm, 15:00 giờ Việt Nam.
 *
 * Đây là THÓI QUEN chứ không phải quy định cứng. Trên dữ liệu thật, 123 trong
 * khoảng 131 kỳ đúng thứ Năm 15:00 (~94%); số còn lại lệch sang thứ Tư, thứ Bảy
 * hay 22:00 vì ngày lễ và điều chỉnh đột xuất. Vì vậy kết quả luôn phải được
 * gọi là thời điểm "dự kiến".
 */
public final class AnnouncementSchedule {

    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final DayOfWeek DAY = DayOfWeek.THURSDAY;
    static final int HOUR = 15;

    private AnnouncementSchedule() { }

    /**
     * Thứ Năm 15:00 đầu tiên SAU hẳn lần công bố cuối. "Sau hẳn" nghĩa là lần
     * công bố đúng thứ Năm 15:00 thì kỳ kế tiếp là tuần sau, không phải cùng lúc.
     */
    public static OffsetDateTime nextAfter(OffsetDateTime last) {
        ZonedDateTime lastVn = last.atZoneSameInstant(VN);

        LocalDate thursday = lastVn.toLocalDate().with(TemporalAdjusters.nextOrSame(DAY));
        ZonedDateTime candidate = thursday.atTime(HOUR, 0).atZone(VN);

        if (!candidate.isAfter(lastVn)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate.toOffsetDateTime();
    }
}
