package fitiuh.com.fuelcast_core.entity;

/** Khớp CHECK constraint của ingestion_run.status trong V1. */
public enum IngestionStatus {
    /** Đã bắt đầu, chưa kết thúc. Một dòng kẹt ở đây nghĩa là tiến trình chết giữa chừng. */
    RUNNING,

    SUCCESS,

    /** Nạp được một phần, ví dụ bản tin thiếu vài mặt hàng. */
    PARTIAL,

    FAILED
}
