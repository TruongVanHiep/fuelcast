package fitiuh.com.fuelcast_core.forecast;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Backtest walk-forward của mô hình "truyền dẫn": mức thay đổi giá bán lẻ
 * (đồng) được đoán từ mức thay đổi giá thế giới trung bình của kỳ (USD).
 *
 * Quy tắc sống còn là KHÔNG NHÌN TƯƠNG LAI: tại mỗi kỳ, hệ số chỉ được học từ các
 * kỳ trước đó, rồi mới đoán, rồi mới thêm kỳ này vào dữ liệu học. Đảo thứ tự ba
 * bước này là leakage, lỗi kinh điển làm con số đẹp một cách giả tạo.
 */
public final class PassThroughBacktest {

    /** Số kỳ tối thiểu để học trước khi bắt đầu đoán. */
    public static final int DEFAULT_MIN_TRAIN = 20;

    private PassThroughBacktest() { }

    /** Một kỳ: giá bán lẻ đổi deltaVnd khi giá thế giới trung bình đổi deltaUsd. */
    public record Observation(OffsetDateTime at, double deltaVnd, double deltaUsd) { }

    /**
     * @param maeNaive  sai số tuyệt đối trung bình khi đoán "không đổi" (Δ = 0)
     * @param maeModel  sai số tuyệt đối trung bình của mô hình
     * @param hitRate   tỷ lệ đoán đúng chiều tăng/giảm, trong [0, 1]
     */
    public record Result(int nTest, double maeNaive, double maeModel, double hitRate) {

        /** Mô hình giảm được bao nhiêu phần sai số so với đoán "không đổi". */
        public double gain() {
            return maeNaive == 0 ? 0 : 1 - maeModel / maeNaive;
        }

        /** Không thắng đường cơ sở ngây thơ thì mô hình vô dụng. */
        public boolean beatsNaive() {
            return nTest > 0 && maeModel < maeNaive;
        }
    }

    public static Result run(List<Observation> observations, int minTrain) {
        OnlineOls ols = new OnlineOls();
        int n = 0;
        double sumNaive = 0;
        double sumModel = 0;
        int hits = 0;

        for (Observation o : observations) {      // đã sắp theo thời gian tăng dần
            if (ols.count() >= minTrain && ols.canPredict()) {
                double predicted = ols.predict(o.deltaUsd());   // 1. đoán bằng quá khứ
                n++;
                sumNaive += Math.abs(o.deltaVnd());
                sumModel += Math.abs(o.deltaVnd() - predicted);
                if (Math.signum(predicted) == Math.signum(o.deltaVnd())) {
                    hits++;
                }
            }
            ols.add(o.deltaUsd(), o.deltaVnd());                // 2. rồi mới học kỳ này
        }

        if (n == 0) {
            return new Result(0, 0, 0, 0);
        }
        return new Result(n, sumNaive / n, sumModel / n, (double) hits / n);
    }
}
