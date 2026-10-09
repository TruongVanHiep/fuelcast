package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.entity.*;
import fitiuh.com.fuelcast_core.repository.FuelProductRepository;
import fitiuh.com.fuelcast_core.repository.PricePublisherRepository;
import fitiuh.com.fuelcast_core.repository.RetailPriceRepository;
import fitiuh.com.fuelcast_core.service.parser.ParsedBulletin;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Nạp dữ liệu giá lịch sử từ CSV của giai đoạn spike.
 *
 * Chạy lại bao nhiêu lần cũng được: khoá tự nhiên của retail_price và ràng
 * buộc duy nhất trên adjustment_cycle khiến lần chạy thứ hai không tạo thêm gì.
 */

@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class PriceImportService {

    private static final Logger log = LoggerFactory.getLogger(PriceImportService.class);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PUBLISHER_CODE = "MOIT";
    private static final short REGION_1 = 1;

    FuelProductRepository products;
    PricePublisherRepository publishers;
    RetailPriceRepository prices;
    AdjustmentCycleService cycleService;
    ProductCodeResolver resolver;

    /** Kết quả một lần nạp, để người gọi báo cáo lại. */
    public record ImportResult(int inserted, int skippedExisting, int unrecognised,
                               Set<String> unknownNames, long totalCycles) { }

    @Transactional
    public ImportResult importCsv(Path csv) throws java.io.IOException {
        Map<String, FuelProduct> productByCode = new HashMap<>();
        products.findAll().forEach(p -> productByCode.put(p.getCode(), p));

        PricePublisher moit = requireMoit();

        int inserted = 0;
        int skippedExisting = 0;
        int unrecognised = 0;
        Set<String> unknownNames = new TreeSet<>();

        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {          // bỏ dòng header
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] f = splitCsv(line);
            if (f.length < 5) {
                continue;
            }

            String code = resolver.resolve(f[1]);
            if (code == null || !productByCode.containsKey(code)) {
                unrecognised++;
                unknownNames.add(f[1]);
                continue;
            }

            OffsetDateTime observedAt = LocalDateTime.parse(f[0]).atZone(VN).toOffsetDateTime();
            AdjustmentCycle cycle = cycleService.findOrCreateAdjustmentCycle(observedAt);

            boolean isNew = insertIfAbsent(observedAt, productByCode.get(code), moit,
                    new BigDecimal(f[2]),
                    f[4].isBlank() ? null : new BigDecimal(f[4]),
                    cycle);
            if (isNew) {
                inserted++;
            } else {
                skippedExisting++;
            }
        }

        log.info("Nạp CSV xong: {} dòng mới, {} dòng đã có, {} dòng không nhận ra mặt hàng",
                inserted, skippedExisting, unrecognised);

        return new ImportResult(inserted, skippedExisting, unrecognised,
                unknownNames, cycleService.count());
    }

    /**
     * Ghi giá của một bản tin đã parse. Cùng khoá tự nhiên với đường nạp CSV nên
     * một kỳ đã có từ CSV sẽ được bỏ qua chứ không nhân đôi.
     *
     * @Transactional bao cả bản tin: lỗi ở mặt hàng thứ ba thì hai mặt hàng đầu
     * cũng bị huỷ, để không bao giờ có một kỳ giá chỉ ghi được một nửa.
     */
    @Transactional
    public ImportResult importBulletin(ParsedBulletin bulletin) {
        if (!bulletin.isUsable()) {
            throw new IllegalArgumentException(
                    "Bản tin không dùng được: thiếu ngày hiệu lực hoặc không có giá nào");
        }

        PricePublisher moit = requireMoit();
        OffsetDateTime observedAt = bulletin.effectiveAt().atZone(VN).toOffsetDateTime();
        AdjustmentCycle cycle = cycleService.findOrCreateAdjustmentCycle(observedAt);

        int inserted = 0;
        int skippedExisting = 0;
        int unrecognised = 0;
        Set<String> unknownCodes = new TreeSet<>();

        for (ParsedBulletin.ParsedPrice p : bulletin.prices()) {
            Optional<FuelProduct> product = products.findByCode(p.productCode());
            if (product.isEmpty()) {
                unrecognised++;
                unknownCodes.add(p.productCode());
                continue;
            }
            BigDecimal delta = p.deltaVnd() == null ? null : BigDecimal.valueOf(p.deltaVnd());
            if (insertIfAbsent(observedAt, product.get(), moit,
                    BigDecimal.valueOf(p.priceVnd()), delta, cycle)) {
                inserted++;
            } else {
                skippedExisting++;
            }
        }

        return new ImportResult(inserted, skippedExisting, unrecognised,
                unknownCodes, cycleService.count());
    }

    /** Trả true nếu đã ghi một dòng mới, false nếu khoá tự nhiên đã tồn tại. */
    private boolean insertIfAbsent(OffsetDateTime observedAt, FuelProduct product,
                                   PricePublisher publisher, BigDecimal priceVnd,
                                   BigDecimal deltaVnd, AdjustmentCycle cycle) {
        RetailPriceId id = new RetailPriceId(observedAt, product.getId(), publisher.getId(), REGION_1);
        if (prices.existsById(id)) {
            return false;
        }
        prices.save(new RetailPrice(id, priceVnd, deltaVnd, cycle.getId()));
        return true;
    }

    private PricePublisher requireMoit() {
        return publishers.findByCode(PUBLISHER_CODE).orElseThrow(
                () -> new IllegalStateException("Thiếu price_publisher '" + PUBLISHER_CODE
                        + "' — migration V2 đã chạy chưa?"));
    }

    /** CSV ở đây đơn giản: chỉ cần xử lý trường bọc trong dấu ngoặc kép. */
    static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(cur.toString().strip());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().strip());
        return out.toArray(new String[0]);
    }
}
