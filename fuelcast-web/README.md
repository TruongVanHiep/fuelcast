# fuelcast-web

Giao diện của FuelCast: React 19, TypeScript, Vite, Tailwind 4, ECharts. Chỉ đọc dữ liệu từ
API của `fuelcast-core` (5 endpoint GET), không có đăng nhập.

| Trang | Nội dung |
|---|---|
| Tổng quan (`/`) | Đếm ngược tới kỳ công bố dự kiến, dự báo kỳ tới, giá hiện hành |
| Dự báo (`/forecast`) | Dự báo LIVE cho kỳ tới, kèm thời điểm tạo và lời cảnh báo |
| Bảng điểm (`/scorecard`) | Sai số mô hình so với "giá không đổi", tách LIVE và REPLAY |
| Lịch sử giá (`/history`) | Biểu đồ bậc thang theo mặt hàng và khoảng ngày, xuất CSV |

## Chạy

Cần Node 24 và `fuelcast-core` đang chạy ở cổng 8080.

```bash
npm install
npm run dev      # http://localhost:5173
```

Vite chuyển mọi đường dẫn `/api` sang `localhost:8080` (xem `vite.config.ts`), nên code gọi
đường dẫn tương đối y như khi triển khai và không cần bật CORS ở backend.

```bash
npm run lint
npm run build    # kiểm tra kiểu bằng tsc rồi build ra dist/
```

## Cấu trúc

```
src/
  api/          axios, kiểu dữ liệu khớp DTO của backend, mỗi endpoint một hàm
  hooks/        useAsync (đang tải / lỗi / có dữ liệu), useECharts
  components/   Layout, Countdown, Badges, Notice, StateBlocks; charts/ chứa biểu đồ
  pages/        bốn trang ở trên
  lib/format.ts định dạng tiền, ngày giờ theo giờ Việt Nam
```

## Vài quyết định

- **Màu không đứng một mình.** Tăng/giảm luôn có mũi tên và dấu +/− đi kèm, để người mù
  màu vẫn đọc được. Bảng màu khai báo một lần bằng biến CSS, có cả chế độ tối.
- **Lời cảnh báo của backend hiện nguyên văn.** REPLAY khác LIVE thế nào là một phần của dữ
  liệu: bỏ nó đi là làm người xem hiểu sai con số.
- **Đếm ngược tính từ "số giây còn lại" server trả về**, không từ đồng hồ máy người dùng
  (đồng hồ lệch vài phút là đếm sai).
- **Biểu đồ giá vẽ dạng bậc thang**, vì giá giữ nguyên tới lần công bố kế tiếp; nối thẳng
  sẽ vẽ ra những mức giá chưa từng có.
- **ECharts nạp theo trang.** Trang tổng quan không có biểu đồ nên không phải tải ECharts.

## Đóng gói

`Dockerfile` build bằng Node rồi chạy bằng nginx (không phải root). `nginx.conf` chuyển
`/api` sang service `app` và trả `index.html` cho mọi đường dẫn khác để React Router xử lý.
Xem `deploy/docker-compose.prod.yml` và `docs/deploy.md`.
