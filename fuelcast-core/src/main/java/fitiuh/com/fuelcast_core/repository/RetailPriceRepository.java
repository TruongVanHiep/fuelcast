package fitiuh.com.fuelcast_core.repository;

import fitiuh.com.fuelcast_core.entity.RetailPrice;
import fitiuh.com.fuelcast_core.entity.RetailPriceId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RetailPriceRepository extends JpaRepository<RetailPrice, RetailPriceId> {

    List<RetailPrice> findByIdProductIdOrderByIdObservedAtDesc(Short productId);

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
