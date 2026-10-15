# Hai giai đoạn: build bằng JDK đầy đủ, chạy bằng JRE nhỏ. Image cuối không chứa
# mã nguồn, Maven hay JDK, chỉ có JRE và một file jar.

# ───────────────────────────── build ─────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Chép riêng pom và wrapper trước, để lớp "tải thư viện" được cache: sửa mã nguồn
# không làm tải lại toàn bộ thư viện Maven.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -ntp dependency:go-offline

COPY src src
# Bỏ test ở đây: CI đã chạy test, và test Spring cần một PostgreSQL thật mà bước
# build image không có.
RUN ./mvnw -B -ntp -o -DskipTests package \
 && cp target/fuelcast-core-*-SNAPSHOT.jar /workspace/app.jar

# ───────────────────────────── runtime ─────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Không chạy bằng root: nếu ứng dụng bị khai thác thì kẻ tấn công không có quyền root.
RUN addgroup -S fuelcast && adduser -S fuelcast -G fuelcast
COPY --from=build /workspace/app.jar app.jar
USER fuelcast

EXPOSE 8080

# Lấy bộ nhớ theo giới hạn của container thay vì theo cả máy chủ.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Ho_Chi_Minh"

ENTRYPOINT ["java", "-jar", "app.jar"]
