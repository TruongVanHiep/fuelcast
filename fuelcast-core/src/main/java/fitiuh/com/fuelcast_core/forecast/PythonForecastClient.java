package fitiuh.com.fuelcast_core.forecast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Observation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Máy khách gọi dịch vụ dự báo Python (fuelcast-forecast). Chỉ có khi
 * fuelcast.forecast.python.enabled=true; mặc định tắt để test và các lệnh chạy một lần
 * không đòi một dịch vụ thứ hai.
 *
 * Lớp này chỉ lo phần HTTP và đổi kiểu. Mọi quyết định nghiệp vụ (kỳ nào đoán, ghi
 * thế nào) nằm ở PythonLiveForecast và PythonReplayService.
 *
 * Hợp đồng ở fuelcast-forecast/app/schemas.py; tên trường là snake_case.
 */
@Component
@ConditionalOnProperty(name = "fuelcast.forecast.python.enabled", havingValue = "true")
public class PythonForecastClient {

    private final RestClient http;

    public PythonForecastClient(@Qualifier("pythonForecastRestClient") RestClient http) {
        this.http = http;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prediction(
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("predicted_delta_vnd") double predictedDeltaVnd,
            @JsonProperty("rule_delta_vnd") double ruleDeltaVnd,
            @JsonProperty("residual_delta_vnd") double residualDeltaVnd,
            @JsonProperty("used_residual") boolean usedResidual,
            @JsonProperty("train_rows") int trainRows,
            @JsonProperty("features") Map<String, Double> features,
            @JsonProperty("explanation") Map<String, Double> explanation) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BacktestStep(
            @JsonProperty("at") String at,
            @JsonProperty("actual_delta_vnd") double actualDeltaVnd,
            @JsonProperty("rule_delta_vnd") double ruleDeltaVnd,
            @JsonProperty("predicted_delta_vnd") double predictedDeltaVnd,
            @JsonProperty("train_rows") int trainRows,
            @JsonProperty("used_residual") boolean usedResidual) {

        public OffsetDateTime atTime() {
            return OffsetDateTime.parse(at);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Backtest(
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("steps") List<BacktestStep> steps) { }

    private record PeriodBody(
            @JsonProperty("at") String at,
            @JsonProperty("delta_vnd") double deltaVnd,
            @JsonProperty("delta_usd") double deltaUsd) { }

    private record PredictBody(
            @JsonProperty("periods") List<PeriodBody> periods,
            @JsonProperty("target_at") String targetAt) { }

    private record BacktestBody(
            @JsonProperty("periods") List<PeriodBody> periods,
            @JsonProperty("mode") String mode) { }

    /**
     * Đoán kỳ SẮP TỚI. Rỗng nếu dịch vụ trả 422, tức là dữ liệu chưa đủ để học (ít cặp liền
     * kề, hoặc giá thế giới không đổi). Lỗi khác (không kết nối được, 5xx) được ném ra để
     * người gọi quyết định, không bị nuốt thành "không có dự báo".
     */
    public Optional<Prediction> predict(List<Observation> observations, OffsetDateTime targetAt) {
        try {
            return Optional.ofNullable(http.post().uri("/predict")
                    .body(new PredictBody(periods(observations), targetAt.toString()))
                    .retrieve()
                    .body(Prediction.class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 422) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** Walk-forward ở chế độ "lagged": giá thế giới của kỳ trước, thứ có trước giờ công bố. */
    public Backtest backtestLagged(List<Observation> observations) {
        Backtest result = http.post().uri("/backtest")
                .body(new BacktestBody(periods(observations), "lagged"))
                .retrieve()
                .body(Backtest.class);
        if (result == null) {
            throw new IllegalStateException("Dịch vụ dự báo trả phản hồi rỗng cho /backtest");
        }
        return result;
    }

    private static List<PeriodBody> periods(List<Observation> observations) {
        return observations.stream()
                .map(o -> new PeriodBody(o.at().toString(), o.deltaVnd(), o.deltaUsd()))
                .toList();
    }
}
