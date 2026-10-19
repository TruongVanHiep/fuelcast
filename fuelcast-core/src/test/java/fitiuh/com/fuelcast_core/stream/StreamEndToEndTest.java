package fitiuh.com.fuelcast_core.stream;

import fitiuh.com.fuelcast_core.stream.StreamEvents.ForecastsUpdated;
import fitiuh.com.fuelcast_core.stream.StreamEvents.PricesUpdated;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cả đường đi thật: một client HTTP mở kết nối vào Tomcat thật, ứng dụng phát một sự kiện nội
 * bộ, client đọc ra đúng khung SSE. Kiểm tra được điều mock không kiểm được: dữ liệu thật sự được
 * đẩy xuống (flush) ngay chứ không nằm đệm trong máy chủ, tên sự kiện đúng, và đóng kết nối thì
 * máy chủ gỡ đăng ký.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StreamEndToEndTest {

    @Value("${local.server.port}")
    int port;

    @Autowired StreamHub hub;
    @Autowired ApplicationEventPublisher publisher;

    /** Đọc từng dòng của luồng ở một luồng riêng, để test có thể chờ có hạn thay vì treo mãi. */
    private static class Reader implements AutoCloseable {
        final List<String> lines = new CopyOnWriteArrayList<>();
        private final InputStream body;
        private final CompletableFuture<Void> task;

        Reader(InputStream body) {
            this.body = body;
            this.task = CompletableFuture.runAsync(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (java.io.IOException ignored) {
                    // kết nối bị đóng: hết luồng
                }
            });
        }

        boolean await(Predicate<List<String>> condition) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                if (condition.test(new ArrayList<>(lines))) {
                    return true;
                }
                Thread.sleep(50);
            }
            return false;
        }

        @Override
        public void close() throws java.io.IOException {
            body.close();
            task.cancel(true);
        }
    }

    private Reader connect() throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/stream/prices"))
                .header("Accept", "text/event-stream").GET().timeout(Duration.ofSeconds(30)).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        return new Reader(response.body());
    }

    private static boolean has(List<String> lines, String exact) {
        return lines.contains(exact);
    }

    private static boolean hasLineContaining(List<String> lines, String part) {
        return lines.stream().anyMatch(l -> l.contains(part));
    }

    @Test
    void aPublishedEventReachesTheBrowserAsAnSseFrame() throws Exception {
        try (Reader reader = connect()) {
            // "ready" tới ngay khi kết nối: chứng tỏ không có lớp đệm nào giữ dữ liệu lại.
            assertThat(reader.await(l -> has(l, "event:ready"))).as("sự kiện ready").isTrue();

            publisher.publishEvent(new PricesUpdated(OffsetDateTime.parse("2026-10-22T15:03:00+07:00"), 1));

            assertThat(reader.await(l -> has(l, "event:prices"))).as("sự kiện prices").isTrue();
            assertThat(reader.await(l -> hasLineContaining(l, "\"ingested\":1"))).as("dữ liệu JSON").isTrue();
            assertThat(reader.lines).anyMatch(l -> l.startsWith("data:") && l.contains("2026-10-22T15:03"));
        }
    }

    @Test
    void forecastEventsUseTheirOwnName() throws Exception {
        try (Reader reader = connect()) {
            assertThat(reader.await(l -> has(l, "event:ready"))).isTrue();

            publisher.publishEvent(new ForecastsUpdated(OffsetDateTime.now(), 8, 4));

            assertThat(reader.await(l -> has(l, "event:forecasts"))).isTrue();
            assertThat(reader.await(l -> hasLineContaining(l, "\"created\":8"))).isTrue();
            assertThat(reader.lines).doesNotContain("event:prices");
        }
    }

    @Test
    void closingTheConnectionFreesTheServerSideSlot() throws Exception {
        int before = hub.subscriberCount();

        Reader reader = connect();
        assertThat(reader.await(l -> has(l, "event:ready"))).isTrue();
        assertThat(hub.subscriberCount()).isEqualTo(before + 1);

        reader.close();

        // Máy chủ biết kết nối đã chết ở lần gửi kế tiếp (sự kiện hoặc nhịp tim) hoặc khi
        // Tomcat báo lỗi; ép một lần gửi để không phải chờ nhịp tim 20 giây.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (hub.subscriberCount() > before && System.nanoTime() < deadline) {
            publisher.publishEvent(new PricesUpdated(OffsetDateTime.now(), 0));
            Thread.sleep(100);
        }
        assertThat(hub.subscriberCount()).isEqualTo(before);
    }
}
