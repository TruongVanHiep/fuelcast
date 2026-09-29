package fitiuh.com.fuelcast_core.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@Table(name = "price_publisher")
@NoArgsConstructor(force = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PricePublisher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Short id;

    @Column(nullable = false, unique = true, length = 30)
    String code;

    @Column(name = "name_vi", nullable = false, length = 150)
    String nameVi;

    @Column(nullable = false, length = 20)
    String type;

    @Column(length = 255)
    String website;
}
