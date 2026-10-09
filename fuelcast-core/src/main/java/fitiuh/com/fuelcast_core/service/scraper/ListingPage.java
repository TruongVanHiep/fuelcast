package fitiuh.com.fuelcast_core.service.scraper;

import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả đọc một trang danh sách tin.
 *
 * oldestArticleDate tính trên MỌI bài của trang, không chỉ bản tin giá: có những
 * trang không chứa bản tin nào mà vẫn phải biết đã lùi tới ngày nào để quyết
 * định dừng. articleCount == 0 nghĩa là đã hết danh sách.
 */
public record ListingPage(List<BulletinLink> bulletins, LocalDate oldestArticleDate, int articleCount) { }
