package fitiuh.com.fuelcast_core.service.parser;

import org.springframework.stereotype.Component;


import org.jsoup.Jsoup;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bóc tách bản tin "điều hành giá xăng dầu" trên moit.gov.vn.
 *
 * Nguyên tắc thiết kế, rút ra từ giai đoạn spike:
 *
 * 1. Bám MẪU CÂU CHỮ, không bám class CSS. MOIT đổi giao diện vài lần mỗi năm
 *    nhưng câu "Xăng E5RON92: không cao hơn 19.191 đồng/lít" thì không đổi.
 *
 * 2. Neo vào TỪ KHOÁ của từng mặt hàng rồi mới quét tới con số, thay vì bắt tên
 *    tự do. Cách bắt tên tự do từng làm mất xăng RON95 ở 66/72 kỳ.
 *
 * 3. Chuẩn hoá Unicode về NFC trước khi so khớp. Trang web trộn lẫn NFC và NFD
 *    (chữ "ề" có thể là 1 hoặc 2 code point) — không chuẩn hoá thì regex trượt
 *    ngẫu nhiên tuỳ bản tin.
 *
 * 4. Trả về product code chuẩn, không phải tên thô: qua các năm bản tin viết
 *    "madút" rồi "mazut", "điêzen" rồi "diesel", "RON95-III" rồi "E10RON95-III".
 */
@Component
public class MoitBulletinParser {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /**
     * Từ khoá nhận diện mặt hàng, theo thứ tự ưu tiên.
     * E5RON92 phải đứng trước RON95 để không bị nuốt nhầm.
     */
    private static final String[][] PRODUCT_KEYWORDS = {
            {"E5RON92",  "E\\s*5\\s*RON\\s*-?\\s*92"},
            {"RON95",    "(?:E\\s*10\\s*)?RON\\s*-?\\s*95"},
            {"DO_005S",  "(?:đi[êe]zen|diezen|diesel|đi-ê-zen)"},
            {"KEROSENE", "[Dd]ầu\\s+h(?:ỏ|o)a"},
            {"FO_180",   "(?:mazut|madút|madut|ma-dút|ma-zut)"},
    };

    /** Ưu tiên: có cụm "không cao hơn" thì gần như chắc chắn là giá bán lẻ. */
    private static final String PRICE_STRICT =
            "(?:(?!Xăng|Dầu|đồng)[^;\\n]){0,80}?không\\s+cao\\s+hơn\\s+([\\d.]{4,9})\\s*đồng\\s*/\\s*(lít|kg)"
          + "(?:[^(\\n]{0,20}\\((tăng|giảm)\\s*([\\d.]+))?";

    /**
     * Dự phòng khi bản tin không dùng cụm đó. Yêu cầu tối thiểu 5 chữ số để
     * không nhặt nhầm mức trích lập Quỹ BOG ("Dầu điêzen: 0 đồng/lít").
     */
    private static final String PRICE_LOOSE =
            "(?:(?!Xăng|Dầu|đồng)[^;\\n]){0,60}?([\\d.]{5,9})\\s*đồng\\s*/\\s*(lít|kg)"
          + "(?:[^(\\n]{0,20}\\((tăng|giảm)\\s*([\\d.]+))?";

    private static final Pattern RE_APPLY = Pattern.compile(
            "Áp\\s+dụng\\s+từ\\s*(\\d{1,2})\\s*giờ\\s*(\\d{1,2})['’]?\\s*ngày\\s*(\\d{1,2})\\s*"
          + "tháng\\s*(\\d{1,2})\\s*năm\\s*(\\d{4})", FLAGS);

    private static final Pattern RE_WORLD = Pattern.compile(
            "([\\d,.]+)\\s*USD/(thùng|tấn)\\s+([^;.]{3,40})", FLAGS);

