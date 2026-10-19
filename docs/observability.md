# Giám sát (observability)

Hệ thống chạy nền và tự quét MOIT, nên câu hỏi quan trọng nhất không phải "máy có khoẻ không" mà
là **"bộ quét còn sống không, và bản tin mới nhất ra cách đây bao lâu?"**. Phần này trả lời câu đó
bằng số đo (Micrometer, Prometheus), một dashboard (Grafana) và các cảnh báo.

Phạm vi cố ý hẹp: chỉ **số đo (metrics)**. Thiết kế ban đầu còn nêu Loki (log) và Tempo (trace);
tôi không làm vì chúng nặng RAM trên một VPS 4 GB, và trace ít giá trị khi chỉ có một ứng dụng
gọi một dịch vụ phụ. Log vẫn xem bằng `docker compose logs`.

## Thành phần

```
ứng dụng (cổng quản trị 8081) ◄── Prometheus (quét mỗi 15 giây, giữ 30 ngày) ◄── Grafana
                                      └── luật cảnh báo (observability/alerts.yml)
```

| Thành phần | Vai trò | Ở đâu |
|---|---|---|
| Actuator + Micrometer | Mở số đo ở `/actuator/prometheus` | `fuelcast-core`, cổng **8081** riêng |
| Prometheus | Quét số đo, lưu 30 ngày, đánh giá luật cảnh báo | `observability/prometheus.yml`, `alerts.yml` |
| Grafana | Dashboard "FuelCast: tổng quan", nạp từ file | `observability/grafana/` |

**Cổng quản trị tách riêng khỏi cổng ứng dụng (8081 so với 8080).** Số đo lộ thông tin nội bộ (đường
dẫn thư mục, tên lớp, số kết nối). Nginx chỉ chuyển `/api/` sang ứng dụng và cổng 8081 không được
mở ra máy chủ, nên chỉ Prometheus trong cùng mạng compose mới đọc được. Prometheus và Grafana thì
chỉ nghe ở `127.0.0.1`, và **không đi qua Caddy**.

## Bật lên

Máy phát triển (tuỳ chọn, tốn cỡ 300 đến 500 MB RAM):

```bash
cd fuelcast-core
docker compose --profile observability up -d
```

Prometheus ở <http://localhost:9090>, Grafana ở <http://localhost:3000> (tài khoản `admin`, mật khẩu
mặc định `admin` chỉ cho máy phát triển; đặt biến `GRAFANA_ADMIN_PASSWORD` nếu muốn khác).

Máy chủ thật: bật sẵn trong `deploy/docker-compose.prod.yml`, và **bắt buộc** đặt
`GRAFANA_ADMIN_PASSWORD` trong `deploy/.env`. Vì chỉ nghe ở `127.0.0.1`, muốn xem thì mở đường hầm
SSH từ máy bạn rồi vào `http://localhost:3000`:

```bash
ssh -L 3000:localhost:3000 -L 9090:localhost:9090 nguoidung@IP_MAY_CHU
```

Khi chạy ứng dụng từ IntelliJ (không trong docker), đổi target của `fuelcast-core` trong
`observability/prometheus.yml` thành `host.docker.internal:8081`.

## Số đo của dự án

Ngoài số đo có sẵn của JVM và HTTP, ứng dụng tự đo (trong `BulletinPoller` và `BulletinFreshnessMetrics`):

| Số đo | Loại | Ý nghĩa |
|---|---|---|
| `fuelcast_bulletin_last_effective_timestamp_seconds` | gauge | Thời điểm bản tin mới nhất có hiệu lực. Lấy từ DB, nên khởi động lại không làm nó về 0 |
| `fuelcast_poll_last_success_timestamp_seconds` | gauge | Lúc lượt quét thành công gần nhất (0 cho tới lượt đầu) |
| `fuelcast_poll_runs_total{result="ok"\|"error"}` | counter | Số lượt quét theo kết quả |
| `fuelcast_bulletins_ingested_total` | counter | Số bản tin đã nạp |
| `fuelcast_bulletins_failed_total` | counter | Số bản tin MOIT mà parser không đọc được |
| `fuelcast_forecasts_created_total` / `_graded_total` | counter | Số dự báo LIVE được tạo / chấm (đếm theo từng mặt hàng và mô hình) |

