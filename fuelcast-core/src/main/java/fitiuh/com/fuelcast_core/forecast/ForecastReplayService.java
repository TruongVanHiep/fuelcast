package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.WorldPrice;
import fitiuh.com.fuelcast_core.forecast.BacktestService.Period;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter.ReplayRow;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Observation;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Step;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import fitiuh.com.fuelcast_core.repository.WorldPriceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Phát lại walk-forward trên lịch sử và lưu từng lần đoán (kind = REPLAY) cùng
 * kết quả chấm điểm của nó.
 *
 * Đây KHÔNG phải dự báo đã được đưa ra trước sự kiện. Mô hình chỉ học từ các kỳ
 * trước kỳ đang đoán, nhưng người viết đã chọn mô hình sau khi nhìn dữ liệu, nên
 * kết quả lạc quan hơn dự báo thật. Vì vậy mọi dòng đều mang kind = REPLAY và bảng
 * điểm tách riêng.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ForecastReplayService {

    /**
     * Tăng số này khi đổi công thức HOẶC cách chọn dữ liệu học, để không trộn kết
     * quả hai phiên bản.
     *
     * v1: chỉ lọc kỳ liền kề theo khoảng cách ngày.
     * v2: thêm kiểm tra liên tục (mức đổi bản tin khớp chênh lệch giá), loại các kỳ
     *     bị thiếu bản tin giữa chừng.
     */
    public static final String MODEL_VERSION = "passthrough-ols-v2";

    /** Giá thế giới trung bình kỳ chỉ đầy đủ lúc công bố: dự báo ở horizon 0. */
    static final int HORIZON_DAYS = 0;

    private static final OffsetDateTime EARLIEST = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATEST = OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    /** Đoán thay đổi dưới ngưỡng này (đồng) thì coi là "không đổi". */
    private static final double FLAT_BELOW_VND = 1.0;

    FuelProductRepository products;
    RetailPriceRepository prices;
    WorldPriceRepository worldPrices;
    ForecastWriter writer;

    public record ReplayResult(int products, int inserted, int alreadyPresent) { }

    @Transactional
    public ReplayResult replay() {
        int productCount = 0;
        int inserted = 0;
        int alreadyPresent = 0;

        for (FuelProduct product : products.findAll()) {
            Optional<String> symbol = WorldSymbols.forProduct(product.getCode());
            if (symbol.isEmpty()) {
                continue;
            }
            List<RetailPrice> retail = prices.findHistory(product.getId(), EARLIEST, LATEST);
            List<WorldPrice> world = worldPrices.findByIdSymbolOrderByIdObservedAtAsc(symbol.get());

            List<Period> periods = BacktestService.alignPeriods(retail, world);
            // walkForward giữ nguyên tham chiếu Observation nên tra ngược về Period theo danh tính.
            Map<Observation, Period> periodOf = new IdentityHashMap<>();
            for (Period p : periods) {
                periodOf.put(p.observation(), p);
            }
            List<Observation> observations = periods.stream().map(Period::observation).toList();

            productCount++;
            for (Step step : PassThroughBacktest.walkForward(observations, PassThroughBacktest.DEFAULT_MIN_TRAIN)) {
                ReplayRow row = toRow(step, periodOf.get(step.observation()), product);
                if (writer.insertReplay(row)) {
                    inserted++;
                } else {
                    alreadyPresent++;
                }
            }
        }
        return new ReplayResult(productCount, inserted, alreadyPresent);
    }

    static ReplayRow toRow(Step step, Period period, FuelProduct product) {
        double actualDelta = step.observation().deltaVnd();
        double predictedDelta = step.predictedDeltaVnd();

        BigDecimal predictedDeltaVnd = money(predictedDelta);
        BigDecimal predictedVnd = period.previousPriceVnd().add(predictedDeltaVnd);
        BigDecimal actualVnd = period.priceVnd();
        BigDecimal absError = actualVnd.subtract(predictedVnd).abs();
        BigDecimal pctError = absError.multiply(BigDecimal.valueOf(100))
                .divide(actualVnd, 4, RoundingMode.HALF_UP);

        return new ReplayRow(
                period.cycleId(), product.getId(), step.observation().at(),
                MODEL_VERSION, HORIZON_DAYS,
                predictedVnd, predictedDeltaVnd, directionOf(predictedDelta),
                featuresJson(step), explanationJson(step),
                actualVnd, absError, pctError,
                Math.signum(predictedDelta) == Math.signum(actualDelta));
    }

    static String directionOf(double predictedDeltaVnd) {
        if (Math.abs(predictedDeltaVnd) < FLAT_BELOW_VND) {
            return "FLAT";
        }
        return predictedDeltaVnd > 0 ? "UP" : "DOWN";
    }

    /** Những gì mô hình đã dùng để đoán: đủ để dựng lại đúng con số đó. */
    static String featuresJson(Step s) {
        return String.format(Locale.ROOT,
                "{\"deltaUsd\":%s,\"slope\":%s,\"intercept\":%s,\"trainPeriods\":%d}",
                s.observation().deltaUsd(), s.slope(), s.intercept(), s.trainPeriods());
    }

    /** Phân rã đóng góp: bao nhiêu đồng đến từ giá thế giới, bao nhiêu là hằng số. */
    static String explanationJson(Step s) {
        return String.format(Locale.ROOT,
                "{\"world_effect\":%s,\"intercept\":%s,\"net_delta\":%s}",
                s.slope() * s.observation().deltaUsd(), s.intercept(), s.predictedDeltaVnd());
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
