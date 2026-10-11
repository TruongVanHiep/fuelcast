package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.service.ScorecardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccuracyController.class)
class AccuracyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ScorecardService scorecardService;

    private static ScorecardDto.Row row(String product, boolean beats) {
        return new ScorecardDto.Row(product, "Tên " + product, "REPLAY", "passthrough-ols-v1", 0,
                99, 125.4, 495.2, beats ? 74.7 : -1.2, 99.0, beats,
                LocalDate.of(2024, 5, 30), LocalDate.of(2026, 10, 8));
    }

    @Test
    void returnsRowsTogetherWithTheNotice() throws Exception {
        when(scorecardService.scorecard()).thenReturn(new ScorecardDto(
                "Dòng REPLAY chưa phải dự báo thật.", List.of(row("RON95", true), row("FO_180", false))));

        mockMvc.perform(get("/api/v1/accuracy/scorecard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").value("Dòng REPLAY chưa phải dự báo thật."))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].product").value("RON95"))
                .andExpect(jsonPath("$.rows[0].kind").value("REPLAY"))
                .andExpect(jsonPath("$.rows[0].horizonDays").value(0))
                .andExpect(jsonPath("$.rows[0].maeModelVnd").value(125.4))
                .andExpect(jsonPath("$.rows[0].beatsNaive").value(true))
                .andExpect(jsonPath("$.rows[1].beatsNaive").value(false))
                .andExpect(jsonPath("$.rows[1].improvementPct").value(-1.2));
    }

    /** Chưa có dòng nào vẫn phải trả lời đầy đủ, kèm cảnh báo, chứ không phải lỗi. */
    @Test
    void noRowsIsStillAValidAnswerWithTheNotice() throws Exception {
        when(scorecardService.scorecard()).thenReturn(new ScorecardDto("cảnh báo", List.of()));

        mockMvc.perform(get("/api/v1/accuracy/scorecard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(0))
                .andExpect(jsonPath("$.notice").value("cảnh báo"));
    }
}
