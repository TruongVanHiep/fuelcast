package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.api.CurrentPriceDto;
import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.RetailPriceId;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceQueryServiceTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime OCT_08 = OffsetDateTime.of(2026, 10, 8, 15, 0, 0, 0, VN);
    private static final OffsetDateTime MAR_27 = OffsetDateTime.of(2026, 3, 27, 0, 0, 0, 0, VN);

    private final RetailPriceRepository prices = mock(RetailPriceRepository.class);
    private final FuelProductRepository products = mock(FuelProductRepository.class);
    private final PriceQueryService service = new PriceQueryService(prices, products);

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
