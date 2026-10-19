package fitiuh.com.fuelcast_core.runner;

import fitiuh.com.fuelcast_core.forecast.PythonReplayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Điểm vào dòng lệnh để phát lại mô hình Python trên lịch sử và lưu vào forecast. Cần dịch
 * vụ fuelcast-forecast đang chạy. Idempotent: chạy lại không nhân đôi dòng.
 *
 * Chạy (không mở web server, app tự thoát khi xong):
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--fuelcast.forecast.python.enabled=true --fuelcast.forecast.python.replay=true --spring.main.web-application-type=none"
 */
@Component
@ConditionalOnProperty(name = "fuelcast.forecast.python.enabled", havingValue = "true")
public class PythonReplayRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PythonReplayRunner.class);
    private static final String OPTION = "fuelcast.forecast.python.replay";

    private final PythonReplayService replay;

    public PythonReplayRunner(PythonReplayService replay) {
        this.replay = replay;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> values = args.getOptionValues(OPTION);
        if (values == null || values.isEmpty() || !"true".equalsIgnoreCase(values.get(0))) {
            return;
        }

        PythonReplayService.ReplayResult r = replay.replay();
        log.info("Phát lại mô hình {}: {} mặt hàng, ghi mới {} dự báo, đã có sẵn {}",
                r.modelVersion(), r.products(), r.inserted(), r.alreadyPresent());
    }
}
