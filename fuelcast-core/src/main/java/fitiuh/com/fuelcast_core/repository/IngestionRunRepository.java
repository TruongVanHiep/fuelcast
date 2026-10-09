package fitiuh.com.fuelcast_core.repository;

import fitiuh.com.fuelcast_core.entity.IngestionRun;
import fitiuh.com.fuelcast_core.entity.IngestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    /** Đã nạp thành công URL này chưa, để lần chạy sau bỏ qua thay vì tải lại. */
    boolean existsByTargetUrlAndStatus(String targetUrl, IngestionStatus status);
}
