package fitiuh.com.fuelcast_core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Khoá tự nhiên của retail_price. Không dùng id tăng tự động, vì khoá này
 * chính là thứ làm cho việc nạp lại dữ liệu trở nên idempotent: crawl cùng
 * một bản tin mười lần vẫn chỉ ra một dòng.
 */
@Embeddable
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(force = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RetailPriceId implements Serializable {

    @Column(name = "observed_at", nullable = false)
    OffsetDateTime observedAt;

    @Column(name = "product_id", nullable = false)
    Short productId;

    @Column(name = "publisher_id", nullable = false)
    Short publisherId;

    /** 1 = vùng 1 (giá công bố), 2 = vùng 2 (được cộng tối đa 2%). */
    @Column(nullable = false)
    Short region;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RetailPriceId other)) return false;
        return Objects.equals(observedAt, other.observedAt)
                && Objects.equals(productId, other.productId)
                && Objects.equals(publisherId, other.publisherId)
                && Objects.equals(region, other.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(observedAt, productId, publisherId, region);
    }
}
