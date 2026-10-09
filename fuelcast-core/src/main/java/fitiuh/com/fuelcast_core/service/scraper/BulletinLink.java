package fitiuh.com.fuelcast_core.service.scraper;

import java.time.LocalDate;

/**
 * Một bản tin trong trang danh sách tin của MOIT; url đã là địa chỉ tuyệt đối.
 * publishedOn là ngày đăng trên trang, có thể null nếu thẻ ngày bị thiếu.
 */
public record BulletinLink(String url, String title, LocalDate publishedOn) { }
