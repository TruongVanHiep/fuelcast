package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.service.ForecastQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ForecastController.class)
class ForecastControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ForecastQueryService forecastQueryService;

    @Test
    void returnsTheNextForecastsWithTheNoticeAndVietnamTime() throws Exception {
        when(forecastQueryService.next()).thenReturn(new NextForecastsDto(
                "Dự báo LIVE chưa hơn đường cơ sở.",
                List.of(new NextForecastsDto.Item("RON95", "Xăng RON95-III", "lít",
                        28300, 46, "UP", 5, "lagged-world-ols-v1",
                        OffsetDateTime.of(2026, 10, 10, 9, 0, 0, 0, ZoneOffset.UTC), true))));

        mockMvc.perform(get("/api/v1/forecasts/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").value("Dự báo LIVE chưa hơn đường cơ sở."))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].product").value("RON95"))
                .andExpect(jsonPath("$.items[0].predictedVnd").value(28300))
                .andExpect(jsonPath("$.items[0].direction").value("UP"))
                .andExpect(jsonPath("$.items[0].horizonDays").value(5))
                .andExpect(jsonPath("$.items[0].primary").value(true))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-10-10T16:00:00+07:00"));
    }

    /** Chưa có dự báo nào vẫn là câu trả lời hợp lệ, và vẫn kèm lời cảnh báo. */
    @Test
    void noForecastIsAValidAnswerWithTheNotice() throws Exception {
        when(forecastQueryService.next()).thenReturn(new NextForecastsDto("cảnh báo", List.of()));

        mockMvc.perform(get("/api/v1/forecasts/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.notice").value("cảnh báo"));
    }
}
