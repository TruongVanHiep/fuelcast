package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.service.CycleQueryService;
import fitiuh.com.fuelcast_core.service.NoCycleDataException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CycleController.class)
class CycleControllerTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CycleQueryService cycleQueryService;

    @Test
    void currentReturnsLastAnnouncementAndCountdownInVietnamTime() throws Exception {
        when(cycleQueryService.currentCycle()).thenReturn(new CurrentCycleDto(
                OffsetDateTime.of(2026, 10, 8, 8, 0, 0, 0, ZoneOffset.UTC),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8),
                OffsetDateTime.of(2026, 10, 15, 15, 0, 0, 0, VN),
                345_600L, false));

        mockMvc.perform(get("/api/v1/cycles/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastEffectiveAt").value("2026-10-08T15:00:00+07:00"))
                .andExpect(jsonPath("$.lastCycleStart").value("2026-10-01"))
                .andExpect(jsonPath("$.lastCycleEnd").value("2026-10-08"))
                .andExpect(jsonPath("$.expectedNextAt").value("2026-10-15T15:00:00+07:00"))
                .andExpect(jsonPath("$.secondsUntilNext").value(345600))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void currentIsProblemJson404WhenThereIsNoData() throws Exception {
        when(cycleQueryService.currentCycle()).thenThrow(new NoCycleDataException());

        mockMvc.perform(get("/api/v1/cycles/current"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
