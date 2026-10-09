package fitiuh.com.fuelcast_core.service.parser;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Kết quả bóc tách một bản tin điều hành giá của Bộ Công Thương. */
public record ParsedBulletin(
        String sourceUrl,
        LocalDateTime effectiveAt,
        List<ParsedPrice> prices,
        List<ParsedWorldPrice> worldPrices
) {

    public boolean isUsable() {
        return effectiveAt != null && !prices.isEmpty();
    }

    /** productCode khớp với fuel_product.code. deltaVnd âm nghĩa là giảm. */
    public record ParsedPrice(String productCode, long priceVnd, String unit, Long deltaVnd) { }

    /**
     * Giá thành phẩm thế giới BÌNH QUÂN của kỳ điều hành vừa qua, không phải giá
     * chốt một ngày. valueUsd là BigDecimal để khỏi đi qua double.
     */
    public record ParsedWorldPrice(String symbol, BigDecimal valueUsd, String unit) { }
}
