package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.service.InvalidDateRangeException;
import fitiuh.com.fuelcast_core.service.PriceQueryService;
import fitiuh.com.fuelcast_core.service.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test lát cắt web: chỉ dựng tầng controller và cấu hình JSON thật của Boot,
 * không có DB. Service được thay bằng mock.
 */
@WebMvcTest(PriceController.class)
class PriceControllerTest {

    private static final OffsetDateTime OCT_08 =
            OffsetDateTime.of(2026, 10, 8, 15, 0, 0, 0, ZoneOffset.ofHours(7));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PriceQueryService priceQueryService;

    @Test
    void currentReturnsOneJsonObjectPerProduct() throws Exception {
        when(priceQueryService.currentPrices()).thenReturn(List.of(
                new CurrentPriceDto("RON95", "Xăng RON95-III", "lít", (short) 1, 28254L, 1065L, OCT_08),
                new CurrentPriceDto("DO_005S", "Dầu điêzen", "lít", (short) 1, 29128L, -589L, OCT_08)));

        mockMvc.perform(get("/api/v1/prices/current"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].product").value("RON95"))
                .andExpect(jsonPath("$[0].name").value("Xăng RON95-III"))
                .andExpect(jsonPath("$[0].unit").value("lít"))
                .andExpect(jsonPath("$[0].region").value(1))
                .andExpect(jsonPath("$[0].priceVnd").value(28254))
                .andExpect(jsonPath("$[0].deltaVnd").value(1065))
                .andExpect(jsonPath("$[1].deltaVnd").value(-589));
    }

    /** Giá là số nguyên đồng, không phải chuỗi và không có phần thập phân. */
    @Test
    void currentSerialisesPriceAsPlainJsonInteger() throws Exception {
        when(priceQueryService.currentPrices()).thenReturn(List.of(
                new CurrentPriceDto("RON95", "Xăng RON95-III", "lít", (short) 1, 28254L, 1065L, OCT_08)));

        mockMvc.perform(get("/api/v1/prices/current"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"priceVnd\":28254,")));
    }

    /**
     * Thời điểm phải ra theo giờ Việt Nam. Đầu vào cố ý ở UTC vì đó là cái DB
     * trả về thật: 15:00 giờ VN = 08:00Z. Nếu ra "2026-10-08T08:00:00Z" thì một
     * client cắt chuỗi lấy ngày sẽ thấy kỳ nửa đêm 27/3 thành ngày 26/3.
     */
    @Test
    void currentRendersEffectiveAtInVietnamOffset() throws Exception {
        OffsetDateTime utc = OffsetDateTime.of(2026, 10, 8, 8, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime midnight = OffsetDateTime.of(2026, 3, 26, 17, 0, 0, 0, ZoneOffset.UTC);
        when(priceQueryService.currentPrices()).thenReturn(List.of(
                new CurrentPriceDto("RON95", "Xăng RON95-III", "lít", (short) 1, 28254L, 1065L, utc),
                new CurrentPriceDto("KEROSENE", "Dầu hỏa", "lít", (short) 1, 35384L, -971L, midnight)));

        mockMvc.perform(get("/api/v1/prices/current"))
                .andExpect(jsonPath("$[0].effectiveAt").value("2026-10-08T15:00:00+07:00"))
                .andExpect(jsonPath("$[1].effectiveAt").value("2026-03-27T00:00:00+07:00"));
    }

    // ───────────────────────────── history ─────────────────────────────

    @Test
    void historyReturnsTheSeriesWithProductInfoOnce() throws Exception {
        when(priceQueryService.history("RON95", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .thenReturn(new PriceHistoryDto("RON95", "Xăng RON95-III", "lít", (short) 1,
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                        List.of(new PricePointDto(OCT_08, 28254L, 1065L),
                                new PricePointDto(OCT_08.plusDays(7), 28300L, 46L))));

        mockMvc.perform(get("/api/v1/prices/history")
                        .param("product", "RON95").param("from", "2025-01-01").param("to", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value("RON95"))
                .andExpect(jsonPath("$.from").value("2025-01-01"))
                .andExpect(jsonPath("$.to").value("2025-12-31"))
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].priceVnd").value(28254))
                .andExpect(jsonPath("$.points[1].deltaVnd").value(46));
    }

    /** from và to tùy chọn: bỏ trống thì controller truyền null cho service tự đặt mặc định. */
    @Test
    void historyPassesNullWhenDatesAreOmitted() throws Exception {
        when(priceQueryService.history("RON95", null, null))
                .thenReturn(new PriceHistoryDto("RON95", "Xăng RON95-III", "lít", (short) 1,
                        LocalDate.of(2025, 10, 11), LocalDate.of(2026, 10, 11), List.of()));

        mockMvc.perform(get("/api/v1/prices/history").param("product", "RON95"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    /** Lỗi theo RFC 7807, không phải trang lỗi hay chuỗi trơn. */
    @Test
    void historyOfUnknownProductIsProblemJson404() throws Exception {
        when(priceQueryService.history("XYZ", null, null)).thenThrow(new ProductNotFoundException("XYZ"));

        mockMvc.perform(get("/api/v1/prices/history").param("product", "XYZ"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Mặt hàng không tồn tại"));
    }

    @Test
    void historyWithStartAfterEndIsProblemJson400() throws Exception {
        when(priceQueryService.history("RON95", LocalDate.of(2025, 6, 1), LocalDate.of(2025, 1, 1)))
                .thenThrow(new InvalidDateRangeException(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 1, 1)));

        mockMvc.perform(get("/api/v1/prices/history")
                        .param("product", "RON95").param("from", "2025-06-01").param("to", "2025-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void historyWithMalformedDateIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/prices/history").param("product", "RON95").param("from", "01/02/2025"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void historyWithoutProductIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/prices/history"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void currentReturnsEmptyArrayWhenThereIsNoPriceYet() throws Exception {
        when(priceQueryService.currentPrices()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prices/current"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
