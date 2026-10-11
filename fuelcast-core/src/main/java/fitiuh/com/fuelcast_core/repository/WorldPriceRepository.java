package fitiuh.com.fuelcast_core.repository;

import fitiuh.com.fuelcast_core.entity.WorldPrice;
import fitiuh.com.fuelcast_core.entity.WorldPriceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldPriceRepository extends JpaRepository<WorldPrice, WorldPriceId> {

    /** Chuỗi một mã giá thế giới, thời gian tăng dần. */
    List<WorldPrice> findByIdSymbolOrderByIdObservedAtAsc(String symbol);
}
