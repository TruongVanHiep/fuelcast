package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.api.CurrentCycleDto;
import fitiuh.com.fuelcast_core.entity.AdjustmentCycle;
import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.WorldPrice;
import fitiuh.com.fuelcast_core.forecast.BacktestService.Period;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter.LiveRow;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Observation;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import fitiuh.com.fuelcast_core.repository.WorldPriceRepository;
import fitiuh.com.fuelcast_core.service.AdjustmentCycleService;
import fitiuh.com.fuelcast_core.service.CycleQueryService;
import fitiuh.com.fuelcast_core.service.NoCycleDataException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Dự báo LIVE: đoán mức đổi giá của kỳ SẮP TỚI chỉ bằng thông tin có trước giờ
 * công bố, rồi chấm điểm khi bản tin thật xuất hiện.
 *
 * Mô hình dùng Δ giá thế giới trung bình của kỳ VỪA RỒI để đoán Δ giá bán lẻ của kỳ
 * tới (trễ một kỳ). Đây là thứ duy nhất có trước giờ công bố khi chưa có số liệu
 * theo ngày. Backtest walk-forward cho thấy nó CHƯA hơn việc đoán "giá không đổi"
 * (sai số trong vòng 2%, đúng chiều khoảng 50%). Hạ tầng này có giá trị ở chỗ nó
 * bắt đầu thu bằng chứng ngoài mẫu thật, và để thay mô hình khi có dữ liệu tốt hơn.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LiveForecastService {

    private static final Logger log = LoggerFactory.getLogger(LiveForecastService.class);

    /** Khác "passthrough-ols-v2": mô hình đó dùng giá thế giới của chính kỳ được đoán. */
    public static final String MODEL_VERSION = "lagged-world-ols-v1";

    static final int MIN_TRAIN_PAIRS = 20;

    private static final OffsetDateTime EARLIEST = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATEST = OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    FuelProductRepository products;
    RetailPriceRepository prices;
    WorldPriceRepository worldPrices;
    CycleQueryService cycleQuery;
    AdjustmentCycleService cycles;
    ForecastWriter writer;
    Clock clock;
    /** Có khi bật fuelcast.forecast.python.enabled: mô hình Python dự báo song song. */
    Optional<PythonLiveForecast> pythonLive;

    /**
     * graded: dự báo cũ vừa được chấm; created: dự báo mới; existing: đã có sẵn; skipped:
     * không dự báo được. Đếm theo từng (mặt hàng, mô hình): khi mô hình Python bật thì mỗi
     * mặt hàng có hai dòng.
     */
    public record RefreshResult(int graded, int created, int existing, int skipped) { }

    /**
     * Chấm các dự báo đã có đáp án, rồi đảm bảo kỳ sắp tới có dự báo. Idempotent,
     * gọi bao nhiêu lần cũng được: bộ quét gọi nó sau mỗi lượt.
     */
    @Transactional
    public RefreshResult refresh() {
        int graded = writer.gradePendingLive();

        CurrentCycleDto cycle;
        try {
            cycle = cycleQuery.currentCycle();
        } catch (NoCycleDataException e) {
            return new RefreshResult(graded, 0, 0, 0);       // chưa có bản tin nào để dự báo tiếp
        }

        AdjustmentCycle open = cycles.openCycleFor(cycle.expectedNextAt());
        OffsetDateTime now = OffsetDateTime.now(clock);
        int horizonDays = (int) Math.max(0, Duration.between(now, cycle.expectedNextAt()).toDays());

        int[] tally = new int[3];       // 0: created, 1: existing, 2: skipped
        for (FuelProduct product : products.findAll()) {
            Optional<List<Period>> periods = periodsFor(product);
            if (periods.isEmpty()) {
                tally[2]++;             // mặt hàng không có mã giá thế giới tương ứng
                continue;
            }
            record(predict(periods.get(), cycle.lastEffectiveAt())
                    .map(p -> toRow(p, product, open, now, horizonDays)), tally);
            // Mô hình Python chạy SAU mô hình Java và không bao giờ làm hỏng nó: lỗi của dịch
            // vụ Python được bắt ngay trong PythonLiveForecast và chỉ làm mất dòng của nó.
            pythonLive.ifPresent(py -> record(
                    py.buildRow(periods.get(), product, cycle, open, now, horizonDays), tally));
        }
        return new RefreshResult(graded, tally[0], tally[1], tally[2]);
    }

    private void record(Optional<LiveRow> row, int[] tally) {
        if (row.isEmpty()) {
            tally[2]++;
        } else if (writer.insertLive(row.get())) {
            tally[0]++;
        } else {
            tally[1]++;
        }
    }

    /** Chuỗi kỳ đã ghép của mặt hàng; rỗng (Optional) nếu mặt hàng không có mã giá thế giới. */
    private Optional<List<Period>> periodsFor(FuelProduct product) {
        Optional<String> symbol = WorldSymbols.forProduct(product.getCode());
        if (symbol.isEmpty()) {
            return Optional.empty();
        }
        List<RetailPrice> retail = prices.findHistory(product.getId(), EARLIEST, LATEST);
        List<WorldPrice> world = worldPrices.findByIdSymbolOrderByIdObservedAtAsc(symbol.get());
        return Optional.of(BacktestService.alignPeriods(retail, world));
    }

    /** Kết quả của việc học và đoán, kèm những gì đã dùng. */
    record Prediction(double predictedDeltaVnd, double slope, double intercept,
                      double deltaUsdPrev, int trainPairs, BigDecimal lastPriceVnd,
                      OffsetDateTime lastAnnouncementAt) { }

    /**
     * Học từ mọi cặp (Δ thế giới kỳ trước → Δ bán lẻ kỳ này), rồi đoán dựa trên Δ
     * thế giới của kỳ gần nhất.
     *
     * Trả rỗng khi mặt hàng không nằm trên bản công bố mới nhất (dầu hỏa sau
     * 27/3/2026): đoán "kỳ sau" cho một thứ không còn được công bố là vô nghĩa.
     */
    static Optional<Prediction> predict(List<Period> periods, OffsetDateTime latestAnnouncementAt) {
        if (periods.isEmpty()) {
            return Optional.empty();
        }
        Period last = periods.get(periods.size() - 1);
        if (!last.observation().at().toInstant().equals(latestAnnouncementAt.toInstant())) {
            return Optional.empty();
        }

        OnlineOls ols = new OnlineOls();
        int pairs = 0;
        for (int i = 1; i < periods.size(); i++) {
            Observation before = periods.get(i - 1).observation();
            Observation current = periods.get(i).observation();
            Duration gap = Duration.between(before.at(), current.at());
            // Hai kỳ liền kề thật: cùng khoảng 5-9 ngày như khi ghép dữ liệu.
            if (gap.compareTo(BacktestService.MIN_GAP) >= 0 && gap.compareTo(BacktestService.MAX_GAP) <= 0) {
                ols.add(before.deltaUsd(), current.deltaVnd());
                pairs++;
            }
        }
        if (pairs < MIN_TRAIN_PAIRS || !ols.canPredict()) {
            return Optional.empty();
        }

        double deltaUsdPrev = last.observation().deltaUsd();
        return Optional.of(new Prediction(
                ols.predict(deltaUsdPrev), ols.slope(), ols.intercept(),
                deltaUsdPrev, pairs, last.priceVnd(), last.observation().at()));
    }

    static LiveRow toRow(Prediction p, FuelProduct product, AdjustmentCycle open,
                         OffsetDateTime now, int horizonDays) {
        BigDecimal delta = BigDecimal.valueOf(p.predictedDeltaVnd()).setScale(2, RoundingMode.HALF_UP);
        return new LiveRow(
                open.getId(), product.getId(), now,
                MODEL_VERSION, horizonDays,
                p.lastPriceVnd().add(delta), delta,
                ForecastReplayService.directionOf(p.predictedDeltaVnd()),
                featuresJson(p), explanationJson(p));
    }

    static String featuresJson(Prediction p) {
        return String.format(Locale.ROOT,
                "{\"deltaUsdPrevPeriod\":%s,\"slope\":%s,\"intercept\":%s,\"trainPairs\":%d,"
              + "\"lastAnnouncementAt\":\"%s\"}",
                p.deltaUsdPrev(), p.slope(), p.intercept(), p.trainPairs(), p.lastAnnouncementAt());
    }

    static String explanationJson(Prediction p) {
        return String.format(Locale.ROOT,
                "{\"lagged_world_effect\":%s,\"intercept\":%s,\"net_delta\":%s}",
                p.slope() * p.deltaUsdPrev(), p.intercept(), p.predictedDeltaVnd());
    }
}
