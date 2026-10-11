package fitiuh.com.fuelcast_core.service;

/** Chưa có kỳ điều hành nào được công bố trong DB. Tầng API đổi thành HTTP 404. */
public class NoCycleDataException extends RuntimeException {

    public NoCycleDataException() {
        super("Chưa có dữ liệu kỳ điều hành nào được công bố");
    }
}