Vài quyết định đáng nói:

- **Đo thời điểm (timestamp), không đo "đã bao lâu".** Giá trị "đã trôi qua bao lâu" chỉ đúng lúc tính;
  timestamp thì đúng mãi và để Grafana tự trừ (`time() - metric`). Cảnh báo cũng vì vậy vẫn đúng khi việc
  quét của Prometheus bị trễ.
- **Hai gauge chứ không một.** Một cái trả lời "MOIT đã công bố gì và khi nào", cái kia trả lời "bộ quét có
  còn chạy không". Bộ quét chạy tốt mà MOIT không đăng gì là chuyện khác với bộ quét chết.
- **Bộ đếm được tạo ngay trong constructor** để xuất hiện với giá trị 0 từ lần quét đầu; tạo lười thì
  chuỗi chỉ xuất hiện sau lần tăng đầu tiên và `rate()`/`increase()` sai ở lần đó.
- **Không đưa thứ có vô số giá trị vào nhãn** (URL bản tin, id): mỗi giá trị mới là một chuỗi số đo mới
  và Prometheus sẽ phình.
- **Số đo của bộ quét chỉ có khi bộ quét bật** (`fuelcast.ingestion.poll.enabled`), như bản thân bộ quét.
  Chạy ứng dụng với bộ quét tắt thì không thấy chúng.

## Cảnh báo

Bốn luật trong `observability/alerts.yml`, mỗi luật có `for` để một lần khởi động lại hay lỗi mạng thoáng
qua không kêu oan:

| Cảnh báo | Điều kiện | Ý nghĩa |
|---|---|---|
| `FuelcastDown` | Prometheus không quét được ứng dụng trong 2 phút | Ứng dụng chết |
| `NoNewBulletin` | Bản tin mới nhất có hiệu lực cách đây quá 8 ngày (đúng trong 1 giờ) | Đã lỡ một kỳ: MOIT chưa đăng, parser hỏng, hoặc bộ quét hỏng |
| `PollerStalled` | Không có lượt quét thành công nào trong 13 giờ (đúng trong 15 phút) | Bộ quét kẹt; ngoài khung thứ Năm nó hỏi mỗi 6 giờ nên 13 giờ là đã lỡ hai lượt |
| `BulletinParseFailing` | Có bản tin lỗi trong 1 giờ qua | Thường là trang MOIT đổi bố cục; bản gốc đã lưu nên sửa parser xong nạp lại được bằng `--fuelcast.reprocess=true` |

**Luật cảnh báo được kiểm thử** như code: `observability/alerts.test.yml` dựng chuỗi số đo giả và nói trước
luật nào phải kêu lúc nào (ví dụ "bản tin 8 ngày tuổi: chưa kêu; 8 ngày 2 giờ: kêu", "không có bản tin nào:
im lặng, không coi là bản tin năm 1970"). Chạy tay:

```bash
docker run --rm --entrypoint promtool -v "$PWD/observability:/etc/prometheus:ro" -w /etc/prometheus \
  prom/prometheus:v3.13.4 test rules alerts.test.yml
```

CI chạy lệnh này, cộng kiểm tra cú pháp của Prometheus và dashboard.

## Giới hạn đã biết

- **Chưa có kênh gửi thông báo.** Cảnh báo hiện ở trang `/alerts` của Prometheus và được đếm trên dashboard
  ("Cảnh báo đang kêu"), nhưng không có email hay tin nhắn nào được gửi. Làm việc đó cần Alertmanager
  cộng một token bot Telegram hoặc cấu hình SMTP, mà đó là bí mật của riêng bạn.
- **Dịch vụ Python chưa có số đo** (độ trễ và lỗi của lời gọi `/predict`). Thêm `Timer` trong
  `PythonForecastClient` là bước tiếp theo hợp lý.
- **Prometheus chạy một bản, dữ liệu trong một volume.** Mất volume là mất lịch sử số đo (không mất dữ liệu
  nghiệp vụ, thứ đó nằm trong PostgreSQL). Dashboard và luật nằm trong git nên dựng lại được y nguyên.
