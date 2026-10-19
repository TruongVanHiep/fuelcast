package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.api.CurrentCycleDto;
import fitiuh.com.fuelcast_core.entity.AdjustmentCycle;
import fitiuh.com.fuelcast_core.entity.CycleStatus;
import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.forecast.BacktestService.Period;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter.LiveRow;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest.Observation;
import fitiuh.com.fuelcast_core.forecast.PythonForecastClient.Prediction;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PythonLiveForecastTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime LAST = OffsetDateTime.of(2026, 10, 15, 15, 0, 0, 0, VN);
    private static final OffsetDateTime NEXT = LAST.plusWeeks(1);
    private static final OffsetDateTime NOW = LAST.plusDays(4);

    private final PythonForecastClient client = mock(PythonForecastClient.class);
    private final PythonLiveForecast live = new PythonLiveForecast(client, JsonMapper.builder().build());

    private final FuelProduct product = FuelProduct.builder().id((short) 2).code("RON95")
            .nameVi("Xăng RON95").unit("lít").displayOrder((short) 2).build();
    private final AdjustmentCycle open = AdjustmentCycle.builder().id(900L)
            .cycleStart(LocalDate.of(2026, 10, 15)).cycleEnd(LocalDate.of(2026, 10, 22))
            .status(CycleStatus.OPEN).build();
    private final CurrentCycleDto cycle = new CurrentCycleDto(
            LAST, LAST.toLocalDate().minusDays(7), LAST.toLocalDate(), NEXT, 0, false);

    private static Period period(OffsetDateTime at, String price) {
        BigDecimal p = new BigDecimal(price);
        return new Period(new Observation(at, 100, 1.0), 1L, p, p.subtract(new BigDecimal(100)));
    }

    private static Prediction prediction() {
        Map<String, Double> features = new HashMap<>();
        features.put("usd_in", 0.47);
        features.put("usd_in_prev", null);          // đặc trưng thiếu: JSON null, không được làm hỏng
        return new Prediction("rule-lgbm-residual-v1", 33.5, 22.5, 11.0, true, 79, features,
                Map.of("rule_effect", 19.0, "net_delta", 33.5));
    }

    @Test
    void turnsThePredictionIntoAForecastRowForTheOpenCycle() {
        List<Period> periods = List.of(period(LAST.minusWeeks(1), "27000"), period(LAST, "27100"));
        when(client.predict(any(), any())).thenReturn(Optional.of(prediction()));

        Optional<LiveRow> row = live.buildRow(periods, product, cycle, open, NOW, 3);

        assertThat(row).isPresent();
        LiveRow r = row.get();
        assertThat(r.cycleId()).isEqualTo(900L);
        assertThat(r.productId()).isEqualTo((short) 2);
        assertThat(r.createdAt()).isEqualTo(NOW);
        assertThat(r.horizonDays()).isEqualTo(3);
        assertThat(r.modelVersion()).isEqualTo("rule-lgbm-residual-v1");
        assertThat(r.predictedDelta()).isEqualByComparingTo("33.50");
        assertThat(r.predictedVnd()).isEqualByComparingTo("27133.50");     // giá kỳ cuối + mức đổi
        assertThat(r.direction()).isEqualTo("UP");
        assertThat(r.featuresJson()).contains("\"trainRows\":79").contains("\"usedResidual\":true")
                .contains("\"usd_in\":0.47").contains("\"usd_in_prev\":null");
        assertThat(r.explanationJson()).contains("\"net_delta\":33.5").contains("rule_effect");
    }

    /** Hỏi dịch vụ về đúng kỳ sắp tới: gửi thời điểm công bố dự kiến để lấy tháng làm đặc trưng. */
    @Test
    void asksAboutTheExpectedNextAnnouncement() {
        List<Period> periods = List.of(period(LAST, "27100"));
        when(client.predict(any(), any())).thenReturn(Optional.of(prediction()));

        live.buildRow(periods, product, cycle, open, NOW, 3);

        verify(client).predict(List.of(periods.get(0).observation()), NEXT);
    }

    /** Mặt hàng không còn trên bản công bố mới nhất (như dầu hỏa): không đoán, không gọi dịch vụ. */
    @Test
    void skipsAProductNotOnTheLatestAnnouncementWithoutCallingTheService() {
        List<Period> stale = List.of(period(LAST.minusWeeks(5), "27000"));

        assertThat(live.buildRow(stale, product, cycle, open, NOW, 3)).isEmpty();
        verify(client, never()).predict(any(), any());
    }

    @Test
    void noPeriodsMeansNoForecast() {
        assertThat(live.buildRow(List.of(), product, cycle, open, NOW, 3)).isEmpty();
        verify(client, never()).predict(any(), any());
    }

    @Test
    void anUnprocessableAnswerMeansNoForecast() {
        List<Period> periods = List.of(period(LAST, "27100"));
        when(client.predict(any(), any())).thenReturn(Optional.empty());

        assertThat(live.buildRow(periods, product, cycle, open, NOW, 3)).isEmpty();
    }

    /** Dịch vụ Python tắt hay không với tới được: chỉ mất dòng của nó, không ném lỗi ra ngoài. */
    @Test
    void anUnreachableServiceDoesNotThrow() {
        List<Period> periods = List.of(period(LAST, "27100"));
        when(client.predict(any(), any())).thenThrow(new ResourceAccessException("connection refused"));

        assertThat(live.buildRow(periods, product, cycle, open, NOW, 3)).isEmpty();
    }
}
