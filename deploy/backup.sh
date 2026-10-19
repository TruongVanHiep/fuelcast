#!/usr/bin/env bash
# Sao lưu DB fuelcast. Dự báo LIVE là dữ liệu không tái tạo được (dấu thời gian "ghi trước
# bản tin" không dựng lại được), nên đây là thứ quan trọng nhất trên máy chủ.
#
# Chạy tay:        ./backup.sh
# Chạy hằng ngày:  xem docs/deploy.md (crontab)
#
# Biến môi trường tuỳ chọn:
#   BACKUP_DIR           nơi giữ bản sao (mặc định /var/backups/fuelcast)
#   KEEP_DAYS            giữ bao nhiêu ngày (mặc định 14)
#   BACKUP_RSYNC_TARGET  nếu đặt (ví dụ user@host:/duong/dan/), đẩy bản mới ra máy khác.
#                        Bản sao nằm cùng ổ đĩa với DB thì mất ổ là mất cả hai.

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/fuelcast}"
KEEP_DAYS="${KEEP_DAYS:-14}"
COMPOSE_DIR="$(cd "$(dirname "$0")" && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/fuelcast-$STAMP.dump"

mkdir -p "$BACKUP_DIR"

# -Fc: định dạng nén của pg_dump, khôi phục được bằng pg_restore.
# --no-owner --no-acl: không ghi tên chủ sở hữu hay quyền vào dump, để khôi phục sang
# máy khác (có thể khác tên role) không bị lỗi.
# -T: tắt cấp pseudo-TTY, bắt buộc khi chạy trong cron, nếu không dữ liệu nhị phân bị hỏng.
# Ghi ra file tạm rồi mới đổi tên, để một lần dump hỏng giữa chừng không để lại
# file trông như bản sao hợp lệ.
docker compose -f "$COMPOSE_DIR/docker-compose.prod.yml" exec -T postgres \
  pg_dump -U fuelcast -Fc --no-owner --no-acl fuelcast > "$OUT.partial"

# Một dump rỗng là dấu hiệu hỏng; dừng lại, không xoá các bản cũ vì chúng có thể là bản duy nhất.
if [ ! -s "$OUT.partial" ]; then
  rm -f "$OUT.partial"
  echo "Dump rỗng, dừng lại." >&2
  exit 1
fi
mv "$OUT.partial" "$OUT"
echo "Đã ghi $OUT ($(du -h "$OUT" | cut -f1))"

# Xoá bản cũ hơn KEEP_DAYS ngày, chỉ khi bản mới ở trên đã thành công (set -e bảo đảm điều đó).
find "$BACKUP_DIR" -name 'fuelcast-*.dump' -mtime +"$KEEP_DAYS" -delete

if [ -n "${BACKUP_RSYNC_TARGET:-}" ]; then
  rsync -a "$OUT" "$BACKUP_RSYNC_TARGET"
  echo "Đã đẩy sang $BACKUP_RSYNC_TARGET"
fi
