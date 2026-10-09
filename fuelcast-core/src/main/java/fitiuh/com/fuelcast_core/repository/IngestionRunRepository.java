package fitiuh.com.fuelcast_core.repository;

import fitiuh.com.fuelcast_core.entity.IngestionRun;
import fitiuh.com.fuelcast_core.entity.IngestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    /** Đã nạp thành công URL này chưa, để lần chạy sau bỏ qua thay vì tải lại. */
    boolean existsByTargetUrlAndStatus(String targetUrl, IngestionStatus status);

    /**
     * Id của mọi lần nạp còn giữ bản gốc. Chỉ lấy id, không lấy entity: mỗi
     * raw_payload cỡ 110 KB, nạp cả trăm bản vào bộ nhớ một lúc là lãng phí.
     */
    @Query("select r.id from IngestionRun r where r.rawPayload is not null order by r.id")
    List<Long> findIdsWithRawPayload();
}
