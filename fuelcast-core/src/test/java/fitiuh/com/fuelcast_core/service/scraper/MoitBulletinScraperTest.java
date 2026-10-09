package fitiuh.com.fuelcast_core.service.scraper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chỉ test phần bóc HTML danh sách; phần gọi mạng thật kiểm bằng tay. */
class MoitBulletinScraperTest {

    /**
     * Trang danh sách (100 bài) chứa 7 bản tin giá lẫn trong họp báo, thông tư,
     * bổ nhiệm... Phải ra đúng 7, theo thứ tự mới nhất trước.
     */
    @Test
    void keepsOnlyPriceBulletinsFromListingPage() throws IOException {
        ListingPage page = MoitBulletinScraper.parseListing(fixture("moit-listing-page1.html"));

        assertThat(page.bulletins()).extracting(BulletinLink::title).containsExactly(
                "Thông tin điều hành giá xăng dầu ngày 8/10/2026",
                "Một số thông tin đáng chú ý về điều hành giá xăng dầu ngày 1/10",
                "Một số thông tin điều hành giá bán xăng dầu ngày 24/9/2026",
                "Một số thông tin điều hành giá xăng dầu ngày 17/9",
                "Một số thông tin về việc điều hành giá xăng dầu ngày 10/9",
                "Một số thông tin về việc điều hành giá xăng dầu ngày 3/9",
                "Một số thông tin về việc điều hành giá xăng dầu ngày 27/8/2026");
    }

    /** Href trong HTML là đường dẫn tương đối; scraper phải trả địa chỉ đầy đủ. */
    @Test
    void returnsAbsoluteUrls() throws IOException {
        ListingPage page = MoitBulletinScraper.parseListing(fixture("moit-listing-page1.html"));

        assertThat(page.bulletins().get(0).url())
                .isEqualTo("https://moit.gov.vn/tin-tuc/thong-tin-dieu-hanh-gia-xang-dau-ngay-8-10-2026.html");
        assertThat(page.bulletins())
                .allSatisfy(l -> assertThat(l.url()).startsWith("https://moit.gov.vn/"));
    }

    /**
     * Bài về quy hoạch kho dự trữ có "xăng dầu" trong slug nhưng không phải bản
     * tin giá. Đây là lý do lọc theo tiêu đề thay vì theo slug.
     */
    @Test
    void rejectsArticleThatOnlyMentionsFuelInSlug() throws IOException {
        ListingPage page = MoitBulletinScraper.parseListing(fixture("moit-listing-page1.html"));

        assertThat(page.bulletins()).extracting(BulletinLink::url)
                .noneMatch(u -> u.contains("quy-hoach-ha-tang-du-tru"));
    }

    /**
     * Slug của "ngày 1/10" không có năm, nên năm phải lấy từ ngày đăng trên
     * thẻ span.article-date chứ không đoán từ URL hay tiêu đề.
     */
    @Test
    void readsPublishDateFromArticleMetadata() throws IOException {
        ListingPage page = MoitBulletinScraper.parseListing(fixture("moit-listing-page1.html"));

        BulletinLink noYearInSlug = page.bulletins().stream()
                .filter(l -> l.title().endsWith("ngày 1/10"))
                .findFirst().orElseThrow();

        assertThat(noYearInSlug.publishedOn()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    /**
     * Điều kiện dừng của vòng lặp phụ thuộc vào ngày cũ nhất của CẢ trang, vì có
     * trang không chứa bản tin giá nào mà vẫn phải biết đã lùi tới đâu.
     */
    @Test
    void reportsOldestDateAndCountOverAllArticles() throws IOException {
        ListingPage page = MoitBulletinScraper.parseListing(fixture("moit-listing-page1.html"));

        assertThat(page.articleCount()).isEqualTo(100);
        assertThat(page.oldestArticleDate()).isEqualTo(LocalDate.of(2026, 5, 25));
    }

    /** Hết danh sách: server trả HTML không có bài nào, vòng lặp dựa vào đó để dừng. */
    @Test
    void emptyListingHasNoArticles() {
        ListingPage page = MoitBulletinScraper.parseListing("<html><body></body></html>");

        assertThat(page.articleCount()).isZero();
        assertThat(page.bulletins()).isEmpty();
        assertThat(page.oldestArticleDate()).isNull();
    }

    /** Title có thể ở dạng NFD (chữ rời dấu); lọc vẫn phải nhận ra. */
    @Test
    void recognisesDecomposedUnicodeInTitle() {
        String nfd = Normalizer.normalize(
                "Thông tin điều hành giá xăng dầu ngày 8/10/2026", Normalizer.Form.NFD);

        assertThat(MoitBulletinScraper.isPriceBulletin(nfd)).isTrue();
    }

    /**
     * Có kỳ tiêu đề không có chữ "giá" ("điều hành xăng dầu"). Bộ lọc chặt hơn
     * từng làm mất 5 bản tin so với dữ liệu spike.
     */
    @Test
    void acceptsTitleWithoutTheWordGia() {
        assertThat(MoitBulletinScraper.isPriceBulletin(
                "Một số thông tin về điều hành xăng dầu ngày 12/12/2024")).isTrue();
    }

    @Test
    void rejectsUnrelatedFuelArticles() {
        assertThat(MoitBulletinScraper.isPriceBulletin(
                "Bộ Công Thương xin ý kiến về điều chỉnh quy hoạch hạ tầng dự trữ xăng dầu")).isFalse();
    }

    @Test
    void acceptsHttpsUrlOnMoitHost() {
        MoitBulletinScraper.requireMoitUrl(
                "https://moit.gov.vn/tin-tuc/thong-tin-dieu-hanh-gia-xang-dau-ngay-8-10-2026.html");
    }

    /** Mỗi dòng là một cách đánh lừa kiểu so chuỗi mà so host thì không bị. */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://moit.gov.vn/tin-tuc/x.html",
            "https://moit.gov.vn.evil.com/tin-tuc/x.html",
            "https://moit.gov.vn@evil.com/tin-tuc/x.html",
            "https://evil.com/moit.gov.vn/x.html",
            "https://www.moit.gov.vn/tin-tuc/x.html"
    })
    void rejectsUrlsOutsideMoit(String url) {
        assertThatThrownBy(() -> MoitBulletinScraper.requireMoitUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formCarriesPageNumberAndEncodesSpaceInOrder() {
        String form = MoitBulletinScraper.listingForm(3);

        assertThat(form).contains("pageNo=3", "itemsPerPage=100", "orderBy=publishTime+DESC");
    }

    /** Phải gọi mục cha /tin-tuc; mục "Thông báo" (101788814) thiếu bản tin. */
    @Test
    void formTargetsParentNewsCategory() {
        String form = MoitBulletinScraper.listingForm(1);

        assertThat(form).contains("categoryId=101788658").doesNotContain("101788814");
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            assertThat(in).as("thiếu fixture %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
