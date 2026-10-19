package fitiuh.com.fuelcast_core.stream;

/** Đã đủ số kết nối SSE tối đa; kết nối mới bị từ chối thay vì để cạn luồng và bộ nhớ. */
public class TooManyStreamsException extends RuntimeException {

    public TooManyStreamsException(int max) {
        super("Đã đạt tối đa " + max + " kết nối theo dõi trực tiếp. Hãy thử lại sau ít phút.");
    }
}
