package fitiuh.com.fuelcast_core.forecast;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
     * Một lần đoán của walk-forward, kèm cả những gì mô hình đã dùng để đoán, để
     * lưu lại và giải thích được.
     *
     * @param slope          đồng/lít trên mỗi USD/thùng, học từ trainPeriods kỳ trước
     * @param trainPeriods   số kỳ đã học tại thời điểm đoán
     */
    public record Step(Observation observation, double predictedDeltaVnd,
                       double slope, double intercept, int trainPeriods) { }

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

    /**
     * Đi theo thời gian, mỗi kỳ chỉ đoán bằng các kỳ trước đó. Trả về từng lần
     * đoán; kỳ đầu (chưa đủ lịch sử để học) không có dòng nào.
     */
    public static List<Step> walkForward(List<Observation> observations, int minTrain) {
        OnlineOls ols = new OnlineOls();
        List<Step> steps = new ArrayList<>();

        for (Observation o : observations) {      // đã sắp theo thời gian tăng dần
            if (ols.count() >= minTrain && ols.canPredict()) {
                steps.add(new Step(o, ols.predict(o.deltaUsd()),        // 1. đoán bằng quá khứ
                        ols.slope(), ols.intercept(), (int) ols.count()));
            }
            ols.add(o.deltaUsd(), o.deltaVnd());                        // 2. rồi mới học kỳ này
        }
        return steps;
    }

    public static Result run(List<Observation> observations, int minTrain) {
        return summarise(walkForward(observations, minTrain));
    }

    static Result summarise(List<Step> steps) {
        if (steps.isEmpty()) {
            return new Result(0, 0, 0, 0);
        }
        double sumNaive = 0;
        double sumModel = 0;
        int hits = 0;
        for (Step s : steps) {
            double actual = s.observation().deltaVnd();
            sumNaive += Math.abs(actual);
            sumModel += Math.abs(actual - s.predictedDeltaVnd());
            if (Math.signum(s.predictedDeltaVnd()) == Math.signum(actual)) {
                hits++;
            }
        }
        int n = steps.size();
        return new Result(n, sumNaive / n, sumModel / n, (double) hits / n);
    }
}
