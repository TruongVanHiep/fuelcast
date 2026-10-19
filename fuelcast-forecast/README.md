# fuelcast-forecast

Dịch vụ dự báo của FuelCast: Python, FastAPI, LightGBM. Không giữ trạng thái và không đọc DB:
`fuelcast-core` gửi chuỗi các kỳ điều hành giá trong mỗi request, dịch vụ huấn luyện lại rồi
trả kết quả. Dữ liệu chỉ vài chục đến vài trăm dòng nên mỗi lần huấn luyện mất cỡ vài chục mili
giây, và không có "mô hình đã lưu" nào có thể lỗi thời so với dữ liệu.

**Trạng thái:** đã nối vào `fuelcast-core` (xem mục *Tích hợp*). Mô hình Java vẫn là mô hình
chính của hệ thống; mô hình Python chạy song song để so sánh.

## Mô hình

Hai lớp, theo ý tưởng "ML hiệu chỉnh phần dư" của tài liệu thiết kế:

1. **Lớp luật** (`app/rules/base_price.py`): mức đổi giá bán lẻ (đồng/lít) = hệ số × mức đổi
   giá thế giới (USD/thùng) + hằng số, học bằng bình phương tối thiểu. Công thức giá cơ sở đầy
   đủ của thiết kế cần giá Platts theo ngày, thuế, premium nhập khẩu; dự án không có nguồn đó,
   nên lớp luật chỉ là phần truyền dẫn đo được từ dữ liệu có thật.
2. **Lớp phần dư** (`app/models/lgbm.py`): LightGBM đoán phần mà lớp luật bỏ sót. Mọi tham số
   chọn theo hướng chống quá khớp (cây sâu 2, mỗi lá tối thiểu 10 mẫu, 60 vòng, học chậm,
   `deterministic`), và không huấn luyện khi có dưới 30 dòng.

Đặc trưng (`app/features.py`): giá thế giới đầu vào và kỳ liền trước, mức đổi giá bán lẻ của hai
kỳ trước, tháng.

### Hai chế độ, và vì sao phải phân biệt

| Chế độ | Giá thế giới dùng để đoán kỳ i | Ý nghĩa |
|---|---|---|
| `lagged` | của kỳ **i-1** | Thứ duy nhất có trước giờ công bố: **dự báo thật** (LIVE) |
| `same_period` | của chính kỳ **i** | Chỉ đầy đủ khi bản tin đã ra: **giải thích** một kỳ đã biết (REPLAY, horizon 0) |

`/predict` chỉ chấp nhận `lagged`. Đây là cách ngăn chính cái bẫy đã ghi ở mục *Bài toán khó
nhất* trong README gốc: một mô hình "đúng 98%" mà thật ra chỉ giải thích dữ liệu đã biết.

## API

| Endpoint | Việc làm |
|---|---|
| `GET /health` | Tình trạng và phiên bản mô hình |
| `POST /predict` | Đoán mức đổi giá của kỳ **sắp tới**; trả kèm phần luật, phần dư và lời giải thích cộng lại đúng bằng mức đổi dự báo |
| `POST /backtest` | Walk-forward trên chuỗi đã gửi, so với "không đổi" và với lớp luật đứng một mình |

Tài liệu tương tác ở `/docs` khi chạy. Lỗi dữ liệu trả `422` với trường `detail` (ví dụ không đủ
cặp liền kề để học, thời điểm thiếu múi giờ, chuỗi không tăng dần).

## Chạy

Cần Python 3.12 trở lên.

```bash
python -m venv .venv
.venv/Scripts/activate            # Linux/macOS: source .venv/bin/activate
pip install -r requirements-dev.txt
pytest
uvicorn app.api:app --port 8000   # http://localhost:8000/docs
```

Hoặc bằng Docker: `docker build -t fuelcast-forecast .` rồi `docker run -p 8000:8000 fuelcast-forecast`.

## Tích hợp với fuelcast-core

