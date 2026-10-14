package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.service.ForecastQueryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API dự báo. Mỏng như các controller khác. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ForecastController {

    ForecastQueryService forecastQueryService;

    /** Dự báo LIVE cho kỳ sắp tới, mỗi mặt hàng một dòng. Rỗng nếu chưa có. */
    @GetMapping("/forecasts/next")
    public NextForecastsDto next() {
        return forecastQueryService.next();
    }
}
