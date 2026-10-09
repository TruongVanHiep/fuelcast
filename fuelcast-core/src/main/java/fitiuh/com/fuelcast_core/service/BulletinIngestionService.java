package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.entity.IngestionRun;
import fitiuh.com.fuelcast_core.entity.IngestionStatus;
import fitiuh.com.fuelcast_core.repository.IngestionRunRepository;
import fitiuh.com.fuelcast_core.service.parser.MoitBulletinParser;
import fitiuh.com.fuelcast_core.service.parser.ParsedBulletin;
import fitiuh.com.fuelcast_core.service.scraper.BulletinLink;
import fitiuh.com.fuelcast_core.service.scraper.MoitBulletinScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Điều phối đường đi của một bản tin: tải → lưu bản gốc → parse → ghi giá.
 *
 * Class này CỐ Ý không đánh @Transactional. Mỗi lệnh save của repository tự
 * chạy trong transaction riêng và commit ngay, nên bản gốc (raw_payload) đã nằm
 * trong DB trước khi parse và ghi giá. Nếu bọc cả hàm trong một transaction thì
 * parse lỗi sẽ cuốn luôn bản gốc đi mất, đúng thứ ta cần giữ để sửa parser.
 *
 * Việc ghi giá thì ngược lại: PriceImportService.importBulletin là một
 * transaction riêng, nguyên khối cho cả bản tin.
 */
@Service
public class BulletinIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BulletinIngestionService.class);

    static final String SOURCE = "MOIT_BULLETIN";

    /** Kết quả xử lý một URL. rows là số dòng giá MỚI được ghi. */
    public record Outcome(IngestionStatus status, int rows, String message) { }

    public record BackfillResult(int found, int skipped, int succeeded, int failed, List<String> failedUrls) { }

    private final MoitBulletinScraper scraper;
    private final MoitBulletinParser parser;
    private final PriceImportService importer;
    private final IngestionRunRepository runs;
    private final Duration requestGap;

    /**
     * requestGap là giãn cách giữa hai lần tải bài. Mặc định 5 giây để lịch sự
     * với MOIT; test truyền Duration.ZERO để không phải ngồi chờ.
     */
    public BulletinIngestionService(MoitBulletinScraper scraper,
                                    MoitBulletinParser parser,
                                    PriceImportService importer,
                                    IngestionRunRepository runs,
                                    @Value("${fuelcast.ingestion.request-gap:PT5S}") Duration requestGap) {
        this.scraper = scraper;
        this.parser = parser;
        this.importer = importer;
        this.runs = runs;
        this.requestGap = requestGap;
    }

    /**
     * Nạp một bản tin. Lỗi của riêng URL này (tải hỏng, parser không đọc được,
     * ghi giá lỗi) được ghi vào ingestion_run và trả về dưới dạng Outcome FAILED,
     * không ném ra ngoài, để một bài hỏng không làm chết cả đợt backfill.
     *
     * InterruptedException thì phải ném tiếp: nuốt nó nghĩa là không ai dừng
     * được vòng lặp. Dòng RUNNING bị bỏ lại chính là dấu vết của lần bị ngắt.
     */
    public Outcome ingest(String url) throws InterruptedException {
        IngestionRun run = runs.save(IngestionRun.builder().source(SOURCE).targetUrl(url).build());
        try {
            String html = scraper.fetchBulletin(url);

            run.setRawPayload(html);
            run = runs.save(run);                       // bản gốc đã commit

            ParsedBulletin parsed = parser.parse(html, url);
            if (!parsed.isUsable()) {
                return fail(run, "Parser không đọc được ngày hiệu lực hoặc giá nào");
            }

            PriceImportService.ImportResult result = importer.importBulletin(parsed);
            run.succeed(result.inserted());
            runs.save(run);
            return new Outcome(IngestionStatus.SUCCESS, result.inserted(), null);

        } catch (IOException | RuntimeException e) {
            return fail(run, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Lùi về {@code since}, nạp mọi bản tin chưa nạp thành công. Chạy lại bao
     * nhiêu lần cũng được: URL đã SUCCESS bị bỏ qua, URL FAILED được thử lại.
     *
     * Bản tin đã có từ CSV spike vẫn được tải lại một lần (CSV không để lại dòng
     * ingestion_run); giá của chúng bị bỏ qua nhờ khoá tự nhiên, còn bản gốc thì
     * được lưu lại, phục vụ việc parse lại về sau.
     */
    public BackfillResult backfill(LocalDate since) throws IOException, InterruptedException {
        // Cũ nhất trước: đợt chạy dang dở vẫn để lại một chuỗi liên tục từ quá khứ.
        List<BulletinLink> links = scraper.findBulletinsSince(since).stream()
                .distinct()
                .sorted(Comparator.comparing(BulletinLink::publishedOn,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();

        int skipped = 0;
        int succeeded = 0;
        List<String> failedUrls = new ArrayList<>();
        boolean fetchedBefore = false;

        for (BulletinLink link : links) {
            if (runs.existsByTargetUrlAndStatus(link.url(), IngestionStatus.SUCCESS)) {
                skipped++;
                continue;
            }
            if (fetchedBefore) {
                Thread.sleep(requestGap.toMillis());
            }
            fetchedBefore = true;

            Outcome outcome = ingest(link.url());
            if (outcome.status() == IngestionStatus.SUCCESS) {
                succeeded++;
            } else {
                failedUrls.add(link.url());
            }
        }
        return new BackfillResult(links.size(), skipped, succeeded, failedUrls.size(), failedUrls);
    }

    private Outcome fail(IngestionRun run, String message) {
        log.warn("Nạp thất bại {}: {}", run.getTargetUrl(), message);
        run.fail(message);
        runs.save(run);
        return new Outcome(IngestionStatus.FAILED, 0, message);
    }
}
