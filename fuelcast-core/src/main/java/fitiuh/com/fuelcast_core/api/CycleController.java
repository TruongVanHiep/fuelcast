package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.service.CycleQueryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API kỳ điều hành giá. Mỏng như PriceController: mọi quyết định nằm ở service. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CycleController {

    CycleQueryService cycleQueryService;

    /** Lần công bố gần nhất, lần kế tiếp dự kiến và đồng hồ đếm ngược. */
    @GetMapping("/cycles/current")
    public CurrentCycleDto current() {
        return cycleQueryService.currentCycle();
    }
}