    private static final LinkedHashMap<String, String> WORLD_SYMBOLS = new LinkedHashMap<>();
    static {
        WORLD_SYMBOLS.put("ron92",   "MOPS_RON92");
        WORLD_SYMBOLS.put("ron 92",  "MOPS_RON92");
        WORLD_SYMBOLS.put("ron95",   "MOPS_RON95");
        WORLD_SYMBOLS.put("ron 95",  "MOPS_RON95");
        WORLD_SYMBOLS.put("điêzen",  "MOPS_GASOIL_005");
        WORLD_SYMBOLS.put("diezen",  "MOPS_GASOIL_005");
        WORLD_SYMBOLS.put("diesel",  "MOPS_GASOIL_005");
        WORLD_SYMBOLS.put("hỏa",     "MOPS_KEROSENE");
        WORLD_SYMBOLS.put("mazut",   "MOPS_FO_180");
        WORLD_SYMBOLS.put("madút",   "MOPS_FO_180");
        WORLD_SYMBOLS.put("madut",   "MOPS_FO_180");
    }

    public ParsedBulletin parse(String html, String sourceUrl) {
        String text = normalize(Jsoup.parse(html).text());

        return new ParsedBulletin(
                sourceUrl,
                findEffectiveAt(text),
                findPrices(text),
                findWorldPrices(text));
    }

    /** NFC + gom khoảng trắng. Bỏ bước này là mất mặt hàng một cách ngẫu nhiên. */
    private static String normalize(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFC).replaceAll("\\s+", " ");
    }

    private static LocalDateTime findEffectiveAt(String text) {
        Matcher m = RE_APPLY.matcher(text);
        if (!m.find()) {
            return null;
        }
        LocalDate day = LocalDate.of(
                Integer.parseInt(m.group(5)), Integer.parseInt(m.group(4)),
                Integer.parseInt(m.group(3)));
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));

        // "24 giờ 00" là nửa đêm CUỐI ngày, tức 00:00 ngày kế tiếp. LocalDateTime
        // không có giờ 24 nên phải đổi tay. Chỉ chấp nhận đúng 24:00: giờ 24
        // phút 30 hay giờ 25 vẫn phải lỗi, kẻo giờ rác lặng lẽ thành một ngày khác.
        if (hour == 24 && minute == 0) {
            return day.plusDays(1).atStartOfDay();
        }
        return day.atTime(hour, minute);
    }

    private static List<ParsedBulletin.ParsedPrice> findPrices(String text) {
        List<ParsedBulletin.ParsedPrice> prices = new ArrayList<>();
        for (String[] kw : PRODUCT_KEYWORDS) {
            ParsedBulletin.ParsedPrice price = findPrice(text, kw[0], kw[1]);
            if (price != null) {
                prices.add(price);
            }
        }
        return prices;
    }

    private static ParsedBulletin.ParsedPrice findPrice(String text, String code, String keyword) {
        for (String tail : new String[]{PRICE_STRICT, PRICE_LOOSE}) {
            Matcher m = Pattern.compile(keyword + tail, FLAGS).matcher(text);
            if (!m.find()) {
                continue;
            }
            long price = parseVnd(m.group(1));
            if (price < 1000) {          // chắc chắn không phải giá bán lẻ
                continue;
            }
            Long delta = null;
            if (m.group(4) != null) {
                long d = parseVnd(m.group(4));
                delta = "giảm".equalsIgnoreCase(m.group(3)) ? -d : d;
            }
            return new ParsedBulletin.ParsedPrice(
                    code, price, m.group(2).toLowerCase(Locale.ROOT), delta);
        }
        return null;
    }

    private static List<ParsedBulletin.ParsedWorldPrice> findWorldPrices(String text) {
        List<ParsedBulletin.ParsedWorldPrice> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher m = RE_WORLD.matcher(text);
        while (m.find()) {
            String symbol = toSymbol(m.group(3));
            if (symbol == null || !seen.add(symbol)) {
                continue;
            }
            out.add(new ParsedBulletin.ParsedWorldPrice(
                    symbol, parseUsd(m.group(1)), "USD/" + m.group(2).toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    private static String toSymbol(String raw) {
        String low = normalize(raw).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : WORLD_SYMBOLS.entrySet()) {
            if (low.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** "19.191" -> 19191. Dấu chấm là phân nhóm nghìn, không phải thập phân. */
    static long parseVnd(String s) {
        return Long.parseLong(s.replace(".", ""));
    }

    /** "94,948" -> 94.948. Bản tin dùng dấu phẩy làm dấu thập phân. */
    static BigDecimal parseUsd(String s) {
        return new BigDecimal(s.replace(".", "").replace(",", "."));
    }
}
