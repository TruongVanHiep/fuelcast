package fitiuh.com.fuelcast_core.service;

import fitiuh.com.fuelcast_core.entity.AdjustmentCycle;
import fitiuh.com.fuelcast_core.entity.CycleStatus;
import fitiuh.com.fuelcast_core.repository.AdjustmentCycleRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

/** Nghiệp vụ quanh kỳ điều hành giá. */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdjustmentCycleService {

    AdjustmentCycleRepository adjustmentCycleRepository;

    /** Kỳ điều hành dài 7 ngày, công bố chiều thứ Năm. */
    static final int CYCLE_LENGTH_DAYS = 7;

    /**
     * Lấy kỳ ứng với một thời điểm công bố, tạo mới nếu chưa có.
     * Idempotent: gọi nhiều lần với cùng thời điểm chỉ sinh một kỳ.
     */
    @Transactional
    public AdjustmentCycle findOrCreateAdjustmentCycle(OffsetDateTime announcedAt) {
        LocalDate cycleEnd =  announcedAt.toLocalDate();
        LocalDate cycleStart = cycleEnd.minusDays(CYCLE_LENGTH_DAYS);

        return adjustmentCycleRepository.findByCycleStartAndCycleEnd(cycleStart, cycleEnd).orElseGet(() -> {
            AdjustmentCycle cycle = AdjustmentCycle.builder()
                    .cycleStart(cycleStart)
                    .cycleEnd(cycleEnd)
                    .status(CycleStatus.ANNOUNCED)
                    .build();
            cycle.setAnnouncedAt(announcedAt);
            cycle.setEffectiveFrom(announcedAt);
            return adjustmentCycleRepository.save(cycle);
        });
    }

    /**
     * Tìm kỳ điều hành mới nhất đã được công bố.
     */
    @Transactional
    public Optional<AdjustmentCycle> findLatestAnnounced() {
        return adjustmentCycleRepository.findFirstByStatusOrderByCycleEndDesc(CycleStatus.ANNOUNCED);
    }

    @Transactional
    public long count(){
        return adjustmentCycleRepository.count();
    }

}
