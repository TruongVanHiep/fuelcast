package fitiuh.com.fuelcast_core.repository;

import fitiuh.com.fuelcast_core.entity.FuelProduct;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FuelProductRepository extends JpaRepository<FuelProduct, Short> {

    Optional<FuelProduct> findByCode(String code);
}
