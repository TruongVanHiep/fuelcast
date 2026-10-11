package fitiuh.com.fuelcast_core.runner;

import fitiuh.com.fuelcast_core.forecast.ForecastReplayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Điểm vào dòng lệnh để phát lại mô hình trên lịch sử và lưu vào forecast.
 * Idempotent: chạy lại không nhân đôi dòng.
 *
 * Chạy (không mở web server, app tự thoát khi xong):
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--fuelcast.forecast.replay=true --spring.main.web-application-type=none"
 */
@Component
public class ForecastReplayRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ForecastReplayRunner.class);
    private static final String OPTION = "fuelcast.forecast.replay";

    private final ForecastReplayService replay;

    public ForecastReplayRunner(ForecastReplayService replay) {
        this.replay = replay;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> values = args.getOptionValues(OPTION);
        if (values == null || values.isEmpty() || !"true".equalsIgnoreCase(values.get(0))) {
            return;
        }

        ForecastReplayService.ReplayResult r = replay.replay();
        log.info("Phát lại mô hình {}: {} mặt hàng, ghi mới {} dự báo, đã có sẵn {}",
                ForecastReplayService.MODEL_VERSION, r.products(), r.inserted(), r.alreadyPresent());
    }
}