Bật bằng `fuelcast.forecast.python.enabled=true` (mặc định tắt, để test và các lệnh chạy một lần
không đòi một dịch vụ thứ hai) và `fuelcast.forecast.python.url`. Trong `docker compose` cả hai
đã được đặt sẵn, địa chỉ là `http://forecast:8000`.

- **Dự báo LIVE.** Sau mỗi lượt quét, core gửi chuỗi kỳ của từng mặt hàng tới `/predict` và ghi kết
  quả vào cùng bảng `forecast` với `model_version = rule-lgbm-residual-v1`. Chuỗi kỳ là chính
  chuỗi mà mô hình Java dùng (`BacktestService.alignPeriods`), nên hai mô hình học trên cùng
  tập dữ liệu. Bản tin ra thì cả hai được chấm bằng cùng một bản tin.
- **Dự báo REPLAY.** `--fuelcast.forecast.python.replay=true` gọi `/backtest` ở chế độ `lagged`
  và ghi từng lần đoán lịch sử với **horizon 7**: giá thế giới của kỳ trước chỉ đầy đủ vào lúc
  công bố kỳ trước, tức khoảng một kỳ (7 ngày) trước lần công bố đang đoán. Horizon 0 dành
  riêng cho "giá thế giới cùng kỳ" của mô hình Java; ghi 0 ở đây sẽ gắn nhãn sai thành giải thích.
- **Chịu lỗi.** Dịch vụ này hỏng, chậm hay tắt thì core chỉ mất dòng dự báo của nó: mô hình Java
  luôn được ghi trước và không bị ảnh hưởng. Mã `422` (chưa đủ dữ liệu) được coi là "không dự
  báo", còn lỗi mạng và `5xx` được ghi cảnh báo thay vì nuốt im lặng.
- **Hiển thị.** `/api/v1/forecasts/next` trả một dòng cho mỗi (mặt hàng, mô hình) và đánh dấu
  `primary` cho mô hình chính; tổng quan chỉ nêu mô hình chính, trang Dự báo nêu cả hai. Hai mô
  hình có thể bất đồng về chiều: ngày 19/10/2026, với kỳ 22/10, E5RON92 và RON95 được mô hình Java
  dự báo giảm còn mô hình Python dự báo tăng.

## Kiểm thử

34 test, gồm những cái đáng chú ý:

- **Chống leakage:** làm hỏng mọi dữ liệu *sau* kỳ K thì các lần đoán tới kỳ K không được đổi,
  ở cả hai chế độ.
- **Không bịa ra kỹ năng:** trên dữ liệu nhiễu thuần, mô hình không được giảm sai số quá 10%
  so với "không đổi".
- **Cái bẫy trong một test:** cùng một dữ liệu, `same_period` giảm sai số hơn 50% còn `lagged`
  dưới 15%.
- **Xác định:** cùng dữ liệu cho cùng kết quả, để backtest và dự báo không lệch nhau.

## Đo trên dữ liệu thật

Xuất dữ liệu từ DB (chạy ở thư mục gốc dự án) rồi chạy script:

```bash
docker exec -i fuelcast-db psql -U fuelcast -d fuelcast -q -c "COPY (
  SELECT p.code, r.observed_at, r.price_vnd, r.delta_vnd, w.price_usd
  FROM retail_price r
  JOIN fuel_product p ON p.id = r.product_id
  JOIN world_price w ON w.observed_at = r.observed_at
   AND w.symbol = CASE p.code WHEN 'E5RON92' THEN 'MOPS_RON92' WHEN 'RON95' THEN 'MOPS_RON95'
        WHEN 'DO_005S' THEN 'MOPS_GASOIL_005' WHEN 'KEROSENE' THEN 'MOPS_KEROSENE' WHEN 'FO_180' THEN 'MOPS_FO_180' END
  WHERE r.region = 1 ORDER BY p.code, r.observed_at) TO STDOUT WITH CSV HEADER" > real.csv
python scripts/real_backtest.py real.csv
```

