package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.WorldPrice;
import fitiuh.com.fuelcast_core.forecast.BacktestService.Period;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter.ReplayRow;
import fitiuh.com.fuelcast_core.forecast.PythonForecastClient.Backtest;
import fitiuh.com.fuelcast_core.forecast.PythonForecastClient.BacktestStep;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import fitiuh.com.fuelcast_core.repository.WorldPriceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Phát lại walk-forward của mô hình Python trên lịch sử và lưu từng lần đoán (kind =
 * REPLAY) cùng kết quả chấm điểm, để bảng điểm có sẵn một dòng so sánh với mô hình Java
 * và "không đổi" mà không phải đợi nhiều kỳ LIVE.
 *
 * Khác với ForecastReplayService (Java, horizon 0 dùng giá thế giới CÙNG kỳ): ở đây chạy
 * chế độ "lagged", tức dùng giá thế giới của kỳ TRƯỚC, đúng thông tin mà dự báo LIVE có.
 * Nên đây là phép đo gần dự báo thật hơn. Vẫn là REPLAY (lạc quan hơn LIVE) vì mô hình được
 * chọn sau khi nhìn dữ liệu.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@ConditionalOnProperty(name = "fuelcast.forecast.python.enabled", havingValue = "true")
public class PythonReplayService {

    /**
     * Giá thế giới của kỳ trước chỉ đầy đủ vào lúc công bố kỳ trước, tức khoảng một kỳ
     * (7 ngày) trước lần công bố đang đoán. Vì vậy ghi horizon 7, không phải 0 (0 nghĩa là
     * dùng giá thế giới cùng kỳ, thứ chỉ có khi đã biết kết quả).
     */
    static final int HORIZON_DAYS = 7;

    private static final OffsetDateTime EARLIEST = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATEST = OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    FuelProductRepository products;
    RetailPriceRepository prices;
    WorldPriceRepository worldPrices;
    PythonForecastClient client;
    ForecastWriter writer;

    public record ReplayResult(int products, int inserted, int alreadyPresent, String modelVersion) { }

    @Transactional
    public ReplayResult replay() {
        int productCount = 0;
        int inserted = 0;
        int alreadyPresent = 0;
        String modelVersion = null;

        for (FuelProduct product : products.findAll()) {
            Optional<String> symbol = WorldSymbols.forProduct(product.getCode());
            if (symbol.isEmpty()) {
                continue;
            }
            List<RetailPrice> retail = prices.findHistory(product.getId(), EARLIEST, LATEST);
            List<WorldPrice> world = worldPrices.findByIdSymbolOrderByIdObservedAtAsc(symbol.get());
            List<Period> periods = BacktestService.alignPeriods(retail, world);
            if (periods.isEmpty()) {
                continue;
            }

            Backtest backtest = client.backtestLagged(periods.stream().map(Period::observation).toList());
            modelVersion = backtest.modelVersion();

            Map<Instant, Period> periodAt = new HashMap<>();
            for (Period p : periods) {
                periodAt.put(p.observation().at().toInstant(), p);
            }

            productCount++;
            for (BacktestStep step : backtest.steps()) {
                Period period = periodAt.get(step.atTime().toInstant());
                if (period == null) {
                    continue;   // dịch vụ trả một thời điểm ta không gửi: bỏ, không đoán
                }
                if (writer.insertReplay(toRow(step, period, product, backtest.modelVersion()))) {
                    inserted++;
                } else {
                    alreadyPresent++;
                }
            }
        }
        return new ReplayResult(productCount, inserted, alreadyPresent, modelVersion);
    }

    static ReplayRow toRow(BacktestStep step, Period period, FuelProduct product, String modelVersion) {
        double predictedDelta = step.predictedDeltaVnd();
        double actualDelta = period.observation().deltaVnd();

        BigDecimal predictedDeltaVnd = BigDecimal.valueOf(predictedDelta).setScale(2, RoundingMode.HALF_UP);
        BigDecimal predictedVnd = period.previousPriceVnd().add(predictedDeltaVnd);
        BigDecimal actualVnd = period.priceVnd();
        BigDecimal absError = actualVnd.subtract(predictedVnd).abs();
        BigDecimal pctError = absError.multiply(BigDecimal.valueOf(100))
                .divide(actualVnd, 4, RoundingMode.HALF_UP);

        return new ReplayRow(
                period.cycleId(), product.getId(), period.observation().at(),
                modelVersion, HORIZON_DAYS,
                predictedVnd, predictedDeltaVnd, ForecastReplayService.directionOf(predictedDelta),
                featuresJson(step), explanationJson(step),
                actualVnd, absError, pctError,
                Math.signum(predictedDelta) == Math.signum(actualDelta));
    }

    static String featuresJson(BacktestStep s) {
        return String.format(Locale.ROOT,
                "{\"mode\":\"lagged\",\"trainRows\":%d,\"usedResidual\":%b}",
                s.trainRows(), s.usedResidual());
    }

    /** Phân rã: bao nhiêu đồng đến từ lớp luật, bao nhiêu từ lớp phần dư. */
    static String explanationJson(BacktestStep s) {
        return String.format(Locale.ROOT,
                "{\"rule_effect\":%s,\"residual_model\":%s,\"net_delta\":%s}",
                s.ruleDeltaVnd(), s.predictedDeltaVnd() - s.ruleDeltaVnd(), s.predictedDeltaVnd());
    }
}
