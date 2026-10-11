package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.service.PriceQueryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API đọc giá. Controller chỉ nhận request, gọi service, trả DTO: mọi quyết định
 * nghiệp vụ (sắp xếp, đổi kiểu tiền, mặt hàng ngừng công bố) nằm ở service.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PriceController {

    PriceQueryService priceQueryService;

    /** Giá bán lẻ hiện hành của từng mặt hàng ở vùng 1. */
    @GetMapping("/prices/current")
    public List<CurrentPriceDto> current() {
        return priceQueryService.currentPrices();
    }
}
