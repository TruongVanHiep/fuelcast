package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.entity.AdjustmentCycle;
import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.entity.PricePublisher;
import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.RetailPriceId;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.PricePublisherRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import fitiuh.com.fuelcast_core.service.parser.ParsedBulletin;
import fitiuh.com.fuelcast_core.service.parser.ParsedBulletin.ParsedPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceImportServiceTest {

    private static final OffsetDateTime T_2024_01_11 =
            OffsetDateTime.of(2024, 1, 11, 15, 0, 0, 0, ZoneOffset.ofHours(7));

    private final FuelProductRepository products = mock(FuelProductRepository.class);
    private final PricePublisherRepository publishers = mock(PricePublisherRepository.class);
    private final RetailPriceRepository prices = mock(RetailPriceRepository.class);
    private final AdjustmentCycleService cycleService = mock(AdjustmentCycleService.class);

    private final PriceImportService service = new PriceImportService(
            products, publishers, prices, cycleService, new ProductCodeResolver());

    @BeforeEach
    void setUp() {
        when(publishers.findByCode("MOIT")).thenReturn(Optional.of(
                PricePublisher.builder().id((short) 1).code("MOIT").build()));
        when(cycleService.findOrCreateAdjustmentCycle(any(OffsetDateTime.class)))
                .thenReturn(AdjustmentCycle.builder().id(10L).build());
        when(cycleService.count()).thenReturn(1L);
        when(prices.existsById(any(RetailPriceId.class))).thenReturn(false);
        when(products.findByCode("E5RON92")).thenReturn(Optional.of(product(1, "E5RON92")));
        when(products.findByCode("RON95")).thenReturn(Optional.of(product(2, "RON95")));
    }

    @Test
    void importBulletinWritesEachPriceWithNaturalKeyAndCycle() {
        ParsedBulletin bulletin = bulletin(
                new ParsedPrice("E5RON92", 21041, "lít", 35L),
                new ParsedPrice("RON95", 21935, "lít", -19L));

        PriceImportService.ImportResult result = service.importBulletin(bulletin);

        assertThat(result.inserted()).isEqualTo(2);
        assertThat(result.skippedExisting()).isZero();

        ArgumentCaptor<RetailPrice> saved = ArgumentCaptor.forClass(RetailPrice.class);
        verify(prices, org.mockito.Mockito.times(2)).save(saved.capture());

        RetailPrice first = saved.getAllValues().get(0);
        assertThat(first.getId().getObservedAt()).isEqualTo(T_2024_01_11);
        assertThat(first.getId().getProductId()).isEqualTo((short) 1);
        assertThat(first.getId().getPublisherId()).isEqualTo((short) 1);
        assertThat(first.getId().getRegion()).isEqualTo((short) 1);
        assertThat(first.getPriceVnd()).isEqualByComparingTo("21041");
        assertThat(first.getDeltaVnd()).isEqualByComparingTo("35");
        assertThat(first.getCycleId()).isEqualTo(10L);

        assertThat(saved.getAllValues().get(1).getDeltaVnd()).isEqualByComparingTo("-19");
    }

    /** Kỳ đã có từ CSV spike phải được bỏ qua, không nhân đôi. */
    @Test
    void importBulletinSkipsPriceThatAlreadyExists() {
        when(prices.existsById(any(RetailPriceId.class))).thenAnswer(inv ->
                ((RetailPriceId) inv.getArgument(0)).getProductId() == 2);

        PriceImportService.ImportResult result = service.importBulletin(bulletin(
                new ParsedPrice("E5RON92", 21041, "lít", 35L),
                new ParsedPrice("RON95", 21935, "lít", 19L)));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.skippedExisting()).isEqualTo(1);
    }

    @Test
    void importBulletinStoresNullDeltaWhenBulletinHasNone() {
        service.importBulletin(bulletin(new ParsedPrice("E5RON92", 21041, "lít", null)));

        ArgumentCaptor<RetailPrice> saved = ArgumentCaptor.forClass(RetailPrice.class);
        verify(prices).save(saved.capture());
        assertThat(saved.getValue().getDeltaVnd()).isNull();
    }

    @Test
    void importBulletinReportsUnknownProductCodeInsteadOfFailing() {
        when(products.findByCode("MYSTERY")).thenReturn(Optional.empty());

        PriceImportService.ImportResult result = service.importBulletin(bulletin(
                new ParsedPrice("E5RON92", 21041, "lít", 35L),
                new ParsedPrice("MYSTERY", 1234, "lít", 1L)));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.unrecognised()).isEqualTo(1);
        assertThat(result.unknownNames()).containsExactly("MYSTERY");
    }

    @Test
    void importBulletinRejectsBulletinWithoutEffectiveDate() {
        ParsedBulletin noDate = new ParsedBulletin("u", null,
                List.of(new ParsedPrice("E5RON92", 21041, "lít", 35L)), List.of());

        assertThatThrownBy(() -> service.importBulletin(noDate))
                .isInstanceOf(IllegalArgumentException.class);
        verify(prices, never()).save(any());
    }

    /**
     * importCsv vừa được refactor để dùng chung insertIfAbsent với importBulletin;
     * test này giữ cho hành vi cũ không đổi (chưa có DB để chạy thật).
     */
    @Test
    void importCsvStillInsertsRowsAndKeepsBlankDeltaAsNull(@TempDir Path dir) throws IOException {
        when(products.findAll()).thenReturn(List.of(product(1, "E5RON92"), product(2, "RON95")));
        Path csv = dir.resolve("spike.csv");
        Files.writeString(csv, String.join("\n",
                "effective_at,product,price_vnd,unit,delta_vnd,url",
                "2024-01-11T15:00,Xăng E5RON92,21041,lít,35,http://x",
                "2024-01-11T15:00,Xăng RON95-III,21935,lít,,http://x",
                "2024-01-11T15:00,Mặt hàng lạ,1,lít,1,http://x"), StandardCharsets.UTF_8);

        PriceImportService.ImportResult result = service.importCsv(csv);

        assertThat(result.inserted()).isEqualTo(2);
        assertThat(result.unrecognised()).isEqualTo(1);

        ArgumentCaptor<RetailPrice> saved = ArgumentCaptor.forClass(RetailPrice.class);
        verify(prices, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getDeltaVnd()).isEqualByComparingTo(new BigDecimal("35"));
        assertThat(saved.getAllValues().get(1).getDeltaVnd()).isNull();
        assertThat(saved.getAllValues().get(0).getId().getObservedAt()).isEqualTo(T_2024_01_11);
    }

    private static ParsedBulletin bulletin(ParsedPrice... ps) {
        return new ParsedBulletin("https://moit.gov.vn/x.html",
                LocalDateTime.of(2024, 1, 11, 15, 0), List.of(ps), List.of());
    }

    private static FuelProduct product(int id, String code) {
        return FuelProduct.builder().id((short) id).code(code).build();
    }
}
