# Hai giai đoạn: build bằng Node, chạy bằng nginx. Image cuối chỉ có nginx và các file
# tĩnh đã build, không có Node, mã nguồn hay node_modules.

# ───────────────────────────── build ─────────────────────────────
FROM node:24-alpine AS build
WORKDIR /workspace

# Chép riêng package*.json trước để lớp "cài thư viện" được cache: sửa mã nguồn không
# làm cài lại toàn bộ thư viện. "npm ci" cài đúng phiên bản trong package-lock.json,
# khác "npm install" có thể tự nâng phiên bản.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ───────────────────────────── runtime ─────────────────────────────
# Bản "unprivileged": nginx chạy không bằng root, nghe cổng 8080 (cổng dưới 1024 cần root).
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 8080
