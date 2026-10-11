package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.api.CurrentPriceDto;
import fitiuh.com.fuelcast_core.api.PriceHistoryDto;
import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.RetailPriceId;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceQueryServiceTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime OCT_08 = OffsetDateTime.of(2026, 10, 8, 15, 0, 0, 0, VN);
    private static final OffsetDateTime MAR_27 = OffsetDateTime.of(2026, 3, 27, 0, 0, 0, 0, VN);

    private final RetailPriceRepository prices = mock(RetailPriceRepository.class);
    private final FuelProductRepository products = mock(FuelProductRepository.class);
    /** "Hôm nay" cố định: 11/10/2026 theo giờ Việt Nam. */
    private static final Clock TODAY = Clock.fixed(
            OffsetDateTime.of(2026, 10, 11, 9, 0, 0, 0, VN).toInstant(), VN);

    private final PriceQueryService service = new PriceQueryService(prices, products, TODAY);

    @Test
    void mapsPriceAndProductIntoDto() {
        when(products.findAll()).thenReturn(List.of(product(2, "RON95", "Xăng RON95-III", "lít", 2)));
        when(prices.findLatestPerProduct()).thenReturn(List.of(price(2, "28254.00", "1065.00", OCT_08)));

        List<CurrentPriceDto> result = service.currentPrices();

        assertThat(result).containsExactly(new CurrentPriceDto(
                "RON95", "Xăng RON95-III", "lít", (short) 1, 28254L, 1065L, OCT_08));
    }

    /** Thứ tự hiển thị lấy từ fuel_product.display_order, không phải thứ tự id hay thứ tự DB trả. */
    @Test
    void ordersByProductDisplayOrder() {
        when(products.findAll()).thenReturn(List.of(
                product(1, "E5RON92", "Xăng E5RON92", "lít", 3),
                product(2, "RON95", "Xăng RON95", "lít", 1),
                product(3, "DO_005S", "Dầu điêzen", "lít", 2)));
        when(prices.findLatestPerProduct()).thenReturn(List.of(
                price(1, "27703.00", "1134.00", OCT_08),
                price(2, "28254.00", "1065.00", OCT_08),
                price(3, "29128.00", "-589.00", OCT_08)));

        assertThat(service.currentPrices()).extracting(CurrentPriceDto::product)
                .containsExactly("RON95", "DO_005S", "E5RON92");
    }

    @Test
    void keepsNegativeDeltaAndTreatsMissingDeltaAsNull() {
        when(products.findAll()).thenReturn(List.of(
                product(1, "DO_005S", "Dầu điêzen", "lít", 1),
                product(2, "RON95", "Xăng RON95", "lít", 2)));
        when(prices.findLatestPerProduct()).thenReturn(List.of(
                price(1, "29128.00", "-589.00", OCT_08),
                price(2, "28254.00", null, OCT_08)));

        List<CurrentPriceDto> result = service.currentPrices();

        assertThat(result.get(0).deltaVnd()).isEqualTo(-589L);
        assertThat(result.get(1).deltaVnd()).isNull();
    }

    /**
     * Dầu hỏa không còn được công bố từ 27/3/2026. Service không tự lọc: nó vẫn
     * có mặt cùng effectiveAt cũ để client biết giá đó đã cũ.
     */
    @Test
    void keepsDiscontinuedProductWithItsOwnOldEffectiveDate() {
        when(products.findAll()).thenReturn(List.of(
                product(1, "RON95", "Xăng RON95", "lít", 1),
                product(2, "KEROSENE", "Dầu hỏa", "lít", 2)));
        when(prices.findLatestPerProduct()).thenReturn(List.of(
                price(1, "28254.00", "1065.00", OCT_08),
                price(2, "35384.00", "-971.00", MAR_27)));

        List<CurrentPriceDto> result = service.currentPrices();

        assertThat(result).hasSize(2);
        assertThat(result.get(1).product()).isEqualTo("KEROSENE");
        assertThat(result.get(1).effectiveAt()).isEqualTo(MAR_27);
    }

    /** Giá có phần lẻ phải lỗi to, không được bị cắt lặng lẽ. */
    @Test
    void failsLoudlyWhenPriceHasFractionalPart() {
        when(products.findAll()).thenReturn(List.of(product(1, "RON95", "Xăng RON95", "lít", 1)));
        when(prices.findLatestPerProduct()).thenReturn(List.of(price(1, "28254.50", "10.00", OCT_08)));

        assertThatThrownBy(() -> service.currentPrices()).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void failsWhenPriceReferencesUnknownProduct() {
        when(products.findAll()).thenReturn(List.of(product(1, "RON95", "Xăng RON95", "lít", 1)));
        when(prices.findLatestPerProduct()).thenReturn(List.of(price(99, "1000.00", null, OCT_08)));

        assertThatThrownBy(() -> service.currentPrices())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("99");
    }

    // ───────────────────────────── history ─────────────────────────────

    /**
     * Biên ngày: "đến hết 31/12" phải là "< 00:00 ngày 1/1 giờ VN". Nếu dùng
     * "<= 00:00 ngày 31/12" thì kỳ công bố 15:00 ngày 31/12 bị rơi mất.
     */
    @Test
    void historyIncludesTheWholeLastDayInVietnamTime() {
        when(products.findByCode("RON95")).thenReturn(Optional.of(product(2, "RON95", "Xăng RON95", "lít", 2)));
        when(prices.findHistory(eq((short) 2), any(), any())).thenReturn(List.of());

        service.history("RON95", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(prices).findHistory(eq((short) 2), from.capture(), to.capture());

        assertThat(from.getValue()).isEqualTo(OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, VN));
        assertThat(to.getValue()).isEqualTo(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, VN));
    }

    @Test
    void historyMapsPointsInTheOrderTheRepositoryGives() {
        when(products.findByCode("RON95")).thenReturn(Optional.of(product(2, "RON95", "Xăng RON95", "lít", 2)));
        when(prices.findHistory(eq((short) 2), any(), any())).thenReturn(List.of(
                price(2, "21935.00", "19.00", OffsetDateTime.of(2024, 1, 11, 15, 0, 0, 0, VN)),
                price(2, "22000.00", null, OffsetDateTime.of(2024, 1, 18, 15, 0, 0, 0, VN))));

        PriceHistoryDto result = service.history("RON95", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result.product()).isEqualTo("RON95");
        assertThat(result.name()).isEqualTo("Xăng RON95");
        assertThat(result.points()).hasSize(2);
        assertThat(result.points().get(0).priceVnd()).isEqualTo(21935L);
        assertThat(result.points().get(0).deltaVnd()).isEqualTo(19L);
        assertThat(result.points().get(1).deltaVnd()).isNull();
    }

    /** Bỏ trống to thì là hôm nay (theo đồng hồ VN), bỏ trống from thì lùi một năm. */
    @Test
    void historyDefaultsToTheLastYearEndingToday() {
        when(products.findByCode("RON95")).thenReturn(Optional.of(product(2, "RON95", "Xăng RON95", "lít", 2)));
        when(prices.findHistory(eq((short) 2), any(), any())).thenReturn(List.of());

        PriceHistoryDto result = service.history("RON95", null, null);

        assertThat(result.to()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(result.from()).isEqualTo(LocalDate.of(2025, 10, 11));
    }

    @Test
    void historyAcceptsLowerCaseProductCode() {
        when(products.findByCode("RON95")).thenReturn(Optional.of(product(2, "RON95", "Xăng RON95", "lít", 2)));
        when(prices.findHistory(eq((short) 2), any(), any())).thenReturn(List.of());

        assertThat(service.history(" ron95 ", null, null).product()).isEqualTo("RON95");
    }

    @Test
    void historyOfUnknownProductIsNotFound() {
        when(products.findByCode("XYZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history("xyz", null, null))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("xyz");
    }

    @Test
    void historyRejectsStartAfterEnd() {
        when(products.findByCode("RON95")).thenReturn(Optional.of(product(2, "RON95", "Xăng RON95", "lít", 2)));

        assertThatThrownBy(() -> service.history("RON95", LocalDate.of(2025, 6, 1), LocalDate.of(2025, 1, 1)))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    private static FuelProduct product(int id, String code, String name, String unit, int order) {
        return FuelProduct.builder()
                .id((short) id).code(code).nameVi(name).unit(unit).displayOrder((short) order).build();
    }

    private static RetailPrice price(int productId, String priceVnd, String deltaVnd, OffsetDateTime at) {
        return new RetailPrice(
                new RetailPriceId(at, (short) productId, (short) 1, (short) 1),
                new BigDecimal(priceVnd),
                deltaVnd == null ? null : new BigDecimal(deltaVnd),
                10L);
    }
}
