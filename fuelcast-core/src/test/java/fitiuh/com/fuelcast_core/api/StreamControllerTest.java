package fitiuh.com.fuelcast_core.api;

import fitiuh.com.fuelcast_core.stream.StreamHub;
import fitiuh.com.fuelcast_core.stream.TooManyStreamsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StreamController.class)
class StreamControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StreamHub hub;

    @Test
    void opensAnEventStream() throws Exception {
        when(hub.subscribe()).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/stream/prices").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
    }

    /** Quá tải trả 503 theo RFC 7807 như mọi lỗi khác của API, không phải trang lỗi HTML. */
    @Test
    void tooManyConnectionsIsAProblemDetailWith503() throws Exception {
        when(hub.subscribe()).thenThrow(new TooManyStreamsException(100));

        mockMvc.perform(get("/api/v1/stream/prices").accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Quá nhiều kết nối theo dõi"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("100")));
    }
}
