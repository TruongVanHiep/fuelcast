package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.service.InvalidDateRangeException;
import fitiuh.com.fuelcast_core.service.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Đổi lỗi nghiệp vụ thành phản hồi RFC 7807 (application/problem+json).
 *
 * Lỗi chuẩn của Spring (thiếu tham số, sai kiểu ngày) được spring.mvc.problemdetails
 * xử lý; ở đây chỉ phần của riêng ứng dụng. Lỗi không lường trước thì để rơi ra
 * thành 500: không bắt Exception chung ở đây, vì lỗi lập trình không được giả
 * làm lỗi của người dùng.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail productNotFound(ProductNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Mặt hàng không tồn tại");
        return problem;
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ProblemDetail invalidDateRange(InvalidDateRangeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Khoảng ngày không hợp lệ");
        return problem;
    }
}
