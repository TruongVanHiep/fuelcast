package fitiuh.com.fuelcast_core.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@Table(name = "fuel_product")
@NoArgsConstructor(force = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FuelProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Short id;

    /** E5RON92 | RON95 | DO_005S | KEROSENE | FO_180 — khớp với MoitBulletinParser. */
    @Column( nullable = false,unique = true, length = 20)
    String code;

    @Column(name = "name_vi", nullable = false, length = 100)
    String nameVi;

    /** lít hoặc kg. Dầu madút bán theo kg, phần còn lại theo lít. */
    @Column(nullable = false, length = 10)
    String unit;

    /** Xăng thì chịu thuế tiêu thụ đặc biệt, dầu thì không. */
    @Column(name = "is_gasoline", nullable = false)
    boolean gasoline;

    @Column(name = "display_order", nullable = false)
    Short displayOrder;
}
