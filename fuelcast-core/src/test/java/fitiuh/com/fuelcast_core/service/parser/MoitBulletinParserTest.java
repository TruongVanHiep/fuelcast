package fitiuh.com.fuelcast_core.service.parser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mỗi fixture là một bản tin MOIT thật, được chọn vì nó canh một giả định khác
 * của parser. Không dùng Spring: parser là hàm thuần, HTML vào, record ra.
 */
class MoitBulletinParserTest {

    private final MoitBulletinParser parser = new MoitBulletinParser();

    /**
     * Bản tin 11/1/2024 trộn NFC và NFD ngay trong một trang: chữ "đồng" của
     * E5RON92 là một code point, của RON95-III là "ô" + dấu huyền rời. Bỏ bước
     * chuẩn hoá NFC trong parser thì RON95 biến mất và test này đỏ.
     */
    @Test
    void parses2024BulletinWithMixedUnicodeForms() throws IOException {
        ParsedBulletin bulletin = parse("moit-2024-01-11.html");

        assertThat(bulletin.isUsable()).isTrue();
        assertThat(bulletin.effectiveAt()).isEqualTo(LocalDateTime.of(2024, 1, 11, 15, 0));
        assertThat(bulletin.prices()).hasSize(5);

        assertPrice(bulletin, "E5RON92",  21041, "lít", 35);
        assertPrice(bulletin, "RON95",    21935, "lít", 19);
        assertPrice(bulletin, "DO_005S",  19707, "lít", 339);
        assertPrice(bulletin, "KEROSENE", 20331, "lít", 374);
        assertPrice(bulletin, "FO_180",   15815, "kg",  320);
    }

    /**
     * Một bản tin có cả mặt hàng tăng lẫn giảm: dấu của delta phải được đọc
     * riêng cho từng mặt hàng, không áp một dấu cho cả bản tin.
     */
    @Test
    void readsDeltaSignPerProductWhenBulletinMixesRisesAndFalls() throws IOException {
        ParsedBulletin bulletin = parse("moit-2025-01-23.html");

        assertThat(bulletin.effectiveAt()).isEqualTo(LocalDateTime.of(2025, 1, 23, 15, 0));

        assertPrice(bulletin, "E5RON92",  20592, "lít", -158);
        assertPrice(bulletin, "RON95",    21142, "lít", -78);
        assertPrice(bulletin, "DO_005S",  20194, "lít", 412);
        assertPrice(bulletin, "KEROSENE", 20110, "lít", 404);
        assertPrice(bulletin, "FO_180",   17752, "kg",  571);
    }

    /** Từ giữa 2025 bản tin viết "mazut" thay cho "madút" nhưng vẫn là FO_180. */
    @Test
    void recognisesMazutSpellingAsFuelOil() throws IOException {
        ParsedBulletin bulletin = parse("moit-2025-06-19.html");

        assertThat(bulletin.effectiveAt()).isEqualTo(LocalDateTime.of(2025, 6, 19, 15, 0));
        assertThat(bulletin.prices()).hasSize(5);

        assertPrice(bulletin, "E5RON92",  20631, "lít", 1169);
        assertPrice(bulletin, "RON95",    21244, "lít", 1277);
        assertPrice(bulletin, "DO_005S",  19156, "lít", 1456);
        assertPrice(bulletin, "KEROSENE", 18923, "lít", 1412);
        assertPrice(bulletin, "FO_180",   17643, "kg",  1182);
    }

    /**
     * Năm 2026 xăng RON95-III được thay bằng E10RON95-III (vẫn là mã RON95) và
     * dầu hỏa biến mất khỏi bản tin. Parser phải trả đúng 4 mặt hàng, không bịa
     * ra KEROSENE để đủ 5.
     */
    @Test
    void parses2026BulletinWithE10AndNoKerosene() throws IOException {
        ParsedBulletin bulletin = parse("moit-2026-09-24.html");

        assertThat(bulletin.effectiveAt()).isEqualTo(LocalDateTime.of(2026, 9, 24, 15, 0));
        assertThat(bulletin.prices()).hasSize(4);
        assertThat(bulletin.prices())
                .extracting(ParsedBulletin.ParsedPrice::productCode)
                .doesNotContain("KEROSENE");

        assertPrice(bulletin, "E5RON92", 26397, "lít", 1258);
        assertPrice(bulletin, "RON95",   27087, "lít", 1451);
        assertPrice(bulletin, "DO_005S", 30497, "lít", 552);
        assertPrice(bulletin, "FO_180",  19472, "kg",  276);
    }

