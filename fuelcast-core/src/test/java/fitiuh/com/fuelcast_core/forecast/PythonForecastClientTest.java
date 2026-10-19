package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Observation;
import fitiuh.com.fuelcast_core.forecast.PythonForecastClient.Backtest;
import fitiuh.com.fuelcast_core.forecast.PythonForecastClient.Prediction;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonForecastClientTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime T0 = OffsetDateTime.of(2024, 1, 4, 15, 0, 0, 0, VN);
    private static final OffsetDateTime NEXT = T0.plusWeeks(2);

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://forecast");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PythonForecastClient client = new PythonForecastClient(builder.build());

    private static List<Observation> observations() {
        return List.of(new Observation(T0, 120.0, 1.5), new Observation(T0.plusWeeks(1), -80.0, -0.5));
    }

    private static final String PREDICTION = """
            {"model_version":"rule-lgbm-residual-v1","mode":"lagged",
             "predicted_delta_vnd":33.5,"rule_delta_vnd":22.5,"residual_delta_vnd":11.0,
             "used_residual":true,"train_rows":79,
             "features":{"usd_in":0.47,"usd_in_prev":null,"vnd_prev":120.0,"vnd_prev2":null,"month":7.0},
             "explanation":{"rule_effect":19.0,"intercept":3.5,"residual_model":11.0,"net_delta":33.5},
             "extra_field_added_later":"must be ignored"}
            """;

    @Test
    void sendsSnakeCasePeriodsWithAnOffsetTimestampAndParsesThePrediction() {
        server.expect(requestTo("http://forecast/predict"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.periods.length()").value(2))
                .andExpect(jsonPath("$.periods[0].at").value("2024-01-04T15:00+07:00"))
                .andExpect(jsonPath("$.periods[0].delta_vnd").value(120.0))
                .andExpect(jsonPath("$.periods[0].delta_usd").value(1.5))
                .andExpect(jsonPath("$.target_at").value(NEXT.toString()))
                .andRespond(withSuccess(PREDICTION, MediaType.APPLICATION_JSON));

        Optional<Prediction> result = client.predict(observations(), NEXT);

        assertThat(result).isPresent();
        Prediction p = result.get();
        assertThat(p.modelVersion()).isEqualTo("rule-lgbm-residual-v1");
        assertThat(p.predictedDeltaVnd()).isEqualTo(33.5);
        assertThat(p.ruleDeltaVnd()).isEqualTo(22.5);
        assertThat(p.residualDeltaVnd()).isEqualTo(11.0);
        assertThat(p.usedResidual()).isTrue();
        assertThat(p.trainRows()).isEqualTo(79);
        assertThat(p.features()).containsEntry("usd_in", 0.47).containsEntry("usd_in_prev", null);
        assertThat(p.explanation()).containsEntry("net_delta", 33.5);
        server.verify();
    }

    /** 422 là "chưa đủ dữ liệu để học": một câu trả lời hợp lệ, không phải sự cố. */
    @Test
    void anUnprocessableAnswerMeansNoForecastNotAnError() {
        server.expect(requestTo("http://forecast/predict"))
                .andRespond(withStatus(HttpStatusCode.valueOf(422))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Không đủ dữ liệu\"}"));

        assertThat(client.predict(observations(), NEXT)).isEmpty();
    }

    /** Lỗi máy chủ KHÔNG được nuốt thành "không có dự báo": người gọi phải biết dịch vụ hỏng. */
    @Test
    void aServerErrorIsNotSwallowed() {
        server.expect(requestTo("http://forecast/predict")).andRespond(withServerError());

        assertThatThrownBy(() -> client.predict(observations(), NEXT))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void backtestAsksForTheLaggedModeAndParsesTheSteps() {
        server.expect(requestTo("http://forecast/backtest"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mode").value("lagged"))
                .andRespond(withSuccess("""
                        {"model_version":"rule-lgbm-residual-v1","mode":"lagged",
                         "summary":{"n":1},
                         "steps":[{"at":"2024-03-14T15:00:00+07:00","actual_delta_vnd":300.0,
                                   "naive_delta_vnd":0.0,"rule_delta_vnd":210.0,
                                   "predicted_delta_vnd":250.0,"train_rows":20,"used_residual":true}]}
                        """, MediaType.APPLICATION_JSON));

        Backtest result = client.backtestLagged(observations());

        assertThat(result.modelVersion()).isEqualTo("rule-lgbm-residual-v1");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).atTime().toInstant())
                .isEqualTo(OffsetDateTime.of(2024, 3, 14, 15, 0, 0, 0, VN).toInstant());
        assertThat(result.steps().get(0).predictedDeltaVnd()).isEqualTo(250.0);
        assertThat(result.steps().get(0).usedResidual()).isTrue();
    }
}
