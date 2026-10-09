package fitiuh.com.fuelcast_core.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Đưa tên mặt hàng thô về product code chuẩn.
 *
 * Cần thiết vì CSV sinh từ giai đoạn spike còn lưu tên như bản tin viết, mà
 * cách viết đổi qua các năm: "Dầu madút" / "Dầu mazut", "Dầu điêzen 0.05S" /
 * "Dầu diesel 0.05S", "Xăng RON95-III" / "Xăng E10RON95-III".
 */
@Component
public class ProductCodeResolver {

    private static final Map<String, String> KEYWORD_TO_CODE = new LinkedHashMap<>();
    static {
        // thứ tự quan trọng: e5ron92 trước ron95
        KEYWORD_TO_CODE.put("e5ron92",  "E5RON92");
        KEYWORD_TO_CODE.put("e5 ron92", "E5RON92");
        KEYWORD_TO_CODE.put("ron95",    "RON95");
        KEYWORD_TO_CODE.put("ron 95",   "RON95");
        KEYWORD_TO_CODE.put("điêzen",   "DO_005S");
        KEYWORD_TO_CODE.put("diezen",   "DO_005S");
        KEYWORD_TO_CODE.put("diesel",   "DO_005S");
        KEYWORD_TO_CODE.put("do_005s",  "DO_005S");
        KEYWORD_TO_CODE.put("hỏa",      "KEROSENE");
        KEYWORD_TO_CODE.put("kerosene", "KEROSENE");
        KEYWORD_TO_CODE.put("madút",    "FO_180");
        KEYWORD_TO_CODE.put("madut",    "FO_180");
        KEYWORD_TO_CODE.put("mazut",    "FO_180");
        KEYWORD_TO_CODE.put("fo_180",   "FO_180");
    }

    /** Trả null khi không nhận ra — người gọi tự quyết bỏ qua hay báo lỗi. */
    public String resolve(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String s = Normalizer.normalize(rawName, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT).trim();
        for (Map.Entry<String, String> e : KEYWORD_TO_CODE.entrySet()) {
            if (s.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }
}
