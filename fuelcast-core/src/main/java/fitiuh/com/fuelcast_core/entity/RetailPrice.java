package fitiuh.com.fuelcast_core.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/** Một dòng giá bán lẻ đã công bố, cho một mặt hàng ở một vùng trong một kỳ. */
@Entity
@Table(name = "retail_price")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(force = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RetailPrice {

    @EmbeddedId
    RetailPriceId id;

    @Column(name = "price_vnd", nullable = false)
    BigDecimal priceVnd;

    /** Chênh lệch so với kỳ trước; âm là giảm. Null khi bản tin không nêu. */
    @Column(name = "delta_vnd")
    BigDecimal deltaVnd;

    @Column(name = "cycle_id")
    Long cycleId;

//    protected RetailPrice(RetailPriceId id, BigDecimal priceVnd, BigDecimal deltaVnd, Long cycleId) {
//        this.id = id;
//        this.priceVnd = priceVnd;
//        this.deltaVnd = deltaVnd;
//        this.cycleId = cycleId;
//    }
}
