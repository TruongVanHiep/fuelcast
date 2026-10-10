package fitiuh.com.fuelcast_core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Khoá tự nhiên của world_price: (thời điểm, mã giá). Cùng lý do với
 * RetailPriceId: khoá này làm cho việc nạp lại bản tin trở nên idempotent.
 */
@Embeddable
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(force = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorldPriceId implements Serializable {

    @Column(name = "observed_at", nullable = false)
    OffsetDateTime observedAt;

    /** Ví dụ MOPS_RON95, MOPS_GASOIL_005 — khớp MoitBulletinParser. */
    @Column(nullable = false, length = 30)
    String symbol;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorldPriceId other)) return false;
        return Objects.equals(observedAt, other.observedAt)
                && Objects.equals(symbol, other.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(observedAt, symbol);
    }
}
