package fitiuh.com.fuelcast_core.runner;

import fitiuh.com.fuelcast_core.forecast.LiveForecastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Điểm vào dòng lệnh: chấm điểm các dự báo LIVE đã có đáp án và tạo dự báo cho kỳ
 * tới. Cùng việc bộ quét tự làm sau mỗi lượt, dùng để chạy tay và để kiểm tra.
 *
 * Chạy (không mở web server, app tự thoát khi xong):
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--fuelcast.forecast.live=true --spring.main.web-application-type=none"
 */
@Component
public class LiveForecastRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveForecastRunner.class);
    private static final String OPTION = "fuelcast.forecast.live";

    private final LiveForecastService live;

    public LiveForecastRunner(LiveForecastService live) {
        this.live = live;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> values = args.getOptionValues(OPTION);
        if (values == null || values.isEmpty() || !"true".equalsIgnoreCase(values.get(0))) {
            return;
        }

        LiveForecastService.RefreshResult r = live.refresh();
        log.info("Dự báo LIVE ({}): chấm {}, tạo mới {}, đã có {}, không dự báo được {}",
                LiveForecastService.MODEL_VERSION, r.graded(), r.created(), r.existing(), r.skipped());
    }
}
