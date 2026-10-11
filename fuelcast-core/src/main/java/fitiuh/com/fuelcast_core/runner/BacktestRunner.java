package fitiuh.com.fuelcast_core.runner;

import fitiuh.com.fuelcast_core.forecast.BacktestService;
import fitiuh.com.fuelcast_core.forecast.PassThroughBacktest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Điểm vào dòng lệnh để đo độ chính xác của mô hình truyền dẫn trên lịch sử.
 *
 * Chạy (không mở web server, app tự thoát khi xong):
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--fuelcast.backtest=true --spring.main.web-application-type=none"
 */
@Component
public class BacktestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BacktestRunner.class);
    private static final String OPTION = "fuelcast.backtest";

    private final BacktestService backtest;

    public BacktestRunner(BacktestService backtest) {
        this.backtest = backtest;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> values = args.getOptionValues(OPTION);
        if (values == null || values.isEmpty() || !"true".equalsIgnoreCase(values.get(0))) {
            return;
        }

        log.info("Backtest walk-forward, đoán Δ giá bán lẻ từ Δ giá thế giới TRUNG BÌNH CÙNG KỲ (horizon 0)");
        log.info(String.format(Locale.ROOT, "%-10s %6s %12s %12s %9s %8s  %s",
                "mặt hàng", "n", "MAE ngây thơ", "MAE mô hình", "cải thiện", "đúng chiều", "kết luận"));

        for (BacktestService.ProductBacktest p : backtest.run()) {
            PassThroughBacktest.Result r = p.result();
            log.info(String.format(Locale.ROOT, "%-10s %6d %12.0f %12.0f %8.0f%% %9.0f%%  %s",
                    p.product(), r.nTest(), r.maeNaive(), r.maeModel(),
                    r.gain() * 100, r.hitRate() * 100,
                    r.beatsNaive() ? "thắng ngây thơ" : "KHÔNG thắng ngây thơ, đừng dùng"));
        }
    }
}
