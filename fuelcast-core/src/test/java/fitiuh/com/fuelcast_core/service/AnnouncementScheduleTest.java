package fitiuh.com.fuelcast_core.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AnnouncementScheduleTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);

    private static OffsetDateTime at(int y, int m, int d, int h, int min) {
        return OffsetDateTime.of(y, m, d, h, min, 0, 0, VN);
    }

    /** Ca phổ biến nhất (~94%): vừa công bố thứ Năm 15:00 thì kỳ sau là thứ Năm tuần sau. */
    @Test
    void nextAfterAThursdayAnnouncementIsTheFollowingThursday() {
        // 8/10/2026 là thứ Năm
        assertThat(AnnouncementSchedule.nextAfter(at(2026, 10, 8, 15, 0)))
                .isEqualTo(at(2026, 10, 15, 15, 0));
    }

    /** "Sau hẳn": đúng 15:00 thì không được trả về chính lúc đó. */
    @Test
    void nextIsStrictlyAfterTheLastAnnouncement() {
        OffsetDateTime last = at(2026, 10, 8, 15, 0);

        assertThat(AnnouncementSchedule.nextAfter(last)).isAfter(last);
    }

    /** Có kỳ công bố 15:30 hoặc 16:00 thứ Năm: vẫn qua mốc 15:00 nên kỳ sau là tuần sau. */
    @Test
    void lateThursdayAnnouncementStillMeansNextWeek() {
        assertThat(AnnouncementSchedule.nextAfter(at(2026, 10, 8, 16, 0)))
                .isEqualTo(at(2026, 10, 15, 15, 0));
    }

    /** Công bố sớm hơn 15:00 cùng ngày thứ Năm thì mốc 15:00 hôm đó vẫn còn phía trước. */
    @Test
    void announcementBeforeThreePmThursdayLeavesThatAfternoonAhead() {
        assertThat(AnnouncementSchedule.nextAfter(at(2026, 10, 8, 9, 0)))
                .isEqualTo(at(2026, 10, 8, 15, 0));
    }

    /** Kỳ nửa đêm 27/3/2026 (thứ Sáu 00:00): thứ Năm kế tiếp là 2/4. */
    @Test
    void fridayMidnightAnnouncementPointsToNextThursday() {
        assertThat(AnnouncementSchedule.nextAfter(at(2026, 3, 27, 0, 0)))
                .isEqualTo(at(2026, 4, 2, 15, 0));
    }

    /** Dịp lễ dời sang thứ Tư 22:00: kỳ sau là thứ Năm 15:00 ngay hôm sau. */
    @Test
    void wednesdayNightAnnouncementPointsToThursdayAfternoon() {
        // 7/1/2026 là thứ Tư
        assertThat(AnnouncementSchedule.nextAfter(at(2026, 1, 7, 22, 0)))
                .isEqualTo(at(2026, 1, 8, 15, 0));
    }

    /** Đầu vào ở UTC phải cho cùng kết quả: DB trả về offset UTC, không phải +07:00. */
    @Test
    void worksOnUtcInputBecauseTheDatabaseReturnsUtc() {
        OffsetDateTime utc = OffsetDateTime.of(2026, 10, 8, 8, 0, 0, 0, ZoneOffset.UTC); // = 15:00 VN

        assertThat(AnnouncementSchedule.nextAfter(utc)).isEqualTo(at(2026, 10, 15, 15, 0));
    }

    /**
     * Ca dễ sai về múi giờ: 17:00Z thứ Tư là 00:00 thứ Năm giờ VN. Nếu tính theo
     * UTC sẽ lệch một ngày.
     */
    @Test
    void usesVietnamCalendarDayNotUtcDay() {
        OffsetDateTime utc = OffsetDateTime.of(2026, 10, 7, 17, 0, 0, 0, ZoneOffset.UTC); // = 00:00 thứ Năm 8/10 VN

        assertThat(AnnouncementSchedule.nextAfter(utc)).isEqualTo(at(2026, 10, 8, 15, 0));
    }
}
