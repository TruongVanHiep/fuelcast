package fitiuh.com.fuelcast_core.forecast;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cấu hình đường gọi sang dịch vụ dự báo Python. Chỉ tạo khi bật
 * fuelcast.forecast.python.enabled.
 */
@Configuration
@ConditionalOnProperty(name = "fuelcast.forecast.python.enabled", havingValue = "true")
class PythonForecastConfig {

    @Bean
    RestClient pythonForecastRestClient(
            @Value("${fuelcast.forecast.python.url:http://localhost:8000}") String url,
            @Value("${fuelcast.forecast.python.timeout:PT30S}") Duration timeout) {

        // HTTP/1.1 cố định: máy khách JDK mặc định thử nâng cấp lên HTTP/2 bằng phần đầu
        // "Upgrade", mà uvicorn chỉ nói HTTP/1.1 nên bước đó chỉ thêm chỗ để hỏng.
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        // /backtest huấn luyện hàng chục lần nên chậm hơn /predict nhiều; mặc định 30 giây.
        factory.setReadTimeout(timeout);

        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
}
