package fitiuh.com.fuelcast_core.scheduler;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Khi nào thì đi hỏi MOIT xem có bản tin mới.
 *
 * Hai nhịp, vì dữ liệu thật có hai loại kỳ: khoảng 94% công bố đúng thứ Năm 15:00,
 * khoảng 6% còn lại lệch sang thứ Tư, thứ Bảy hay 22:00 quanh ngày lễ và điều
 * chỉnh đột xuất.
 *
 *  - Trong khung thứ Năm 14:45 đến 17:00 (giờ VN): hỏi mỗi 10 phút, để thấy bản
 *    tin gần như ngay khi nó xuất hiện.
 *  - Ngoài khung: hỏi thưa, mỗi 6 giờ, đủ để bắt kỳ bị dời ngày mà không dồn dập
 *    vào máy chủ của người khác.
 *
 * Hàm thuần theo thời gian, không đọc đồng hồ hệ thống, nên test được bằng cách
 * truyền thời điểm vào.
 */
public final class PollSchedule {

    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final DayOfWeek WINDOW_DAY = DayOfWeek.THURSDAY;
    static final LocalTime WINDOW_START = LocalTime.of(14, 45);
    static final LocalTime WINDOW_END = LocalTime.of(17, 0);

    static final Duration IN_WINDOW = Duration.ofMinutes(10);
    static final Duration OFF_WINDOW = Duration.ofHours(6);

    private PollSchedule() { }

    /** [14:45, 17:00) thứ Năm theo giờ Việt Nam. */
    public static boolean inWindow(Instant now) {
        ZonedDateTime vn = now.atZone(VN);
        if (vn.getDayOfWeek() != WINDOW_DAY) {
            return false;
        }
        LocalTime t = vn.toLocalTime();
        return !t.isBefore(WINDOW_START) && t.isBefore(WINDOW_END);
    }

    public static Duration interval(Instant now) {
        return inWindow(now) ? IN_WINDOW : OFF_WINDOW;
    }

    /**
     * Đã đến lúc hỏi chưa. Khoảng cách tính theo nhịp của thời điểm HIỆN TẠI chứ
     * không phải nhịp lúc hỏi lần trước: lần hỏi cuối lúc 14:40 (nhịp thưa) thì
     * tới 14:50 đã vào khung và phải hỏi ngay, không chờ đủ 6 giờ.
     */
    public static boolean due(Instant now, Instant lastPolledAt) {
        if (lastPolledAt == null) {
            return true;
        }
        return !now.isBefore(lastPolledAt.plus(interval(now)));
    }
}
