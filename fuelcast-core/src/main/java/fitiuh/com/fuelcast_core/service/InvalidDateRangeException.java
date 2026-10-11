package fitiuh.com.fuelcast_core.service;

import java.time.LocalDate;

/**
 * Khoảng ngày vô lý (from sau to). Tách thành exception riêng thay vì dùng
 * IllegalArgumentException: tầng API đổi nó thành HTTP 400, và nếu dùng exception
 * chung thì mọi lỗi lập trình ném IllegalArgumentException cũng thành 400.
 */
public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException(LocalDate from, LocalDate to) {
        super("Ngày bắt đầu " + from + " không được sau ngày kết thúc " + to);
    }
}