Kết quả đo ngày 19/10/2026 (MAE, đồng/lít; walk-forward, mỗi kỳ chỉ học từ các kỳ trước):

**Chế độ `lagged`: dự báo thật.**

| Mặt hàng | Số kỳ | Không đổi | Luật | Luật + LightGBM | Đúng chiều (luật / luật+ML) |
|---|---|---|---|---|---|
| E5RON92 | 89 | 390 | 395 | 410 | 43% / 43% |
| RON95 | 88 | 418 | 424 | 452 | 44% / 41% |
| Dầu diesel | 90 | 432 | 424 | 436 | 58% / 57% |
| Dầu hỏa | 86 | 421 | 420 | 428 | 57% / 50% |
| Mazut | 92 | 350 | 351 | 364 | 54% / 45% |

**Chế độ `same_period`: giải thích kỳ đã biết, không phải dự báo.**

| Mặt hàng | Số kỳ | Không đổi | Luật | Luật + LightGBM | Đúng chiều (luật / luật+ML) |
|---|---|---|---|---|---|
| E5RON92 | 98 | 445 | 90 | 92 | 98% / 96% |
| RON95 | 96 | 465 | 92 | 95 | 100% / 100% |
| Dầu diesel | 99 | 497 | 117 | 117 | 95% / 94% |
| Dầu hỏa | 90 | 508 | 47 | 51 | 98% / 97% |
| Mazut | 100 | 395 | 398 | 216 | 50% / 90% |

Đọc kết quả:

- **Dự báo thật: LightGBM không giúp, và làm tệ hơn một chút.** Thêm lớp phần dư làm sai số
  tăng 2 đến 7% so với lớp luật, và lớp luật cũng chỉ ngang "không đổi". Với vài chục kỳ cho
  mỗi mặt hàng, đây đúng là điều thiết kế đã cảnh báo. Điều kiện để dự báo trước có kỹ năng
  vẫn là có giá dầu *kịp thời* (xem `docs/data-sources.md`), không phải mô hình mạnh hơn.
- **Giải thích kỳ đã biết: lớp luật đã làm gần hết việc**; LightGBM không thêm gì đáng kể.
- **Ngoại lệ đáng ghi lại: mazut.** Lớp luật tuyến tính không giải thích được mazut (giá thế giới
  tính theo USD/**tấn**, đơn vị khác các mặt hàng còn lại), nhưng cây quyết định giảm sai số
  45% và đúng chiều 90%. Đây vẫn là giải thích sau khi đã biết giá thế giới, không phải dự
  báo, nên chưa đủ để kết luận mazut có quy luật phi tuyến dùng được.
- Số kỳ và sai số tuyệt đối ở đây **không so trực tiếp được** với bảng của mô hình Java trong
  README gốc, vì hai bên đánh giá trên các tập kỳ khác nhau (tôi chưa đối chiếu cụ thể từng
  kỳ). Điều so sánh được là các cột trong cùng một bảng.

## Cấu trúc

```
app/
  api.py               FastAPI: /health, /predict, /backtest
  schemas.py           hợp đồng HTTP (pydantic)
  features.py          dựng đặc trưng; nơi duy nhất giữ quy tắc "không nhìn tương lai"
  predictor.py         ghép lớp luật và lớp phần dư
  rules/base_price.py  lớp luật
  models/lgbm.py       lớp phần dư
  backtest/runner.py   walk-forward và các chỉ số
tests/                 34 test
scripts/real_backtest.py   chạy trên dữ liệu thật từ CSV
```

Khác thiết kế ban đầu: không có thư mục `artifacts/` (mô hình huấn luyện lại mỗi request, lý do
ở đầu trang), và dùng `uvicorn` thường chứ không `uvicorn[standard]`, vì một số thư viện phụ
của bản `standard` chưa có bản dựng sẵn cho Python 3.15.
