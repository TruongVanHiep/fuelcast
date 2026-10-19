package fitiuh.com.fuelcast_core.stream;

import fitiuh.com.fuelcast_core.stream.StreamEvents.ForecastsUpdated;
import fitiuh.com.fuelcast_core.stream.StreamEvents.PricesUpdated;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Giữ danh sách các trình duyệt đang theo dõi (Server-Sent Events) và đẩy sự kiện xuống họ.
 *
 * Giới hạn đã biết: chỉ đúng khi chạy MỘT bản ứng dụng, vì danh sách kết nối nằm trong bộ nhớ.
 * Cùng giới hạn với bộ quét bản tin (xem BulletinPoller); nhiều bản cần một kênh chung như Redis
 * pub/sub, chưa cần cho tới khi có nhiều hơn một bản.
 *
 * Ba điều dễ sai khi làm SSE, và cách giữ ở đây:
 *  - Gửi đồng thời từ hai luồng (sự kiện thật và nhịp tim) vào cùng một emitter làm hỏng luồng
 *    dữ liệu, nên mọi lần gửi đều đồng bộ hoá theo emitter.
 *  - Trình duyệt đóng tab mà máy chủ không biết thì kết nối rò mãi, nên có nhịp tim: lần gửi
 *    thất bại là lúc phát hiện và gỡ kết nối chết.
 *  - Proxy ở giữa (nginx, Caddy) im lặng quá lâu sẽ tự cắt kết nối rảnh, nên nhịp tim cũng giữ
 *    cho đường truyền sống.
 */
@Component
public class StreamHub {

    private static final Logger log = LoggerFactory.getLogger(StreamHub.class);

    private final Set<SseEmitter> subscribers = ConcurrentHashMap.newKeySet();
    private final int maxSubscribers;
    private final Duration timeout;
    private final Duration heartbeat;
    private ScheduledExecutorService scheduler;

    public StreamHub(@Value("${fuelcast.stream.max-subscribers:100}") int maxSubscribers,
                     @Value("${fuelcast.stream.timeout:PT30M}") Duration timeout,
                     @Value("${fuelcast.stream.heartbeat:PT20S}") Duration heartbeat) {
        this.maxSubscribers = maxSubscribers;
        this.timeout = timeout;
        this.heartbeat = heartbeat;
    }

    @PostConstruct
    void start() {
        // Luồng riêng, không dùng @Scheduled: bộ lập lịch của Spring chỉ bật khi bộ quét bật,
        // mà nhịp tim thì phải chạy mỗi khi có người theo dõi.
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "stream-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::sendHeartbeat,
                heartbeat.toMillis(), heartbeat.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
        subscribers.forEach(SseEmitter::complete);
        subscribers.clear();
    }

    /**
     * Mở một kết nối mới. Gửi ngay sự kiện "ready": trình duyệt biết kết nối thông, và nếu một
     * proxy đang gom đệm thì lỗi lộ ra ngay lúc đầu chứ không phải tới bản tin sau.
     *
     * @throws TooManyStreamsException nếu đã đủ số kết nối tối đa
     */
    public synchronized SseEmitter subscribe() {
        if (subscribers.size() >= maxSubscribers) {
            throw new TooManyStreamsException(maxSubscribers);
        }
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        register(emitter);
        send(emitter, SseEmitter.event().name("ready").data("{}", MediaType.APPLICATION_JSON)
                // Trình duyệt tự nối lại sau 5 giây khi mất kết nối (EventSource làm sẵn).
                .reconnectTime(5_000));
        return emitter;
    }

    void register(SseEmitter emitter) {
        subscribers.add(emitter);
        // Mọi đường kết thúc đều gỡ khỏi danh sách: người dùng đóng tab, hết hạn, hoặc lỗi.
        Runnable drop = () -> subscribers.remove(emitter);
        emitter.onCompletion(drop);
        emitter.onTimeout(drop);
        emitter.onError(error -> drop.run());
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    @EventListener
    void on(PricesUpdated event) {
        broadcast("prices", event);
    }

    @EventListener
    void on(ForecastsUpdated event) {
        broadcast("forecasts", event);
    }

    void broadcast(String name, Object data) {
        SseEmitter.SseEventBuilder event = SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON);
        for (SseEmitter emitter : subscribers) {
            send(emitter, event);
        }
        log.debug("Đẩy sự kiện {} tới {} kết nối", name, subscribers.size());
    }

    void sendHeartbeat() {
        for (SseEmitter emitter : subscribers) {
            // Dòng bình luận (bắt đầu bằng ":"): trình duyệt bỏ qua, proxy thấy có dữ liệu chạy.
            send(emitter, SseEmitter.event().comment("ping"));
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            // Đồng bộ theo emitter: sự kiện thật và nhịp tim có thể đến từ hai luồng cùng lúc.
            synchronized (emitter) {
                emitter.send(event);
            }
        } catch (IOException | IllegalStateException e) {
            // Trình duyệt đã đi (IOException) hoặc emitter đã đóng (IllegalStateException).
            subscribers.remove(emitter);
            log.debug("Gỡ một kết nối đã chết: {}", e.toString());
        }
    }
}
