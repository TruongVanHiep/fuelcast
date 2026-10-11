package fitiuh.com.fuelcast_core.api;

import java.time.OffsetDateTime;

/** Một điểm trong chuỗi giá của một mặt hàng. Quy ước kiểu giống CurrentPriceDto. */
public record PricePointDto(OffsetDateTime effectiveAt, long priceVnd, Long deltaVnd) { }