    /**
     * Bản tin 26/3/2026 ghi "Áp dụng từ 24 giờ 00' ngày 26 tháng 3", tức nửa đêm
     * cuối ngày 26 = 00:00 ngày 27. LocalDateTime.of(..., 24, 0) ném
     * DateTimeException và làm hỏng cả bản tin. Kết quả phải trùng khoá
     * 2026-03-27T00:00 mà dữ liệu spike đã ghi, để nạp lại không bị nhân đôi.
     */
    @Test
    void readsMidnightEffectiveTimeAsStartOfNextDay() throws IOException {
        ParsedBulletin bulletin = parse("moit-2026-03-26.html");

        assertThat(bulletin.isUsable()).isTrue();
        assertThat(bulletin.effectiveAt()).isEqualTo(LocalDateTime.of(2026, 3, 27, 0, 0));
        assertThat(bulletin.prices()).hasSize(5);

        assertPrice(bulletin, "E5RON92",  23326, "lít", -4749);
        assertPrice(bulletin, "RON95",    24332, "lít", -5625);
        assertPrice(bulletin, "DO_005S",  35440, "lít", -2459);
        assertPrice(bulletin, "KEROSENE", 35384, "lít", -971);
        assertPrice(bulletin, "FO_180",   21748, "kg",  1503);
    }

    /** Chỉ 24:00 được hiểu là nửa đêm; giờ rác không được lặng lẽ thành ngày khác. */
    @Test
    void stillRejectsImpossibleTimesOtherThanMidnight() {
        for (String time : new String[]{"24 giờ 30", "25 giờ 00"}) {
            String html = "<html><body>Áp dụng từ " + time + "’ ngày 26 tháng 3 năm 2026.</body></html>";

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> parser.parse(html, "x"))
                    .as(time)
                    .isInstanceOf(java.time.DateTimeException.class);
        }
    }

    /** Các số này được đối chiếu tay với câu "Bình quân giá thành phẩm..." trong bản tin. */
    @Test
    void readsWorldPricesOf2024Bulletin() throws IOException {
        ParsedBulletin bulletin = parse("moit-2024-01-11.html");

        assertThat(bulletin.worldPrices()).hasSize(5);
        assertWorldPrice(bulletin, "MOPS_RON92",      "87.136",  "USD/thùng");
        assertWorldPrice(bulletin, "MOPS_RON95",      "91.356",  "USD/thùng");
        assertWorldPrice(bulletin, "MOPS_KEROSENE",   "100.748", "USD/thùng");
        assertWorldPrice(bulletin, "MOPS_GASOIL_005", "98.486",  "USD/thùng");
        assertWorldPrice(bulletin, "MOPS_FO_180",     "447.346", "USD/tấn");
    }

    /** Mazut tính theo tấn, còn lại theo thùng: đơn vị phải đi theo từng mặt hàng. */
    @Test
    void readsWorldPricesOf2026BulletinWithoutKerosene() throws IOException {
        ParsedBulletin bulletin = parse("moit-2026-09-24.html");

        assertThat(bulletin.worldPrices())
                .extracting(ParsedBulletin.ParsedWorldPrice::symbol)
                .containsExactlyInAnyOrder("MOPS_RON92", "MOPS_RON95", "MOPS_GASOIL_005", "MOPS_FO_180");
        assertWorldPrice(bulletin, "MOPS_RON92",      "140.834", "USD/thùng");
        assertWorldPrice(bulletin, "MOPS_GASOIL_005", "173.328", "USD/thùng");
        assertWorldPrice(bulletin, "MOPS_FO_180",     "657.618", "USD/tấn");
    }

    /**
     * Bản tin viết "131,050" (dấu phẩy là thập phân). So bằng compareTo vì
     * 131.05 và 131.050 là cùng một giá trị nhưng khác scale.
     */
    @Test
    void readsCommaAsDecimalSeparatorInWorldPrices() throws IOException {
        ParsedBulletin bulletin = parse("moit-2026-03-26.html");

        assertWorldPrice(bulletin, "MOPS_RON92", "131.050", "USD/thùng");
        assertWorldPrice(bulletin, "MOPS_GASOIL_005", "204.620", "USD/thùng");
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static void assertWorldPrice(ParsedBulletin bulletin, String symbol,
                                         String valueUsd, String unit) {
        ParsedBulletin.ParsedWorldPrice w = bulletin.worldPrices().stream()
                .filter(x -> symbol.equals(x.symbol()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("không tìm thấy giá thế giới " + symbol));

        assertThat(w.valueUsd()).as("giá %s", symbol).isEqualByComparingTo(valueUsd);
        assertThat(w.unit()).as("đơn vị %s", symbol).isEqualTo(unit);
    }

    private ParsedBulletin parse(String fixture) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + fixture)) {
            assertThat(in).as("thiếu fixture %s", fixture).isNotNull();
            // UTF-8 tường minh: máy Windows mặc định Cp1258 sẽ làm hỏng tiếng Việt.
            return parser.parse(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    "https://moit.gov.vn/" + fixture);
        }
    }

    private static void assertPrice(ParsedBulletin bulletin, String code,
                                    long priceVnd, String unit, long deltaVnd) {
        ParsedBulletin.ParsedPrice p = bulletin.prices().stream()
                .filter(x -> code.equals(x.productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("không tìm thấy mặt hàng " + code));

        assertThat(p.priceVnd()).as("giá %s", code).isEqualTo(priceVnd);
        assertThat(p.unit()).as("đơn vị %s", code).isEqualTo(unit);
        assertThat(p.deltaVnd()).as("delta %s", code).isEqualTo(deltaVnd);
    }
}
