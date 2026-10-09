package fitiuh.com.fuelcast_core.runner;

import fitiuh.com.fuelcast_core.service.PriceImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Điểm vào dòng lệnh cho việc nạp CSV lịch sử. Chỉ đọc tham số và gọi service —
 * toàn bộ nghiệp vụ nằm ở {@link PriceImportService}.
 *
 * Chạy:
 *   mvn spring-boot:run -Dspring-boot.run.arguments=--fuelcast.import.csv=spike_result.csv
 */
@Component
public class SpikeCsvImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SpikeCsvImportRunner.class);
    private static final String OPTION = "fuelcast.import.csv";

    private final PriceImportService importService;

    public SpikeCsvImportRunner(PriceImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> values = args.getOptionValues(OPTION);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            return;                         // không truyền tham số thì bỏ qua
        }

        Path csv = Path.of(values.get(0));
        if (!Files.exists(csv)) {
            log.error("Không tìm thấy file CSV: {}", csv.toAbsolutePath());
            return;
        }

        PriceImportService.ImportResult r = importService.importCsv(csv);

        log.info("Tổng số kỳ trong DB: {}", r.totalCycles());
        if (!r.unknownNames().isEmpty()) {
            log.warn("Tên mặt hàng chưa map được (bổ sung vào ProductCodeResolver): {}",
                    r.unknownNames());
        }
    }
}
