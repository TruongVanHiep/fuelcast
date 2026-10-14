package fitiuh.com.fuelcast_core.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PollScheduleTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);

    /** 8/10/2026 là thứ Năm; 7/10 thứ Tư; 10/10 thứ Bảy. */
    private static Instant at(int day, int hour, int minute) {
        return OffsetDateTime.of(2026, 10, day, hour, minute, 0, 0, VN).toInstant();
    }

    // ───────────────────────── khung thứ Năm ─────────────────────────

    @Test
    void windowOpensAtQuarterToThreeOnThursday() {
        assertThat(PollSchedule.inWindow(at(8, 14, 44))).isFalse();
        assertThat(PollSchedule.inWindow(at(8, 14, 45))).isTrue();
    }

    @Test
    void windowClosesAtFiveSharp() {
        assertThat(PollSchedule.inWindow(at(8, 16, 59))).isTrue();
        assertThat(PollSchedule.inWindow(at(8, 17, 0))).isFalse();
    }

    @Test
    void sameHoursOnOtherDaysAreNotInTheWindow() {
        assertThat(PollSchedule.inWindow(at(7, 15, 0))).isFalse();     // thứ Tư
        assertThat(PollSchedule.inWindow(at(10, 15, 0))).isFalse();    // thứ Bảy
    }

    /**
     * 08:00Z thứ Năm là 15:00 giờ VN, nằm trong khung. Nhưng 17:00Z thứ Tư đã là
     * 00:00 thứ Năm giờ VN và chưa tới khung: tính theo UTC sẽ sai ngày.
     */
    @Test
    void usesVietnamClockNotUtc() {
        assertThat(PollSchedule.inWindow(OffsetDateTime.of(2026, 10, 8, 8, 0, 0, 0, ZoneOffset.UTC).toInstant()))
                .isTrue();
        assertThat(PollSchedule.inWindow(OffsetDateTime.of(2026, 10, 7, 17, 0, 0, 0, ZoneOffset.UTC).toInstant()))
                .isFalse();
    }

    @Test
    void intervalIsDenseInsideTheWindowAndSparseOutside() {
        assertThat(PollSchedule.interval(at(8, 15, 0))).isEqualTo(Duration.ofMinutes(10));
        assertThat(PollSchedule.interval(at(9, 10, 0))).isEqualTo(Duration.ofHours(6));
    }

    // ───────────────────────── đã đến lúc chưa ─────────────────────────

    @Test
    void firstPollIsAlwaysDue() {
        assertThat(PollSchedule.due(at(9, 10, 0), null)).isTrue();
    }

    @Test
    void insideTheWindowPollsEveryTenMinutes() {
        Instant last = at(8, 15, 0);

        assertThat(PollSchedule.due(at(8, 15, 9), last)).isFalse();
        assertThat(PollSchedule.due(at(8, 15, 10), last)).isTrue();
    }

    @Test
    void outsideTheWindowPollsOnlyEverySixHours() {
        Instant last = at(9, 6, 0);        // thứ Sáu 06:00

        assertThat(PollSchedule.due(at(9, 11, 59), last)).isFalse();
        assertThat(PollSchedule.due(at(9, 12, 0), last)).isTrue();
    }

    /**
     * Lần hỏi cuối lúc 14:40, khi còn ở nhịp thưa (6 giờ). Khung mở từ 14:45 nên
     * đến 14:50 phải hỏi ngay (đủ 10 phút theo nhịp dày) thay vì chờ tới 20:40, nếu
     * không sẽ lỡ đúng bản tin 15:00.
     */
    @Test
    void enteringTheWindowTriggersAPollWithoutWaitingSixHours() {
        Instant last = at(8, 14, 40);

        assertThat(PollSchedule.due(at(8, 14, 49), last)).isFalse();    // đã vào khung nhưng mới 9 phút
        assertThat(PollSchedule.due(at(8, 14, 50), last)).isTrue();     // đủ 10 phút theo nhịp dày
    }
}
