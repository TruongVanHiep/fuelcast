package fitiuh.com.fuelcast_core.service.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lấy danh sách bản tin điều hành giá trên moit.gov.vn.
 *
 * Trang danh sách phân trang bằng AJAX: các nút "2, 3, 4" chỉ là
 * javascript:void(0), nên HTML của trang chỉ chứa trang 1. Muốn trang N phải
 * POST đúng endpoint mà chính trang đó tự gọi.
 *
 * Phải đọc mục cha "Tin tức" chứ không phải mục con "Thông báo": bản tin giá
 * bị MOIT đăng rải qua nhiều mục (52 bài nằm trực tiếp dưới /tin-tuc/, 37 bài
 * dưới /tin-tuc/thong-bao/ trong dữ liệu spike), nên chỉ mục cha mới đủ.
 */
@Component
public class MoitBulletinScraper {

    private static final String MOIT_HOST = "moit.gov.vn";
    private static final String BASE_URL = "https://" + MOIT_HOST;
    private static final String LISTING_ENDPOINT = BASE_URL
            + "/?module=Content.Listing&moduleId=25&cmd=redraw&site=2005517"
            + "&url_mode=rewrite&submitFormId=25&page=Article.News.list";

    /** categoryId của mục /tin-tuc (mục "Thông báo" là 101788814, thiếu bản tin). */
    private static final String NEWS_CATEGORY_ID = "101788658";

    /** Server chấp nhận 100 bài một trang, đỡ phải gọi nhiều so với mặc định 12. */
    static final int ITEMS_PER_PAGE = 100;

    /** Giãn cách giữa hai request liên tiếp tới cùng host. */
    private static final Duration REQUEST_GAP = Duration.ofSeconds(5);

    /** Chốt chặn để lỗi logic không biến thành vòng lặp vô hạn trên server người khác. */
    private static final int MAX_PAGES = 200;

    private static final String USER_AGENT = "fuelcast-dev (hieptruong035@gmail.com)";

    private static final Pattern DATE = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // Cố ý để mặc định (không đi theo redirect): đi theo 302 sang domain
            // khác sẽ vượt qua requireMoitUrl. Gặp 3xx thì sendForBody báo lỗi.
            .build();

