# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg python3 python3-venv \
    && rm -rf /var/lib/apt/lists/*

# Версия yt-dlp не фиксируется: сайты часто меняются, поэтому для обновления
# достаточно пересобрать образ без кеша (docker compose build --no-cache).
# Extra "deno" ставит JS-рантайм, нужный yt-dlp для YouTube.
RUN python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --no-cache-dir "yt-dlp[default,curl-cffi,deno]"
ENV PATH="/opt/yt-dlp/bin:${PATH}"

RUN useradd --create-home --shell /usr/sbin/nologin app
USER app
WORKDIR /home/app
COPY --from=build /workspace/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
