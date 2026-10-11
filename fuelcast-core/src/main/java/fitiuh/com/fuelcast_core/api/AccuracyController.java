package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.service.ScorecardService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API độ chính xác của mô hình. Mỏng như các controller khác. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccuracyController {

    ScorecardService scorecardService;

    /** Sai số của mô hình so với đường cơ sở "không đổi", theo mặt hàng và loại dự báo. */
    @GetMapping("/accuracy/scorecard")
    public ScorecardDto scorecard() {
        return scorecardService.scorecard();
    }
}
