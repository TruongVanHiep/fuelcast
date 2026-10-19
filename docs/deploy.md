# Triển khai fuelcast lên máy chủ

Hướng dẫn từng bước đưa fuelcast lên một VPS Linux, có HTTPS và sao lưu hằng ngày. Các file
cấu hình nằm trong [`deploy/`](../deploy):

| File | Việc nó làm |
|---|---|
| `docker-compose.prod.yml` | Dựng DB, ứng dụng và Caddy. DB và ứng dụng không mở cổng ra ngoài |
| `Caddyfile` | Caddy nhận HTTPS, chuyển vào ứng dụng, tự lấy chứng chỉ |
| `.env.example` | Mẫu cho `.env` (tên miền, mật khẩu DB) |
| `backup.sh` | `pg_dump` hằng ngày, giữ 14 bản |

Máy chủ gợi ý: Ubuntu 24.04, 2 vCPU, 4 GB RAM, vùng Singapore. Các lệnh dưới đây gõ trên
máy chủ (qua SSH) trừ khi có ghi "trên máy bạn".

## 1. Chuẩn bị máy chủ

```bash
ssh root@IP_MAY_CHU
apt update && apt upgrade -y
```

Cài Docker bằng script do chính Docker phát hành (bạn có thể mở
<https://get.docker.com> đọc trước khi chạy):

```bash
curl -fsSL https://get.docker.com | sh
```

Bật tường lửa, chỉ cho SSH, HTTP, HTTPS đi vào. **Cho phép SSH trước khi bật**, nếu không bạn
tự khoá mình ra ngoài:

```bash
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw enable
```

Ghi chú: Docker tự chèn luật riêng và có thể đi vòng qua `ufw` đối với cổng nó đã
publish. Cấu hình ở đây chỉ publish 80 và 443 nên không có gì lộ thêm. Đó cũng là lý do DB
không có `ports:`.

Nên tạo một người dùng thường để làm việc hằng ngày thay vì dùng `root`, và tắt đăng nhập SSH
bằng mật khẩu (chỉ dùng khoá SSH). Các nhà cung cấp VPS thường cho dán khoá SSH ngay lúc tạo máy.

## 2. Tên miền

Đăng ký một tên miền con miễn phí (ví dụ trên duckdns.org), trỏ nó vào IP máy chủ. Kiểm tra
bằng lệnh sau, kết quả phải là IP của máy chủ:

```bash
nslookup fuelcast.duckdns.org
```

Phải trỏ xong trước khi bước 5, nếu không Caddy xin chứng chỉ thất bại.

## 3. Lấy mã nguồn và cấu hình

```bash
git clone https://github.com/TruongVanHiep/fuelcast.git
cd fuelcast/deploy
cp .env.example .env
nano .env
```

Điền `DOMAIN` (tên miền ở bước 2, không có `https://`) và `POSTGRES_PASSWORD` (tạo bằng
`openssl rand -base64 24`). Rút ngắn các lệnh sau bằng cách khai báo file compose một lần
cho cả phiên làm việc:

```bash
export COMPOSE_FILE=docker-compose.prod.yml
```

## 4. Đưa dữ liệu hiện có lên (chỉ làm một lần)

**Mục đích:** giữ nguyên lịch sử và các dự báo LIVE đã ghi, cùng dấu thời gian của chúng.
**Không** chạy lại các runner nhập dữ liệu, vì dấu thời gian "ghi trước bản tin" không dựng
lại được.

Trên máy bạn (PowerShell), tắt container ứng dụng nếu còn chạy rồi dump DB. Làm trong container
rồi `docker cp` ra, vì chuyển hướng `>` của PowerShell làm hỏng dữ liệu nhị phân:

```powershell
docker stop fuelcast-app
docker exec fuelcast-db pg_dump -U fuelcast -Fc --no-owner --no-acl -f /tmp/fuelcast.dump fuelcast
docker cp fuelcast-db:/tmp/fuelcast.dump .\fuelcast.dump
scp .\fuelcast.dump TEN_NGUOI_DUNG@IP_MAY_CHU:~/
```

Trước khi đi tiếp, ghi lại số dòng trên máy bạn để đối chiếu:

```powershell
docker exec fuelcast-db psql -U fuelcast -d fuelcast -c "SELECT kind, count(*) FROM forecast GROUP BY kind;"
```

Trên máy chủ, **chỉ bật DB, chưa bật ứng dụng**. Nếu ứng dụng chạy trước, Flyway tạo schema
rỗng và việc khôi phục sẽ đụng nó:

```bash
docker compose up -d postgres
docker compose cp ~/fuelcast.dump postgres:/tmp/fuelcast.dump

# TimescaleDB cần được báo là sắp khôi phục, rồi báo là đã xong.
docker compose exec postgres psql -U fuelcast -d fuelcast -c "SELECT timescaledb_pre_restore();"
docker compose exec postgres pg_restore -U fuelcast -d fuelcast --no-owner --no-acl /tmp/fuelcast.dump
docker compose exec postgres psql -U fuelcast -d fuelcast -c "SELECT timescaledb_post_restore();"
```

`pg_restore` sẽ in vài dòng lỗi kiểu `extension "timescaledb" already exists` rồi thoát với mã
khác 0. Đó là bình thường: extension đã được image tạo sẵn. Điều cần xem là có lỗi nào *khác*
những dòng liên quan extension hay không.

Đối chiếu kết quả với con số đã ghi ở máy bạn, phải khớp:

```bash
docker compose exec postgres psql -U fuelcast -d fuelcast -c "SELECT kind, count(*) FROM forecast GROUP BY kind;"
```

## 5. Bật ứng dụng

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

Lần build đầu mất vài phút vì Maven tải thư viện. Khi log báo `Started FuelcastCoreApplication`,
mở `https://TEN_MIEN_CUA_BAN` trên trình duyệt. Lần đầu Caddy cần vài giây để lấy chứng chỉ:
xem tiến độ bằng `docker compose logs caddy`.

Từ đây chỉ **một** bản ứng dụng được bật bộ nạp bản tin (`FUELCAST_INGESTION_POLL_ENABLED`):
bản trên máy chủ. Bản trên máy bạn mặc định tắt, đừng bật lên, nếu không hai nơi cùng ghi
vào hai DB khác nhau.

## 6. Sao lưu hằng ngày

```bash
chmod +x ~/fuelcast/deploy/backup.sh
~/fuelcast/deploy/backup.sh      # chạy thử một lần, xem có ra file không
crontab -e
```

Thêm vào cuối crontab (máy chủ thường chạy giờ UTC, 20:30 UTC là 03:30 giờ Việt Nam):

```
30 20 * * * BACKUP_DIR=$HOME/backups $HOME/fuelcast/deploy/backup.sh >> $HOME/backup.log 2>&1
```

Bản sao nằm cùng ổ đĩa với DB thì chỉ chống được lỡ tay, không chống được mất máy chủ. Lấy
về máy bạn định kỳ (PowerShell, trên máy bạn):

```powershell
scp "TEN_NGUOI_DUNG@IP_MAY_CHU:~/backups/fuelcast-*.dump" D:\backups\fuelcast\
```

Hoặc đặt `BACKUP_RSYNC_TARGET` trong dòng cron để máy chủ tự đẩy sang nơi khác.

Một bản sao chưa từng khôi phục thử thì chưa chắc dùng được. Thỉnh thoảng khôi phục nó vào
một DB tạm để kiểm tra.

## 7. Cập nhật phiên bản mới

```bash
cd ~/fuelcast
git pull
cd deploy
docker compose up -d --build
```

DB và chứng chỉ nằm trong volume nên không mất khi dựng lại. **Không** chạy
`docker compose down -v`: cờ `-v` xoá luôn volume, tức là xoá DB.

## Xử lý sự cố

| Triệu chứng | Kiểm tra |
|---|---|
| Trình duyệt báo không kết nối | `ufw status` có 80/443; `docker compose ps` caddy đang chạy |
| Cảnh báo chứng chỉ | `docker compose logs caddy`; tên miền đã trỏ đúng IP chưa (`nslookup`) |
| Ứng dụng khởi động lại liên tục | `docker compose logs app`; thường do sai `POSTGRES_PASSWORD` so với lúc tạo DB |
| `compose` báo thiếu biến | file `.env` thiếu `DOMAIN` hoặc `POSTGRES_PASSWORD` |
| Đầy ổ đĩa | `df -h`; `du -sh ~/backups`; log Docker đã bị giới hạn 10 MB x 3 |
