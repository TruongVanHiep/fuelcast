package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.WorldPrice;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Observation;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import fitiuh.com.fuelcast_core.repository.WorldPriceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ghép chuỗi giá bán lẻ với chuỗi giá thế giới của từng mặt hàng rồi chạy
 * backtest. Chỉ ĐỌC dữ liệu; kết quả là bảng đo, chưa lưu dự báo nào.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BacktestService {

    static final Duration MIN_GAP = Duration.ofDays(5);
    static final Duration MAX_GAP = Duration.ofDays(9);

    private static final OffsetDateTime EARLIEST = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATEST = OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    RetailPriceRepository prices;
    WorldPriceRepository worldPrices;
    FuelProductRepository products;

    public record ProductBacktest(String product, String symbol, PassThroughBacktest.Result result) { }

    @Transactional(readOnly = true)
    public List<ProductBacktest> run() {
        List<ProductBacktest> out = new ArrayList<>();

        List<FuelProduct> ordered = products.findAll().stream()
                .sorted(Comparator.comparing(FuelProduct::getDisplayOrder))
                .toList();

        for (FuelProduct product : ordered) {
            Optional<String> symbol = WorldSymbols.forProduct(product.getCode());
            if (symbol.isEmpty()) {
                continue;               // mặt hàng chưa có mã giá thế giới tương ứng
            }
            List<RetailPrice> retail = prices.findHistory(product.getId(), EARLIEST, LATEST);
            List<WorldPrice> world = worldPrices.findByIdSymbolOrderByIdObservedAtAsc(symbol.get());

            List<Observation> observations = alignObservations(retail, world);
            out.add(new ProductBacktest(product.getCode(), symbol.get(),
                    PassThroughBacktest.run(observations, PassThroughBacktest.DEFAULT_MIN_TRAIN)));
        }
        return out;
    }

    /**
     * Ghép theo CÙNG thời điểm công bố, rồi chỉ giữ các cặp kỳ LIỀN KỀ (cách nhau
     * 5 đến 9 ngày). Kỳ nào bị đứt đoạn do thiếu bản tin thì Δ giá thế giới tính
     * qua khoảng trống sẽ không còn cùng nghĩa với Δ giá bán lẻ, nên bỏ.
     *
     * Δ giá bán lẻ lấy từ chính bản tin (so với kỳ công bố trước đó), còn Δ giá thế
     * giới tính giữa hai kỳ liền kề trong chuỗi đã ghép.
     */
    static List<Observation> alignObservations(List<RetailPrice> retail, List<WorldPrice> world) {
        // Khoá theo Instant: hai OffsetDateTime cùng thời điểm nhưng khác offset
        // (+07:00 và Z) thì equals() trả false.
        Map<Instant, WorldPrice> worldAt = new HashMap<>();
        for (WorldPrice w : world) {
            worldAt.put(w.getId().getObservedAt().toInstant(), w);
        }

        List<Observation> out = new ArrayList<>();
        OffsetDateTime previousAt = null;
        double previousUsd = 0;

        for (RetailPrice r : retail) {                      // đã tăng dần theo thời gian
            WorldPrice w = worldAt.get(r.getId().getObservedAt().toInstant());
            if (w == null) {
                continue;
            }
            OffsetDateTime at = r.getId().getObservedAt();
            double usd = w.getPriceUsd().doubleValue();

            if (previousAt != null && r.getDeltaVnd() != null) {
                Duration gap = Duration.between(previousAt, at);
                if (gap.compareTo(MIN_GAP) >= 0 && gap.compareTo(MAX_GAP) <= 0) {
                    out.add(new Observation(at, r.getDeltaVnd().doubleValue(), usd - previousUsd));
                }
            }
            previousAt = at;
            previousUsd = usd;
        }
        return out;
    }
}
