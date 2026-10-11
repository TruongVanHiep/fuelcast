package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.api.CurrentPriceDto;
import fitiuh.com.fuelcast_core.api.PriceHistoryDto;
import fitiuh.com.fuelcast_core.api.PricePointDto;
import fitiuh.com.fuelcast_core.entity.FuelProduct;
import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Phía đọc: trả giá cho API. Không ghi gì xuống DB. */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PriceQueryService {

    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    RetailPriceRepository prices;
    FuelProductRepository products;
    Clock clock;

    /**
     * Giá mới nhất của từng mặt hàng ở vùng 1, theo thứ tự hiển thị.
     *
     * Mặt hàng nào đã ngừng được công bố (dầu hỏa sau 27/3/2026) vẫn có mặt, với
     * effectiveAt cũ. Cố ý không lọc ở đây: ngưỡng "bao lâu thì coi là cũ" là
     * quyết định của sản phẩm, và client có đủ thông tin để tự phán đoán.
     */
    @Transactional(readOnly = true)
    public List<CurrentPriceDto> currentPrices() {
        // Chỉ 5 mặt hàng, nên nạp cả bảng một lần rẻ hơn một câu JOIN.
        Map<Short, FuelProduct> productById = products.findAll().stream()
                .collect(Collectors.toMap(FuelProduct::getId, Function.identity()));

        return prices.findLatestPerProduct().stream()
                .map(price -> new Row(price, productOf(price, productById)))
                .sorted(Comparator.comparing(row -> row.product().getDisplayOrder()))
                .map(row -> toDto(row.price(), row.product()))
                .toList();
    }

    /**
     * Chuỗi giá của một mặt hàng trong [from, to] (cả hai đầu tính theo NGÀY giờ
     * Việt Nam). Bỏ trống to thì lấy hôm nay, bỏ trống from thì lấy một năm trước to.
     */
    @Transactional(readOnly = true)
    public PriceHistoryDto history(String productCode, LocalDate from, LocalDate to) {
        // Không phân biệt hoa thường: "ron95" cũng là RON95.
        String code = productCode.strip().toUpperCase(Locale.ROOT);
        FuelProduct product = products.findByCode(code)
                .orElseThrow(() -> new ProductNotFoundException(productCode));

        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusYears(1);
        if (start.isAfter(end)) {
            throw new InvalidDateRangeException(start, end);
        }

        // "Đến hết ngày end" = trước 00:00 ngày end+1. Dùng "<= end 00:00" sẽ làm
        // rơi kỳ công bố 15:00 của chính ngày end.
        OffsetDateTime fromInclusive = start.atStartOfDay(VN).toOffsetDateTime();
        OffsetDateTime toExclusive = end.plusDays(1).atStartOfDay(VN).toOffsetDateTime();

        List<PricePointDto> points = prices.findHistory(product.getId(), fromInclusive, toExclusive)
                .stream()
                .map(p -> new PricePointDto(
                        p.getId().getObservedAt(),
                        p.getPriceVnd().longValueExact(),
                        p.getDeltaVnd() == null ? null : p.getDeltaVnd().longValueExact()))
                .toList();

        return new PriceHistoryDto(product.getCode(), product.getNameVi(), product.getUnit(),
                (short) 1, start, end, points);
    }

    private record Row(RetailPrice price, FuelProduct product) { }

    /**
     * Một dòng giá trỏ tới mặt hàng không tồn tại là dữ liệu hỏng (khoá ngoại
     * đáng ra đã chặn). Báo lỗi to thay vì bỏ dòng đi lặng lẽ.
     */
    private static FuelProduct productOf(RetailPrice price, Map<Short, FuelProduct> productById) {
        FuelProduct product = productById.get(price.getId().getProductId());
        if (product == null) {
            throw new IllegalStateException("Giá trỏ tới productId="
                    + price.getId().getProductId() + " không có trong fuel_product");
        }
        return product;
    }

    /**
     * longValueExact chứ không phải longValue: nếu MOIT một ngày công bố giá có
     * phần lẻ, ta nhận lỗi ngay thay vì lặng lẽ cắt mất phần thập phân.
     */
    private static CurrentPriceDto toDto(RetailPrice price, FuelProduct product) {
        return new CurrentPriceDto(
                product.getCode(),
                product.getNameVi(),
                product.getUnit(),
                price.getId().getRegion(),
                price.getPriceVnd().longValueExact(),
                price.getDeltaVnd() == null ? null : price.getDeltaVnd().longValueExact(),
                price.getId().getObservedAt());
    }
}
