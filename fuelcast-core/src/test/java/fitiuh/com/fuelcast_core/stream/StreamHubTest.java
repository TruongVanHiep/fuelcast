package fitiuh.com.fuelcast_core.stream;

import fitiuh.com.fuelcast_core.stream.StreamEvents.ForecastsUpdated;
import fitiuh.com.fuelcast_core.stream.StreamEvents.PricesUpdated;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamHubTest {

    private static final OffsetDateTime AT = OffsetDateTime.of(2026, 10, 22, 15, 3, 0, 0, ZoneOffset.ofHours(7));

    private final StreamHub hub = new StreamHub(2, Duration.ofMinutes(30), Duration.ofSeconds(20));

    /** Emitter ghi lại từng sự kiện được gửi, hoặc ném lỗi như một trình duyệt đã đóng tab. */
    private static class FakeEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        boolean closed;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (closed) {
                throw new IOException("Broken pipe");
            }
            sent.add(builder.build().stream()
                    .map(d -> String.valueOf(d.getData()))
                    .collect(Collectors.joining("")));
        }
    }

    @Test
    void subscribingRegistersTheEmitterAndSendsReadyAtOnce() {
        SseEmitter emitter = hub.subscribe();

        assertThat(emitter).isNotNull();
        assertThat(hub.subscriberCount()).isEqualTo(1);
    }

    @Test
    void refusesConnectionsBeyondTheLimitInsteadOfExhaustingTheServer() {
        hub.subscribe();
        hub.subscribe();

        assertThatThrownBy(hub::subscribe)
                .isInstanceOf(TooManyStreamsException.class)
                .hasMessageContaining("2");
        assertThat(hub.subscriberCount()).isEqualTo(2);
    }

    @Test
    void anEventReachesEverySubscriber() {
        FakeEmitter a = new FakeEmitter();
        FakeEmitter b = new FakeEmitter();
        hub.register(a);
        hub.register(b);

        hub.on(new PricesUpdated(AT, 1));

        assertThat(a.sent).hasSize(1);
        assertThat(b.sent).hasSize(1);
        assertThat(a.sent.get(0)).contains("ingested=1");
    }

    /**
     * Trình duyệt đóng tab mà máy chủ không biết thì kết nối rò mãi. Lần gửi thất bại là lúc
     * phát hiện, và kết nối chết phải bị gỡ, không được làm các kết nối còn sống ngừng nhận.
     */
    @Test
    void aDeadSubscriberIsDroppedAndDoesNotStopTheOthers() {
        FakeEmitter dead = new FakeEmitter();
        FakeEmitter alive = new FakeEmitter();
        hub.register(dead);
        hub.register(alive);
        dead.closed = true;

        hub.on(new ForecastsUpdated(AT, 8, 4));

        assertThat(alive.sent).hasSize(1);
        assertThat(hub.subscriberCount()).isEqualTo(1);
    }

    @Test
    void theHeartbeatFindsAndDropsDeadSubscribers() {
        FakeEmitter dead = new FakeEmitter();
        FakeEmitter alive = new FakeEmitter();
        hub.register(dead);
        hub.register(alive);
        dead.closed = true;

        hub.sendHeartbeat();

        assertThat(hub.subscriberCount()).isEqualTo(1);
        assertThat(alive.sent).hasSize(1);
    }

    @Test
    void aSlotFreedByADeadSubscriberCanBeReused() {
        FakeEmitter dead = new FakeEmitter();
        hub.register(dead);
        hub.subscribe();                        // đủ 2/2
        dead.closed = true;
        hub.sendHeartbeat();                    // gỡ kết nối chết

        hub.subscribe();                        // lại vào được

        assertThat(hub.subscriberCount()).isEqualTo(2);
    }

    @Test
    void broadcastingToNobodyIsHarmless() {
        hub.on(new PricesUpdated(AT, 1));

        assertThat(hub.subscriberCount()).isZero();
    }

    @Test
    void stopCompletesAndClearsEverySubscriber() {
        hub.start();
        hub.subscribe();

        hub.stop();

        assertThat(hub.subscriberCount()).isZero();
    }
}
