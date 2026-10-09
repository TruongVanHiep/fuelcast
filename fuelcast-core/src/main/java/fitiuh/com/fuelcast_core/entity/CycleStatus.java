package fitiuh.com.fuelcast_core.entity;

public enum CycleStatus {
    /** Kỳ đang chạy, đang gom dữ liệu giá thế giới, chưa công bố. */
    OPEN,

    /** Đã có giá công bố; dự báo của kỳ này chưa được chấm điểm. */
    ANNOUNCED,

    /** Đã chấm điểm xong mọi dự báo của kỳ. */
    SETTLED
}
