# Nguồn dữ liệu: đã kiểm gì, kết luận gì

Ghi lại để không phải điều tra lại. Mọi con số dưới đây đo ngày 14/10/2026.

## 1. Bản tin bị thiếu

Kiểm tra tính liên tục (mức đổi nêu trong bản tin phải bằng chênh lệch giá với kỳ trước)
chỉ ra hai chỗ đứt: ngày 25/3/2026 và 2/7/2026.

Đã quét mục tin của moit.gov.vn (mục cha `/tin-tuc`, 15 trang x 100 bài, lùi tới 20/2/2026)
và liệt kê **mọi** bài trong hai khoảng, không lọc theo tiêu đề:

| Khoảng | Bản tin có trên mục tin | Kết luận |
|---|---|---|
| 19/3 đến 25/3/2026 | 19/3, 25/3 (và 26/3) | Không có bản tin nào ở giữa |
| 25/6 đến 2/7/2026 | 25/6, 2/7 | Không có bản tin nào ở giữa |

Nhưng giá **có** đổi ở giữa: bản 19/3 ghi dầu điêzen 33.420 đồng/lít, còn bản 25/3 coi
"giá bán tối đa hiện hành" là 39.666 (tăng 6.246 đồng mà không có bản tin điều hành giá nào
trong 20 đến 24/3). Bản 2/7 viện dẫn một nghị quyết ngày 30/6/2026.

**Kết luận: không tìm lại được từ nguồn này.** Hai kỳ bị loại khỏi phần đánh giá (đúng việc
của bước kiểm tra liên tục). Có thể thử nguồn dự phòng nêu trong tài liệu thiết kế
(thuvienphapluat.vn, công văn BCT-TTTN); chưa làm.

## 2. Số liệu giá thế giới theo ngày

Dữ liệu đang dùng là **trung bình theo kỳ** lấy từ chính bản tin. Muốn dự báo trước sự kiện
cần số liệu theo ngày, kịp thời. Kiểm các ứng viên:

| Nguồn | robots.txt | Lấy được | Độ trễ | Dùng được để dự báo? |
|---|---|---|---|---|
| **Stooq** (tài liệu thiết kế gợi ý) | `User-agent: *` / `Disallow: /` | không kiểm, bị cấm | n/a | **Không**: cấm mọi bot |
| **Yahoo Finance** | chưa kiểm chi tiết | không thử | n/a | **Không**: không có API công khai chính thức; chưa đọc điều khoản, nên không dùng kênh không chính thức |
| **FRED** (`fredgraph.csv`, dữ liệu EIA) | chỉ cấm `fredgraph.png` và vài đường dẫn khác | có, HTTP 200 | **khoảng 8 ngày** (14/10 mới có tới 6/10) | **Không để dự báo**; tốt cho nghiên cứu lịch sử |
| **Vietcombank** (tỷ giá, XML) | `robots.txt` rỗng | có, HTTP 200 | tức thời | Có; ghi chú trong tệp: *chỉ 1 yêu cầu mỗi 5 phút* |

Các chuỗi FRED dùng được cho lịch sử: `DCOILBRENTEU` (Brent), `DGASNYH` (xăng NY Harbor),
`DDFUELNYH` (dầu sưởi NY Harbor), `DJFUELUSGULF` (nhiên liệu phản lực vùng Vịnh Mỹ).

Chưa đọc đầy đủ điều khoản sử dụng của từng nguồn: robots.txt không phải là giấy phép.

## 3. Thí nghiệm: có Brent theo ngày thì dự báo trước được bao nhiêu?

Giả sử có Brent theo ngày một cách kịp thời, biết tới `h` ngày trước giờ công bố. Đặc trưng:
trung bình Brent từ đầu kỳ tới `h` ngày trước giờ công bố, trừ trung bình kỳ liền trước.
Walk-forward (chỉ học từ các kỳ trước), ghép với các kỳ liên tục.

| Mặt hàng | h = 0 | h = 1 | h = 2 | h = 3 | h = 4 |
|---|---|---|---|---|---|
| E5RON92: giảm sai số / đúng chiều | 23% / 78% | 23% / 77% | 24% / 75% | 24% / 71% | 20% / 66% |
| RON95 | 18% / 78% | 19% / 76% | 20% / 76% | 20% / 72% | 13% / 66% |
| Dầu điêzen | 17% / 74% | 18% / 77% | 17% / 78% | 16% / 77% | 12% / 75% |

So với đường cơ sở "giá không đổi", sai số trung bình 442 đến 510 đồng.

Cách đọc:

- Có kỹ năng thật: giảm khoảng 20% sai số và đúng chiều khoảng 75%, ở 1 đến 3 ngày trước.
  Đó là mức mà dự báo bằng thông tin kỳ trước **không** đạt (đúng chiều khoảng 50%).
- Vẫn thua nhiều so với dùng giá thành phẩm của chính kỳ đó (giảm sai số khoảng 80%, đúng chiều
  khoảng 98%): Brent là chỉ báo thô cho xăng và dầu điêzen, thiếu chênh lệch crack spread.
- Thí nghiệm dùng dữ liệu lịch sử nên độ trễ của FRED không ảnh hưởng. Điều kiện để dùng
  được trong dự báo là có nguồn **kịp thời**, mà nguồn miễn phí hợp lệ ở trên thì không.

## 4. Quyết định còn mở

Muốn dự báo `LIVE` có kỹ năng thật cần một nguồn giá dầu theo ngày, kịp thời, được phép dùng.
Chưa có. Ứng viên cần kiểm điều khoản và giới hạn trước khi viết mã: một API có khoá miễn phí
cho giá Brent theo ngày. Việc này cần người dùng tự đăng ký lấy khoá.
