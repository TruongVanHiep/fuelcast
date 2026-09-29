package fitiuh.com.fuelcast_core.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Một kỳ điều hành giá — trục xương sống của toàn hệ thống.
 * Mỗi kỳ kéo dài 7 ngày, công bố chiều thứ Năm, và đi qua
 * OPEN → ANNOUNCED → SETTLED.
 */

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@Table(name = "adjustment_cycle")
@NoArgsConstructor(force = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdjustmentCycle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /** Ngày đầu khoảng lấy bình quân giá Platts. */
    @Column(name = "cycle_start", nullable = false)
    LocalDate cycleStart;

    @Column(name = "cycle_end", nullable = false)
    LocalDate cycleEnd;

    /** Thời điểm Bộ Công Thương công bố; null khi kỳ còn đang chạy. */
    @Column(name = "announced_at")
    OffsetDateTime announcedAt;

    /** Thời điểm giá mới có hiệu lực, thường 15h00 cùng ngày. */
    @Column(name = "effective_from")
    OffsetDateTime effectiveFrom;

    /** Số công văn, ví dụ 5280/BCT-TTTN. */
    @Column(name = "document_no", length = 50)
    String documentNo;

    /** Link bản tin gốc trên moit.gov.vn. */
    @Column(name = "source_url", length = 500)
    String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    CycleStatus status;

}