    /**
     * Lùi từ bài mới nhất về tới {@code cutoff}, gom mọi bản tin giá có ngày đăng
     * từ cutoff trở đi. Dừng khi trang chứa bài cũ hơn cutoff hoặc hết danh sách.
     */
    public List<BulletinLink> findBulletinsSince(LocalDate cutoff) throws IOException, InterruptedException {
        List<BulletinLink> found = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            if (pageNo > 1) {
                Thread.sleep(REQUEST_GAP.toMillis());
            }
            ListingPage page = fetchListingPage(pageNo);
            if (page.articleCount() == 0) {
                break;
            }
            for (BulletinLink link : page.bulletins()) {
                if (link.publishedOn() == null || !link.publishedOn().isBefore(cutoff)) {
                    found.add(link);
                }
            }
            if (page.oldestArticleDate() != null && page.oldestArticleDate().isBefore(cutoff)) {
                break;
            }
        }
        return found;
    }

    /** Tải trang {@code pageNo} (bắt đầu từ 1) của danh sách tin; bài mới nhất nằm đầu. */
    public ListingPage fetchListingPage(int pageNo) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(LISTING_ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                // Thiếu header này server nhận body rỗng và lặng lẽ trả trang 1.
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(listingForm(pageNo)))
                .build();

        return parseListing(sendForBody(request, "trang " + pageNo + " của danh sách tin"));
    }

    /**
     * Tải HTML gốc của một bản tin. Trả nguyên văn, không parse: người gọi lưu
     * bản gốc vào ingestion_run.raw_payload trước rồi mới đưa cho parser.
     */
    public String fetchBulletin(String url) throws IOException, InterruptedException {
        requireMoitUrl(url);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        return sendForBody(request, "bản tin " + url);
    }

    /**
     * Link đến từ HTML của người khác, nên chỉ tải địa chỉ https thuộc đúng
     * moit.gov.vn. So sánh host chứ không so chuỗi: "moit.gov.vn.evil.com" và
     * "https://moit.gov.vn@evil.com/" đều đánh lừa được kiểu startsWith.
     */
    static void requireMoitUrl(String url) {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !MOIT_HOST.equals(uri.getHost())) {
            throw new IllegalArgumentException("Chỉ tải URL https của " + MOIT_HOST + ", nhận được: " + url);
        }
    }

    private String sendForBody(HttpRequest request, String what) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException("MOIT trả HTTP " + response.statusCode() + " khi lấy " + what);
        }
        return response.body();
    }

    /** Dựng body form mà trang MOIT tự gửi khi bấm sang trang khác. */
    static String listingForm(int pageNo) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("layout", "Content.Article.News.default");
        form.put("itemsPerPage", String.valueOf(ITEMS_PER_PAGE));
        form.put("orderBy", "publishTime DESC");
        form.put("pageNo", String.valueOf(pageNo));
        form.put("service", "Content.Article.selectAll");
        form.put("type", "Article.News");
        form.put("categoryId", NEWS_CATEGORY_ID);
        form.put("parentId", NEWS_CATEGORY_ID);
        form.put("widgetCode", "5b72a94b9218655475508114");
        form.put("widgetTemplateId", "5feffbc0cccf1c7cdf7dada3");
        form.put("page", "Article.News.list");
        form.put("moduleParentId", "12");
        form.put("phpModuleName", "Content.Listing");

        return form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /**
     * Bóc HTML danh sách. Tách riêng khỏi phần gọi mạng để test được bằng
     * fixture, không cần MOIT còn sống.
     */
    static ListingPage parseListing(String html) {
        // baseUri để absUrl("href") biến "/tin-tuc/..." thành địa chỉ đầy đủ.
        Document doc = Jsoup.parse(html, BASE_URL);

        List<BulletinLink> bulletins = new ArrayList<>();
        LocalDate oldest = null;
        int count = 0;

        for (Element article : doc.select("article.article-news")) {
            // Mỗi bài có 3 thẻ <a> cùng href (ảnh, tiêu đề, "Đọc thêm"); chỉ thẻ
            // ảnh mang class article-img nên chọn nó để mỗi bài ra đúng một lần.
            Element a = article.selectFirst("a.article-img[title]");
            if (a == null) {
                continue;
            }
            count++;

            LocalDate published = readPublishedDate(article);
            if (published != null && (oldest == null || published.isBefore(oldest))) {
                oldest = published;
            }

            String title = a.attr("title").strip();
            if (isPriceBulletin(title)) {
                bulletins.add(new BulletinLink(a.absUrl("href"), title, published));
            }
        }
        return new ListingPage(bulletins, oldest, count);
    }

    /** Ngày đăng nằm ở span.article-date, dạng dd/MM/yyyy. */
    private static LocalDate readPublishedDate(Element article) {
        Element span = article.selectFirst("span.article-date");
        if (span == null) {
            return null;
        }
        Matcher m = DATE.matcher(span.text());
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.parse(m.group(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Lọc theo tiêu đề (câu chữ) chứ không theo slug: slug lúc có năm lúc không,
     * và "xang-dau" một mình để lọt cả bài về quy hoạch kho dự trữ xăng dầu.
     *
     * Chỉ đòi "điều hành" chứ không đòi "điều hành giá": có kỳ MOIT đặt tiêu đề
     * "Một số thông tin về điều hành xăng dầu ngày 12/12/2024" (không có chữ giá)
     * và bộ lọc chặt hơn làm mất 5 bản tin so với dữ liệu spike. Thà nhận dư vài
     * bài rồi để MoitBulletinParser.isUsable() loại, còn hơn bỏ sót kỳ giá.
     */
    static boolean isPriceBulletin(String title) {
        String t = Normalizer.normalize(title, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return t.contains("điều hành") && t.contains("xăng dầu");
    }
}
