package fitiuh.com.fuelcast_core.repository;

import fitiuh.com.fuelcast_core.entity.PricePublisher;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricePublisherRepository extends JpaRepository<PricePublisher, Short> {

    Optional<PricePublisher> findByCode(String code);
}
