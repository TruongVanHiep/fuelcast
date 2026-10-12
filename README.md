# FuelCast

[![CI](https://github.com/TruongVanHiep/fuelcast/actions/workflows/ci.yml/badge.svg)](https://github.com/TruongVanHiep/fuelcast/actions/workflows/ci.yml)

Theo dõi và dự báo giá xăng dầu bán lẻ ở Việt Nam theo từng kỳ điều hành giá
(công bố chiều thứ Năm). Dữ liệu lấy từ bản tin của Bộ Công Thương.

Trạng thái dự án: **đang phát triển**. Phần thu thập dữ liệu và API đọc đã chạy;
phần dự báo mới đo được ở mức "giải thích một kỳ", chưa dự báo trước sự kiện.
Xem mục *Điều gì đã được chứng minh, và điều gì chưa*.

## Đã có

| Phần | Nội dung |
|---|---|
| Thu thập | Quét danh sách tin moit.gov.vn, tải từng bản tin, **lưu HTML gốc**, bóc giá bán lẻ và giá thế giới |
| Lưu trữ | PostgreSQL + TimescaleDB, schema do Flyway quản lý, nạp lại không nhân đôi (khoá tự nhiên) |
| API đọc | `GET /api/v1/prices/current`, `/prices/history`, `/cycles/current`, `/accuracy/scorecard` |
| Mô hình | Truyền dẫn giá thế giới → giá bán lẻ, đánh giá walk-forward so với "giá không đổi" |
| Lỗi | Theo RFC 7807 (`application/problem+json`) |

## Chạy

Cần JDK 21 và Docker.

```bash
cd fuelcast-core
docker compose up -d            # TimescaleDB, cổng 5433 trên máy
./mvnw spring-boot:run          # API ở http://localhost:8080
./mvnw test
```

Cơ sở dữ liệu dùng cổng **5433** (không phải 5432) để không đụng PostgreSQL cài sẵn.

Các việc chạy bằng dòng lệnh (thêm `--spring.main.web-application-type=none` để app tự thoát):

| Tham số | Việc làm |
|---|---|
| `--fuelcast.backfill.since=2024-01-01` | Quét và nạp các bản tin từ ngày đó (mất cỡ 20 phút, cách nhau 5 giây/request) |
| `--fuelcast.reprocess=true` | Parse lại bản gốc đã lưu, không gọi mạng |
| `--fuelcast.backtest=true` | In bảng đo độ chính xác |
| `--fuelcast.forecast.replay=true` | Lưu các dự báo phát lại vào bảng `forecast` |

## Kiến trúc

Một ứng dụng Spring Boot theo kiểu modular monolith, các lớp tách rõ:

```
moit.gov.vn ─ scraper ─ parser ─ ingestion ─► PostgreSQL ◄─ service ◄─ controller ─► JSON
                          │                      ▲
              ingestion_run.raw_payload ─────────┘ (parse lại khi sửa parser)
```

Vài quyết định có chủ ý:

- **Lưu HTML gốc trước khi parse.** Trang nguồn đổi bố cục vài lần mỗi năm; có bản
  gốc thì sửa parser xong là sửa được lịch sử trong vài giây, không phải quét lại.
- **Không dùng message broker.** Các module chạy chung một tiến trình; đặt broker
  vào giữa chỉ thêm chi phí vận hành mà không mua được gì. Sẽ xem lại khi tách
  service thông báo ra khỏi tiến trình chính.
- **Lọc bản tin theo tiêu đề, không theo URL.** Đường dẫn bản tin không nhất quán
  (lúc có năm, lúc không, có bản tên `dgsg.html`).
- **Thời điểm trả về theo giờ Việt Nam (`+07:00`).** Mặc định UTC làm kỳ nửa đêm
  27/3 hiện thành 26/3.

## Điều gì đã được chứng minh, và điều gì chưa

Mô hình đoán mức thay đổi giá bán lẻ (đồng/lít) từ mức thay đổi **giá thế giới
trung bình của chính kỳ đó**, học theo kiểu walk-forward (mỗi kỳ chỉ dùng các kỳ
trước). So với đoán "giá không đổi":

| Mặt hàng | Số kỳ | Sai số TB mô hình | Sai số TB "không đổi" | Đúng chiều |
|---|---|---|---|---|
| RON95 | 95 | 90 | 457 | 100% |
| E5RON92 | 97 | 89 | 442 | 98% |
| Dầu điêzen | 98 | 115 | 502 | 96% |
| Dầu hỏa | 90 | 47 | 508 | 98% |
| Dầu madút | 99 | 392 | 388 | 49% |

**Đọc con số này cho đúng:**

- **Đây là "giải thích", chưa phải "dự báo".** Giá thế giới trung bình của kỳ chỉ
  đầy đủ vào lúc công bố, nên mô hình đang ở *horizon 0*. Dự báo trước sự kiện cần
  số liệu theo ngày (tỷ giá, giá futures) mà dự án chưa thu thập.
- **Các dòng trong bảng `forecast` hiện đều là `REPLAY`**, tức phát lại trên lịch
  sử. Mô hình được chọn sau khi đã nhìn dữ liệu nên kết quả lạc quan hơn dự báo
  thật. API bảng điểm tách `REPLAY` khỏi `LIVE` và luôn kèm lời cảnh báo.
- **Dầu madút không dự báo được** (không thắng đường cơ sở). Hệ thống báo thẳng
  điều đó thay vì giấu.
- **Giá thế giới trong dữ liệu là trung bình theo kỳ**, không phải giá đóng cửa
  hằng ngày: nguồn miễn phí duy nhất có sẵn. Không dùng lẫn với giá theo ngày.
- **Có kỳ bị thiếu bản tin.** Kiểm tra liên tục (mức đổi trong bản tin phải bằng
  chênh lệch giá) phát hiện ít nhất hai kỳ như vậy (25/3/2026, 2/7/2026) và loại
  chúng khỏi phần đánh giá. Bản tin 26/6/2025 dùng bảng số với hai mốc giá nên
  parser chưa đọc được.
- Mẫu nhỏ (khoảng 140 kỳ), đừng suy ra quá nhiều từ một vài chữ số.

Thông tin chỉ mang tính tham khảo, không phải tư vấn đầu tư.

## Việc tiếp theo

- Thu thập tỷ giá USD/VND và giá futures theo ngày để dự báo thật theo `horizon_days`.
- Tìm hai bản tin bị thiếu và hỗ trợ bản tin dạng bảng của 26/6/2025.
- Lưu dự báo `LIVE` trước mỗi kỳ công bố và chấm điểm sau đó.
