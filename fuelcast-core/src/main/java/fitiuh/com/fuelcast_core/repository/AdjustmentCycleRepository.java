package fitiuh.com.fuelcast_core.repository;

import fitiuh.com.fuelcast_core.entity.AdjustmentCycle;
import fitiuh.com.fuelcast_core.entity.CycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AdjustmentCycleRepository extends JpaRepository<AdjustmentCycle, Long> {
    Optional<AdjustmentCycle> findByCycleStartAndCycleEnd(LocalDate cycleStart, LocalDate cycleEnd);
    Optional<AdjustmentCycle> findFirstByStatusOrderByCycleEndDesc(CycleStatus status);
}
