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

    // ───────────────────────────── helpers ─────────────────────────────

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
