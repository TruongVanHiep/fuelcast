package fitiuh.com.fuelcast_core.forecast;

/**
 * Hồi quy tuyến tính một biến y = a + b·x bằng bình phương tối thiểu, cộng dồn
 * từng điểm. Chỉ cần năm tổng nên thêm một điểm là O(1), không phải tính lại.
 *
 * Cố ý tự viết thay vì kéo thư viện thống kê: công thức ngắn, và người đọc thấy
 * rõ mô hình học gì. Kết quả khớp regr_slope / regr_intercept của PostgreSQL.
 */
final class OnlineOls {

    private long n;
    private double sumX;
    private double sumY;
    private double sumXX;
    private double sumXY;

    void add(double x, double y) {
        n++;
        sumX += x;
        sumY += y;
        sumXX += x * x;
        sumXY += x * y;
    }

    long count() {
        return n;
    }

    /** Cần ít nhất hai điểm và x phải thay đổi, nếu không đường thẳng không xác định. */
    boolean canPredict() {
        return n >= 2 && denominator() > 0;
    }

    double slope() {
        return (n * sumXY - sumX * sumY) / denominator();
    }

    double intercept() {
        return (sumY - slope() * sumX) / n;
    }

    double predict(double x) {
        return intercept() + slope() * x;
    }

    private double denominator() {
        return n * sumXX - sumX * sumX;
    }
}
