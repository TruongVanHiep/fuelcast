package fitiuh.com.fuelcast_core.forecast;

import java.util.Map;
import java.util.Optional;

/**
 * Mặt hàng bán lẻ nào đi theo mã giá thế giới nào. Mã bên phải trùng với mã mà
 * MoitBulletinParser ghi vào world_price.
 *
 * Lưu ý đơn vị: mazut tính theo USD/TẤN, các mặt hàng còn lại theo USD/THÙNG, nên
 * hệ số truyền dẫn của mazut không so sánh trực tiếp với các mặt hàng khác.
 */
final class WorldSymbols {

    private static final Map<String, String> BY_PRODUCT = Map.of(
            "E5RON92", "MOPS_RON92",
            "RON95", "MOPS_RON95",
            "DO_005S", "MOPS_GASOIL_005",
            "KEROSENE", "MOPS_KEROSENE",
            "FO_180", "MOPS_FO_180");

    private WorldSymbols() { }

    static Optional<String> forProduct(String productCode) {
        return Optional.ofNullable(BY_PRODUCT.get(productCode));
    }
}
