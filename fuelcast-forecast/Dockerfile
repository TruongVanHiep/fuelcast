# Một giai đoạn là đủ: không có bước biên dịch nào, chỉ cài thư viện rồi chép mã.

FROM python:3.12-slim

# LightGBM cần thư viện OpenMP của hệ điều hành. Ảnh "slim" không có sẵn, thiếu nó thì
# "import lightgbm" lỗi ngay khi khởi động.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libgomp1 \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /srv

# Chép riêng requirements trước để lớp "cài thư viện" được cache: sửa mã nguồn không
# làm cài lại toàn bộ thư viện.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app app

# Không chạy bằng root.
RUN useradd --system --no-create-home forecast
USER forecast

EXPOSE 8000

# Slim không có curl; dùng chính Python để hỏi /health.
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8000/health', timeout=3).status == 200 else 1)"

CMD ["uvicorn", "app.api:app", "--host", "0.0.0.0", "--port", "8000"]
