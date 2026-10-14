package fitiuh.com.fuelcast_core.forecast;

import fitiuh.com.fuelcast_core.api.CurrentCycleDto;
import fitiuh.com.fuelcast_core.entity.AdjustmentCycle;
import fitiuh.com.fuelcast_core.entity.CycleStatus;
import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.RetailPriceId;
import fitiuh.com.fuelcast_core.entity.WorldPrice;
import fitiuh.com.fuelcast_core.entity.WorldPriceId;
import fitiuh.com.fuelcast_core.forecast.ForecastWriter.LiveRow;
import fitiuh.com.fuelcast_core.forecast.LiveForecastService.RefreshResult;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import fitiuh.com.fuelcast_core.repository.WorldPriceRepository;
import fitiuh.com.fuelcast_core.service.AdjustmentCycleService;
import fitiuh.com.fuelcast_core.service.CycleQueryService;
import fitiuh.com.fuelcast_core.service.NoCycleDataException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveForecastServiceTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime T0 = OffsetDateTime.of(2024, 1, 4, 15, 0, 0, 0, VN);
    private static final int PERIODS = 30;
    /** Kỳ cuối của chuỗi dựng dưới đây (thứ Năm), và kỳ dự kiến kế tiếp một tuần sau. */
    private static final OffsetDateTime LAST = T0.plusWeeks(PERIODS - 1);
    private static final OffsetDateTime NEXT = LAST.plusWeeks(1);
    /** Thứ Bảy sau kỳ cuối: còn đúng 5 ngày tới giờ công bố dự kiến. */
    private static final OffsetDateTime NOW = LAST.plusDays(2);

    private final FuelProductRepository products = mock(FuelProductRepository.class);
    private final RetailPriceRepository prices = mock(RetailPriceRepository.class);
    private final WorldPriceRepository worldPrices = mock(WorldPriceRepository.class);
    private final CycleQueryService cycleQuery = mock(CycleQueryService.class);
    private final AdjustmentCycleService cycles = mock(AdjustmentCycleService.class);
    private final ForecastWriter writer = mock(ForecastWriter.class);

    private final LiveForecastService service = new LiveForecastService(
            products, prices, worldPrices, cycleQuery, cycles, writer,
            Clock.fixed(NOW.toInstant(), VN));

    private static FuelProduct product(int id, String code) {
        return FuelProduct.builder().id((short) id).code(code).nameVi(code).unit("lít")
                .displayOrder((short) id).build();
    }

    /** Chuỗi liên tục: giá tích luỹ đúng các delta, Δ thế giới thay đổi từng tuần. */
    private static List<RetailPrice> retail(short productId, int periods) {
        List<RetailPrice> out = new ArrayList<>();
        BigDecimal price = new BigDecimal(20000);
        for (int i = 0; i < periods; i++) {
            BigDecimal delta = new BigDecimal(Math.round(170 * worldDelta(i)));
            price = price.add(delta);
            out.add(new RetailPrice(new RetailPriceId(T0.plusWeeks(i), productId, (short) 1, (short) 1),
                    price, delta, 100L + i));
        }
        return out;
    }

    private static double worldDelta(int i) {
        return (i % 5) - 2 + i * 0.1;
    }

    private static List<WorldPrice> world(String symbol, int periods) {
        List<WorldPrice> out = new ArrayList<>();
        double usd = 80;
        for (int i = 0; i < periods; i++) {
            usd += (i == 0 ? 0 : worldDelta(i));
            out.add(new WorldPrice(new WorldPriceId(T0.plusWeeks(i), symbol),
                    BigDecimal.valueOf(usd), "USD/thùng", "MOIT_CYCLE_AVG"));
        }
        return out;
    }

    private void givenLatestAnnouncementIs(OffsetDateTime at) {
        when(cycleQuery.currentCycle()).thenReturn(new CurrentCycleDto(
                at, at.toLocalDate().minusDays(7), at.toLocalDate(), NEXT, 0, false));
        when(cycles.openCycleFor(NEXT)).thenReturn(AdjustmentCycle.builder().id(900L)
                .cycleStart(LocalDate.of(2024, 7, 11)).cycleEnd(LocalDate.of(2024, 7, 18))
                .status(CycleStatus.OPEN).build());
    }

    private void givenSeriesFor(FuelProduct p, String symbol, int periods) {
        when(products.findAll()).thenReturn(List.of(p));
        when(prices.findHistory(any(Short.class), any(), any())).thenReturn(retail(p.getId(), periods));
        when(worldPrices.findByIdSymbolOrderByIdObservedAtAsc(symbol)).thenReturn(world(symbol, periods));
    }

    @Test
    void createsALiveForecastForTheNextCycleFromTheLatestAnnouncement() {
        givenLatestAnnouncementIs(LAST);
        givenSeriesFor(product(2, "RON95"), "MOPS_RON95", PERIODS);
        when(writer.insertLive(any())).thenReturn(true);

        RefreshResult r = service.refresh();

        assertThat(r.created()).isEqualTo(1);
        ArgumentCaptor<LiveRow> row = ArgumentCaptor.forClass(LiveRow.class);
        verify(writer).insertLive(row.capture());
        LiveRow live = row.getValue();

        assertThat(live.cycleId()).isEqualTo(900L);                       // kỳ OPEN, không phải kỳ đã công bố
        assertThat(live.createdAt()).isEqualTo(NOW);                      // tạo TRƯỚC bản tin kế tiếp
        assertThat(live.modelVersion()).isEqualTo("lagged-world-ols-v1");
        assertThat(live.horizonDays()).isEqualTo(5);                      // từ Thứ Bảy tới Thứ Năm kế
        assertThat(live.productId()).isEqualTo((short) 2);
        // giá dự đoán = giá kỳ cuối + mức đổi dự đoán
        BigDecimal lastPrice = retail((short) 2, PERIODS).get(PERIODS - 1).getPriceVnd();
        assertThat(live.predictedVnd()).isEqualByComparingTo(lastPrice.add(live.predictedDelta()));
        assertThat(live.featuresJson()).contains("\"trainPairs\":28").contains("deltaUsdPrevPeriod");
    }

    /**
     * Dầu hỏa không còn trên bản công bố mới nhất. Dự báo "kỳ sau" cho một thứ không
     * còn được công bố là vô nghĩa, nên phải bỏ qua chứ không ghi một con số.
     */
    @Test
    void skipsAProductThatIsNotOnTheLatestAnnouncement() {
        givenLatestAnnouncementIs(LAST);
        // chuỗi của mặt hàng này dừng sớm hơn bản công bố mới nhất 5 tuần
        givenSeriesFor(product(4, "KEROSENE"), "MOPS_KEROSENE", PERIODS - 5);

        RefreshResult r = service.refresh();

        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.created()).isZero();
        verify(writer, never()).insertLive(any());
    }

    @Test
    void skipsWhenThereAreTooFewPairsToLearnFrom() {
        givenLatestAnnouncementIs(T0.plusWeeks(10));
        givenSeriesFor(product(2, "RON95"), "MOPS_RON95", 11);   // 9 cặp < 20

        assertThat(service.refresh().skipped()).isEqualTo(1);
        verify(writer, never()).insertLive(any());
    }

    @Test
    void countsAnAlreadyStoredForecastAsExistingNotCreated() {
        givenLatestAnnouncementIs(LAST);
        givenSeriesFor(product(2, "RON95"), "MOPS_RON95", PERIODS);
        when(writer.insertLive(any())).thenReturn(false);

        RefreshResult r = service.refresh();

        assertThat(r.existing()).isEqualTo(1);
        assertThat(r.created()).isZero();
    }

    @Test
    void passesThroughHowManyForecastsWereGraded() {
        when(writer.gradePendingLive()).thenReturn(5);
        when(cycleQuery.currentCycle()).thenThrow(new NoCycleDataException());

        RefreshResult r = service.refresh();

        assertThat(r.graded()).isEqualTo(5);
        assertThat(r.created()).isZero();
        verify(writer, never()).insertLive(any());
    }

    /** Chấm điểm phải xảy ra TRƯỚC khi tạo dự báo mới, để dự báo cũ không bị lẫn. */
    @Test
    void gradesBeforeItCreatesNewForecasts() {
        givenLatestAnnouncementIs(LAST);
        givenSeriesFor(product(2, "RON95"), "MOPS_RON95", PERIODS);
        when(writer.insertLive(any())).thenReturn(true);

        service.refresh();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(writer);
        order.verify(writer).gradePendingLive();
        order.verify(writer).insertLive(any());
    }

    @Test
    void directionIsFlatForATinyPredictedChange() {
        assertThat(ForecastReplayService.directionOf(0.3)).isEqualTo("FLAT");
    }
}
