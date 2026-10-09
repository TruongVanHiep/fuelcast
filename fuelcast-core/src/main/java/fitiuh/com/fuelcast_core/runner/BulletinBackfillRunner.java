package fitiuh.com.fuelcast_core.runner;

import fitiuh.com.fuelcast_core.service.BulletinIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Điểm vào dòng lệnh cho việc vá lịch sử từ moit.gov.vn. Chỉ đọc tham số và
 * gọi service, giống SpikeCsvImportRunner.
 *
 * Chạy (mất cỡ vài chục phút vì phải giãn cách 5 giây giữa các request):
 *   mvn spring-boot:run -Dspring-boot.run.arguments=--fuelcast.backfill.since=2024-01-01
 */
@Component
public class BulletinBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BulletinBackfillRunner.class);
    private static final String OPTION = "fuelcast.backfill.since";

    private final BulletinIngestionService ingestion;

    public BulletinBackfillRunner(BulletinIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> values = args.getOptionValues(OPTION);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            return;                         // không truyền tham số thì bỏ qua
        }

        LocalDate since;
        try {
            since = LocalDate.parse(values.get(0));
        } catch (DateTimeParseException e) {
            log.error("--{} phải có dạng yyyy-MM-dd, nhận được: {}", OPTION, values.get(0));
            return;
        }

        BulletinIngestionService.BackfillResult r = ingestion.backfill(since);

        log.info("Backfill từ {}: tìm thấy {} bản tin, bỏ qua {} (đã nạp), nạp mới {}, lỗi {}",
                since, r.found(), r.skipped(), r.succeeded(), r.failed());
        if (!r.failedUrls().isEmpty()) {
            log.warn("Các URL lỗi (xem ingestion_run.error_message, chạy lại sẽ thử lại): {}",
                    r.failedUrls());
        }
    }
}
