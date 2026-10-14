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

        return adjustmentCycleRepository.findByCycleStartAndCycleEnd(cycleStart, cycleEnd).map(existing -> {
            // Kỳ OPEN được tạo trước để dự báo LIVE có chỗ trỏ tới. Khi bản tin thật
            // tới đúng ngày dự kiến thì thăng cấp chính dòng đó thay vì tạo dòng thứ
            // hai, để dự báo và giá thật cùng trỏ vào một kỳ.
            if (existing.getStatus() == CycleStatus.OPEN) {
                existing.setStatus(CycleStatus.ANNOUNCED);
                existing.setAnnouncedAt(announcedAt);
                existing.setEffectiveFrom(announcedAt);
                return adjustmentCycleRepository.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
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
     * Kỳ đang chạy, chưa có bản tin: tạo (hoặc lấy lại) dòng OPEN cho ngày công bố
     * dự kiến. Cùng khoá (cycle_start, cycle_end) mà findOrCreateAdjustmentCycle sẽ
     * dùng khi bản tin tới đúng ngày, nên hai bên gặp nhau ở cùng một dòng.
     *
     * Nếu bản tin bị dời sang ngày khác thì dòng OPEN này thành mồ côi và không bị
     * xoá: dự báo LIVE đã trỏ vào nó, và bảng forecast chỉ ghi thêm.
     */
    @Transactional
    public AdjustmentCycle openCycleFor(OffsetDateTime expectedAt) {
        LocalDate cycleEnd = expectedAt.toLocalDate();
        LocalDate cycleStart = cycleEnd.minusDays(CYCLE_LENGTH_DAYS);

        return adjustmentCycleRepository.findByCycleStartAndCycleEnd(cycleStart, cycleEnd)
                .orElseGet(() -> adjustmentCycleRepository.save(AdjustmentCycle.builder()
                        .cycleStart(cycleStart)
                        .cycleEnd(cycleEnd)
                        .status(CycleStatus.OPEN)
                        .build()));
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
