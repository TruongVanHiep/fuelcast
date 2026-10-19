package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.api.CurrentCycleDto;
import fitiuh.com.fuelcast_core.entity.AdjustmentCycle;
import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.forecast.BacktestService.Period;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter.LiveRow;
import fitiuh.com.fuelcast_core.forecast.PythonForecastClient.Prediction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dự báo LIVE của mô hình Python, chạy song song với mô hình Java và ghi vào cùng bảng
 * forecast bằng một model_version riêng, để bảng điểm đặt hai mô hình cạnh đường cơ sở
 * "không đổi".
 *
 * Dùng ĐÚNG chuỗi kỳ mà mô hình Java dùng (BacktestService.alignPeriods) nên hai mô hình
 * học trên cùng tập dữ liệu. Dịch vụ Python hỏng hay chậm thì chỉ mất dòng của nó: không
 * bao giờ làm hỏng dự báo của mô hình Java đang chạy.
 */
@Component
@ConditionalOnProperty(name = "fuelcast.forecast.python.enabled", havingValue = "true")
public class PythonLiveForecast {

    private static final Logger log = LoggerFactory.getLogger(PythonLiveForecast.class);

    private final PythonForecastClient client;
    private final JsonMapper json;

    public PythonLiveForecast(PythonForecastClient client, JsonMapper json) {
        this.client = client;
        this.json = json;
    }

    /**
     * @param periods chuỗi kỳ đã ghép của mặt hàng, tăng dần theo thời gian
     * @return rỗng khi không dự báo được: mặt hàng không còn trên bản công bố mới nhất,
     *         dữ liệu chưa đủ, hoặc dịch vụ Python không trả lời
     */
    Optional<LiveRow> buildRow(List<Period> periods, FuelProduct product, CurrentCycleDto cycle,
                               AdjustmentCycle open, OffsetDateTime now, int horizonDays) {
        if (periods.isEmpty()) {
            return Optional.empty();
        }
        Period last = periods.get(periods.size() - 1);
        // Như mô hình Java: đoán "kỳ sau" cho thứ không còn được công bố là vô nghĩa.
        if (!last.observation().at().toInstant().equals(cycle.lastEffectiveAt().toInstant())) {
            return Optional.empty();
        }

        Optional<Prediction> prediction;
        try {
            prediction = client.predict(
                    periods.stream().map(Period::observation).toList(), cycle.expectedNextAt());
        } catch (RestClientException e) {
            log.warn("Dịch vụ dự báo Python không trả lời cho {}: {}", product.getCode(), e.toString());
            return Optional.empty();
        }
        return prediction.map(p -> toRow(p, last, product, open, now, horizonDays));
    }

    LiveRow toRow(Prediction p, Period last, FuelProduct product, AdjustmentCycle open,
                  OffsetDateTime now, int horizonDays) {
        BigDecimal delta = BigDecimal.valueOf(p.predictedDeltaVnd()).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> features = new LinkedHashMap<>();
        if (p.features() != null) {
            features.putAll(p.features());
        }
        features.put("trainRows", p.trainRows());
        features.put("usedResidual", p.usedResidual());
        features.put("lastAnnouncementAt", last.observation().at().toString());

        return new LiveRow(
                open.getId(), product.getId(), now,
                p.modelVersion(), horizonDays,
                last.priceVnd().add(delta), delta,
                ForecastReplayService.directionOf(p.predictedDeltaVnd()),
                json.writeValueAsString(features),
                json.writeValueAsString(p.explanation() == null ? Map.of() : p.explanation()));
    }
}
