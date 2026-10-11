package fitiuh.com.fuelcast_core.repository;

import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.RetailPriceId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface RetailPriceRepository extends JpaRepository<RetailPrice, RetailPriceId> {

    List<RetailPrice> findByIdProductIdOrderByIdObservedAtDesc(Short productId);

    /**
     * Chuỗi giá vùng 1 của một mặt hàng, thời gian tăng dần (biểu đồ cần vậy).
     * Khoảng là [from, to): nửa mở ở đầu cuối để "đến hết ngày X" biểu diễn được
     * bằng "< 00:00 ngày X+1", không làm rơi kỳ công bố lúc 15:00 của ngày X.
     */
    @Query("""
            select p from RetailPrice p
            where p.id.productId = :productId
              and p.id.region = 1
              and p.id.observedAt >= :from
              and p.id.observedAt < :to
            order by p.id.observedAt asc
            """)
    List<RetailPrice> findHistory(@Param("productId") Short productId,
                                  @Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to);

    /** Giá mới nhất của từng mặt hàng ở vùng 1 — dùng cho trang chủ. */
    @Query("""
            select p from RetailPrice p
            where p.id.region = 1
              and p.id.observedAt = (
                  select max(q.id.observedAt) from RetailPrice q
                  where q.id.productId = p.id.productId and q.id.region = 1
              )
            order by p.id.productId
            """)
    List<RetailPrice> findLatestPerProduct();
}
