package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.entity.IngestionRun;
import fitiuh.com.fuelcast_core.entity.IngestionStatus;
import fitiuh.com.fuelcast_core.repository.IngestionRunRepository;
import fitiuh.com.fuelcast_core.service.parser.MoitBulletinParser;
import fitiuh.com.fuelcast_core.service.parser.ParsedBulletin;
import fitiuh.com.fuelcast_core.service.scraper.BulletinLink;
import fitiuh.com.fuelcast_core.service.scraper.MoitBulletinScraper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulletinIngestionServiceTest {

    private static final String URL = "https://moit.gov.vn/tin-tuc/thong-bao/x-ngay-11-1-2024.html";

    private final MoitBulletinScraper scraper = mock(MoitBulletinScraper.class);
    private final PriceImportService importer = mock(PriceImportService.class);
    private final IngestionRunRepository runs = mock(IngestionRunRepository.class);

    /** Parser thật: test này kiểm cả việc ghép parser vào đường đi. */
    private final BulletinIngestionService service = new BulletinIngestionService(
            scraper, new MoitBulletinParser(), importer, runs, Duration.ZERO);

    /** Ảnh chụp trạng thái run tại MỖI lần save, vì entity là một object mutable. */
    private final List<String> rawAtEachSave = new ArrayList<>();
    private final List<IngestionStatus> statusAtEachSave = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(runs.save(any(IngestionRun.class))).thenAnswer(inv -> {
            IngestionRun r = inv.getArgument(0);
            rawAtEachSave.add(r.getRawPayload());
            statusAtEachSave.add(r.getStatus());
            return r;
        });
    }

    /**
     * Lý do tồn tại của ingestion_run.raw_payload: khi bước sau lỗi, bản gốc vẫn
     * phải nằm trong DB. Tại thời điểm importer được gọi, lần save thứ hai (đã có
     * HTML) phải xảy ra rồi.
     */
    @Test
    void savesRawPayloadBeforeImportingPrices() throws Exception {
        when(scraper.fetchBulletin(URL)).thenReturn(fixture("moit-2024-01-11.html"));
        List<String> rawWhenImporting = new ArrayList<>();
        when(importer.importBulletin(any(ParsedBulletin.class))).thenAnswer(inv -> {
            rawWhenImporting.add(rawAtEachSave.isEmpty() ? null : rawAtEachSave.get(rawAtEachSave.size() - 1));
            return new PriceImportService.ImportResult(5, 0, 0, Set.of(), 1);
        });

        service.ingest(URL);

        assertThat(rawWhenImporting).hasSize(1);
        assertThat(rawWhenImporting.get(0)).isNotNull().contains("điều hành giá xăng dầu");
    }

    @Test
    void marksRunSuccessWithNumberOfNewRows() throws Exception {
        when(scraper.fetchBulletin(URL)).thenReturn(fixture("moit-2024-01-11.html"));
        when(importer.importBulletin(any(ParsedBulletin.class)))
                .thenReturn(new PriceImportService.ImportResult(5, 0, 0, Set.of(), 1));

        BulletinIngestionService.Outcome outcome = service.ingest(URL);

        assertThat(outcome.status()).isEqualTo(IngestionStatus.SUCCESS);
        assertThat(outcome.rows()).isEqualTo(5);
        assertThat(statusAtEachSave).containsExactly(
                IngestionStatus.RUNNING, IngestionStatus.RUNNING, IngestionStatus.SUCCESS);
    }

    /** rows_ingested đếm cả dòng giá bán lẻ lẫn dòng giá thế giới mới ghi. */
    @Test
    void countsWorldPriceRowsInTheNumberOfNewRows() throws Exception {
        when(scraper.fetchBulletin(URL)).thenReturn(fixture("moit-2024-01-11.html"));
        when(importer.importBulletin(any(ParsedBulletin.class)))
                .thenReturn(new PriceImportService.ImportResult(5, 0, 0, Set.of(), 1, 5));

        BulletinIngestionService.Outcome outcome = service.ingest(URL);

        assertThat(outcome.rows()).isEqualTo(10);
    }

    @Test
    void fetchFailureMarksRunFailedWithoutPayloadAndNeverImports() throws Exception {
        when(scraper.fetchBulletin(URL)).thenThrow(new IOException("MOIT trả HTTP 302"));

        BulletinIngestionService.Outcome outcome = service.ingest(URL);

        assertThat(outcome.status()).isEqualTo(IngestionStatus.FAILED);
        assertThat(outcome.message()).contains("302");
        assertThat(rawAtEachSave).containsOnlyNulls();
        verify(importer, never()).importBulletin(any());
    }

    /** Bài không phải bản tin giá: vẫn giữ bản gốc để còn xem lại vì sao parser từ chối. */
    @Test
    void unusableBulletinFailsButKeepsRawPayload() throws Exception {
        when(scraper.fetchBulletin(URL)).thenReturn("<html><body>Tổng kết công tác năm 2025</body></html>");

        BulletinIngestionService.Outcome outcome = service.ingest(URL);

        assertThat(outcome.status()).isEqualTo(IngestionStatus.FAILED);
        assertThat(rawAtEachSave.get(rawAtEachSave.size() - 1)).contains("Tổng kết");
        verify(importer, never()).importBulletin(any());
    }

    @Test
    void importFailureKeepsRawPayloadAndMarksRunFailed() throws Exception {
        when(scraper.fetchBulletin(URL)).thenReturn(fixture("moit-2024-01-11.html"));
        when(importer.importBulletin(any(ParsedBulletin.class)))
                .thenThrow(new IllegalStateException("Thiếu price_publisher 'MOIT'"));

        BulletinIngestionService.Outcome outcome = service.ingest(URL);

        assertThat(outcome.status()).isEqualTo(IngestionStatus.FAILED);
        assertThat(outcome.message()).contains("Thiếu price_publisher");
        assertThat(rawAtEachSave.get(rawAtEachSave.size() - 1)).isNotNull();
        assertThat(statusAtEachSave.get(statusAtEachSave.size() - 1)).isEqualTo(IngestionStatus.FAILED);
    }

    /** Ngắt tiến trình không được bị nuốt thành "một URL lỗi". */
    @Test
    void interruptionPropagatesInsteadOfBeingSwallowed() throws Exception {
        when(scraper.fetchBulletin(URL)).thenThrow(new InterruptedException());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.ingest(URL))
                .isInstanceOf(InterruptedException.class);
    }

    /**
     * Scraper trả mới nhất trước; backfill phải xử lý cũ nhất trước, bỏ qua URL
     * đã SUCCESS, và một URL lỗi không được chặn các URL còn lại.
     */
    @Test
    void backfillGoesOldestFirstSkipsDoneAndSurvivesAFailure() throws Exception {
        String newest = "https://moit.gov.vn/tin-tuc/a.html";
        String middle = "https://moit.gov.vn/tin-tuc/c.html";
        String oldest = "https://moit.gov.vn/tin-tuc/b.html";
        when(scraper.findBulletinsSince(any(LocalDate.class))).thenReturn(List.of(
                new BulletinLink(newest, "t", LocalDate.of(2024, 1, 25)),
                new BulletinLink(middle, "t", LocalDate.of(2024, 1, 18)),
                new BulletinLink(oldest, "t", LocalDate.of(2024, 1, 11))));
        when(runs.existsByTargetUrlAndStatus(newest, IngestionStatus.SUCCESS)).thenReturn(true);
        when(scraper.fetchBulletin(oldest)).thenThrow(new IOException("hỏng"));
        when(scraper.fetchBulletin(middle)).thenReturn(fixture("moit-2024-01-11.html"));
        when(importer.importBulletin(any(ParsedBulletin.class)))
                .thenReturn(new PriceImportService.ImportResult(5, 0, 0, Set.of(), 1));

        BulletinIngestionService.BackfillResult result = service.backfill(LocalDate.of(2024, 1, 1));

        assertThat(result.found()).isEqualTo(3);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedUrls()).containsExactly(oldest);

        InOrder order = inOrder(scraper);
        order.verify(scraper).fetchBulletin(oldest);
        order.verify(scraper).fetchBulletin(middle);
        verify(scraper, never()).fetchBulletin(newest);
    }

    @Test
    void backfillDoesNotFetchAnythingWhenEverythingIsAlreadyDone() throws Exception {
        when(scraper.findBulletinsSince(any(LocalDate.class))).thenReturn(List.of(
                new BulletinLink(URL, "t", LocalDate.of(2024, 1, 11))));
        when(runs.existsByTargetUrlAndStatus(anyString(), any())).thenReturn(true);

        BulletinIngestionService.BackfillResult result = service.backfill(LocalDate.of(2024, 1, 1));

        assertThat(result.skipped()).isEqualTo(1);
        verify(scraper, never()).fetchBulletin(anyString());
    }

    /**
     * Bản SUCCESS cũ được đọc lại từ bản gốc để bổ sung phần parser mới đọc được
     * (giá thế giới), và tuyệt đối không động tới mạng.
     */
    @Test
    void reprocessReadsStoredPayloadWithoutTouchingTheNetwork() throws Exception {
        IngestionRun stored = storedRun(1L, IngestionStatus.SUCCESS, fixture("moit-2024-01-11.html"));
        when(runs.findIdsWithRawPayload()).thenReturn(List.of(1L));
        when(runs.findById(1L)).thenReturn(java.util.Optional.of(stored));
        when(importer.importBulletin(any(ParsedBulletin.class)))
                .thenReturn(new PriceImportService.ImportResult(0, 5, 0, Set.of(), 1, 5));

        BulletinIngestionService.ReprocessResult result = service.reprocessStored();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.newRows()).isEqualTo(5);
        assertThat(result.recovered()).isZero();
        org.mockito.Mockito.verifyNoInteractions(scraper);
        verify(runs, never()).save(any(IngestionRun.class));      // SUCCESS giữ nguyên
    }

    /** Ca thật: bản 26/3/2026 từng FAILED vì "24 giờ 00", sau khi sửa parser thì được cứu. */
    @Test
    void reprocessRecoversAFailedRunOnceTheParserCanReadIt() throws Exception {
        IngestionRun failed = storedRun(2L, IngestionStatus.FAILED, fixture("moit-2026-03-26.html"));
        failed.setErrorMessage("DateTimeException: Invalid value for HourOfDay (valid values 0 - 23): 24");
        when(runs.findIdsWithRawPayload()).thenReturn(List.of(2L));
        when(runs.findById(2L)).thenReturn(java.util.Optional.of(failed));
        when(importer.importBulletin(any(ParsedBulletin.class)))
                .thenReturn(new PriceImportService.ImportResult(0, 5, 0, Set.of(), 1, 5));

        BulletinIngestionService.ReprocessResult result = service.reprocessStored();

        assertThat(result.recovered()).isEqualTo(1);
        assertThat(failed.getStatus()).isEqualTo(IngestionStatus.SUCCESS);
        assertThat(failed.getErrorMessage()).isNull();
        verify(runs).save(failed);
    }

    /** Một bản gốc vẫn không đọc được không được chặn các bản còn lại. */
    @Test
    void reprocessCarriesOnPastAPayloadThatStillFails() throws Exception {
        IngestionRun notABulletin = storedRun(3L, IngestionStatus.FAILED,
                "<html><body>Tổng kết công tác năm 2025</body></html>");
        IngestionRun good = storedRun(4L, IngestionStatus.SUCCESS, fixture("moit-2024-01-11.html"));
        when(runs.findIdsWithRawPayload()).thenReturn(List.of(3L, 4L));
        when(runs.findById(3L)).thenReturn(java.util.Optional.of(notABulletin));
        when(runs.findById(4L)).thenReturn(java.util.Optional.of(good));
        when(importer.importBulletin(any(ParsedBulletin.class)))
                .thenReturn(new PriceImportService.ImportResult(0, 5, 0, Set.of(), 1, 5));

        BulletinIngestionService.ReprocessResult result = service.reprocessStored();

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedUrls()).containsExactly(notABulletin.getTargetUrl());
        assertThat(result.newRows()).isEqualTo(5);
        assertThat(notABulletin.getStatus()).isEqualTo(IngestionStatus.FAILED);
    }

    /**
     * Bản đang SUCCESS mà parse lại hỏng nghĩa là parser vừa bị hồi quy. Phải báo
     * ra, nhưng không hạ trạng thái: dữ liệu nó đã ghi trước đó vẫn đúng.
     */
    @Test
    void reprocessReportsButDoesNotDowngradeASuccessfulRunThatNowFails() throws Exception {
        IngestionRun regressed = storedRun(5L, IngestionStatus.SUCCESS, "<html><body>không có giá</body></html>");
        when(runs.findIdsWithRawPayload()).thenReturn(List.of(5L));
        when(runs.findById(5L)).thenReturn(java.util.Optional.of(regressed));

        BulletinIngestionService.ReprocessResult result = service.reprocessStored();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(regressed.getStatus()).isEqualTo(IngestionStatus.SUCCESS);
        verify(runs, never()).save(any(IngestionRun.class));
    }

    private static IngestionRun storedRun(long id, IngestionStatus status, String rawPayload) {
        return IngestionRun.builder()
                .id(id).source("MOIT_BULLETIN").status(status)
                .targetUrl("https://moit.gov.vn/tin-tuc/run-" + id + ".html")
                .rawPayload(rawPayload).build();
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            assertThat(in).as("thiếu fixture %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
