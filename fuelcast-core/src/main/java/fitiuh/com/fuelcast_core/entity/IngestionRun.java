package fitiuh.com.fuelcast_core.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

/**
 * Nhật ký một lần nạp dữ liệu từ một nguồn, kèm bản gốc của thứ đã tải về.
 *
 * Lưu raw_payload TRƯỚC khi parse: khi MOIT đổi layout và parser gãy, ta sửa
 * parser rồi parse lại từ chính HTML đã lưu, không phải crawl lại và không mất
 * bản tin nào.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@Table(name = "ingestion_run")
@NoArgsConstructor(force = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /** Tên nguồn, ví dụ MOIT_BULLETIN. */
    @Column(nullable = false, length = 40)
    String source;

    /**
     * Hibernate luôn ghi cột này một cách tường minh nên DEFAULT now() của DB
     * không được dùng tới; phải tự gán, nếu không INSERT vi phạm NOT NULL.
     */
    @Builder.Default
    @Column(name = "started_at", nullable = false)
    OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "finished_at")
    OffsetDateTime finishedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    IngestionStatus status = IngestionStatus.RUNNING;

    @Builder.Default
    @Column(name = "rows_ingested", nullable = false)
    int rowsIngested = 0;

    @Column(name = "target_url", length = 500)
    String targetUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    String errorMessage;

    /** HTML gốc. columnDefinition để không bị hiểu thành varchar(255). */
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    String rawPayload;

    public void succeed(int rows) {
        this.status = IngestionStatus.SUCCESS;
        this.rowsIngested = rows;
        this.finishedAt = OffsetDateTime.now();
    }

    public void fail(String message) {
        this.status = IngestionStatus.FAILED;
        this.errorMessage = message;
        this.finishedAt = OffsetDateTime.now();
    }
}
