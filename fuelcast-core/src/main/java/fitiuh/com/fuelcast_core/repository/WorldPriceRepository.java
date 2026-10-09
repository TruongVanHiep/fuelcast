package fitiuh.com.fuelcast_core.repository;

import fitiuh.com.fuelcast_core.entity.WorldPrice;
import fitiuh.com.fuelcast_core.entity.WorldPriceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldPriceRepository extends JpaRepository<WorldPrice, WorldPriceId> {
}
