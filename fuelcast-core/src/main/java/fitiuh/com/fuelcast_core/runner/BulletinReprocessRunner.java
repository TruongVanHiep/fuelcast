package fitiuh.com.fuelcast_core.runner;

import fitiuh.com.fuelcast_core.service.BulletinIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Điểm vào dòng lệnh để parse lại các bản gốc đã lưu trong ingestion_run. Không
 * gửi request nào tới MOIT nên chạy trong vài giây.
 *
 * Chạy (không mở web server, app tự thoát khi xong):
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--fuelcast.reprocess=true --spring.main.web-application-type=none"
 */
@Component
public class BulletinReprocessRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BulletinReprocessRunner.class);
    private static final String OPTION = "fuelcast.reprocess";

    private final BulletinIngestionService ingestion;

    public BulletinReprocessRunner(BulletinIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> values = args.getOptionValues(OPTION);
        if (values == null || values.isEmpty() || !"true".equalsIgnoreCase(values.get(0))) {
            return;                         // không bật cờ thì bỏ qua
        }

        BulletinIngestionService.ReprocessResult r = ingestion.reprocessStored();

        log.info("Parse lại {} bản gốc: ghi thêm {} dòng, cứu được {} bản FAILED, còn lỗi {}",
                r.processed(), r.newRows(), r.recovered(), r.failed());
        if (!r.failedUrls().isEmpty()) {
            log.warn("Các URL còn lỗi (bản SUCCESS mà lỗi ở đây nghĩa là parser bị hồi quy): {}",
                    r.failedUrls());
        }
    }
}
