# syntax=docker/dockerfile:1
# Образ собирается в два этапа (multi-stage build):
#   1. build — компилирует jar из исходников, для этого нужен полный JDK;
#   2. итоговый образ — только запускает jar: JRE, ffmpeg и yt-dlp.
# В итоговый образ попадает лишь второй этап, JDK и исходники в нём не остаются.

# ===== Этап 1: сборка jar =====
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package

# ===== Этап 2: итоговый образ для запуска бота =====
# ffmpeg — склейка видео и звука в mp4; python3 и python3-venv — для установки yt-dlp.
FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg python3 python3-venv \
    && rm -rf /var/lib/apt/lists/*

# yt-dlp ставится в отдельное виртуальное окружение Python (/opt/yt-dlp),
# Для обновления yt-dlp необходимо пересобрать образ без кеша (docker compose build --no-cache).
RUN python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --no-cache-dir "yt-dlp[default,curl-cffi,deno]"
ENV PATH="/opt/yt-dlp/bin:${PATH}"

RUN useradd --create-home --shell /usr/sbin/nologin app
USER app
WORKDIR /home/app
COPY --from=build /workspace/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
