package fitiuh.com.fuelcast_core.service;

/** Mã mặt hàng không có trong fuel_product. Tầng API đổi thành HTTP 404. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String code) {
        super("Không có mặt hàng nào với mã '" + code + "'");
    }
}
