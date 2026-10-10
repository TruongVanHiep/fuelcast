package fitiuh.com.fuelcast_core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * Một điểm giá thế giới. Cột source cho biết điểm đó LÀ GÌ, và đừng bao giờ bỏ
 * qua nó khi đọc: MOIT_CYCLE_AVG là trung bình của một kỳ điều hành (thường 7
 * ngày, có kỳ chỉ 1 ngày), còn giá đóng cửa hằng ngày của Brent/RBOB sau này sẽ
 * mang source khác. Trộn hai loại vào một chuỗi sẽ làm sai mô hình dự báo.
 */
@Entity
@Table(name = "world_price")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(force = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorldPrice {

    @EmbeddedId
    WorldPriceId id;

    @Column(name = "price_usd", nullable = false)
    BigDecimal priceUsd;

    /** USD/thùng hoặc USD/tấn (mazut tính theo tấn). */
    @Column(nullable = false, length = 12)
    String unit;

    @Column(nullable = false, length = 40)
    String source;
}
