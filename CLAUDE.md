# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# ReelsBot
It is Telegram bot for downloading short videos. The user sends a link to a video (YouTube Shorts, TikTok, Instagram Reels, VK Clips), the bot downloads it with yt-dlp and sends the video file back to the chat. The main goal is minimal response latency.

## Supported Platforms
Only these platforms are accepted (whitelist). Any other link is rejected.
| Platform | Link formats |
|---|---|
| YouTube | `youtube.com/shorts/{id}`, `youtube.com/watch?v={id}`, `youtu.be/{id}`, `m.youtube.com/...` |
| TikTok | `tiktok.com/@{user}/video/{id}`, `vm.tiktok.com/{code}`, `vt.tiktok.com/{code}` |
| Instagram | `instagram.com/reel/{code}`, `instagram.com/reels/{code}`, `instagram.com/p/{code}` (video posts only) |
| VK | `vk.com/clip{owner}_{id}`, `vk.com/video{owner}_{id}`, `vkvideo.ru/clip...`, `vkvideo.ru/video...`, `m.vk.com/...` |

## Video Limits
All limits are configurable (`application.yaml`), never hardcoded.
- Max duration: **3 minutes (180 s)** — covers YouTube Shorts and Instagram Reels (both up to 3 min)
- Max file size: **50 MB** — Telegram Bot API upload limit (up to 2 GB with a Local Bot API Server, later)
- Max quality: **720p**, prefer H.264 + AAC in mp4 (plays in all Telegram clients)
  Videos over the limit are rejected with a clear message, without downloading.

## Tech Stack
- Java 25
- Maven
- Spring Boot 4
- TelegramBots (rubenlagus)
- yt-dlp (external process) + ffmpeg + Deno (JS runtime, required for YouTube)
- Storage for cache in-memory (Caffeine)

## Architecture
```
Telegram Bot
    ↓
URL Validation
    ↓
Cache Check (file_id)
    ↓
Video Download
    ↓
Telegram Video Sending
    ↓
File Cleanup
```

## Commands
```bash
export TELEGRAM_BOT_TOKEN=...      # required: startup fails validation without it
./mvnw spring-boot:run             # run the bot
./mvnw clean package               # build (runs tests)
./mvnw -DskipTests package         # build without tests
./mvnw test -Dtest=ReelsProjectApplicationTests#contextLoads  # single test
```
No linter or formatter is configured.

## Current Implementation
Implemented so far: link → download → send → cleanup. Not yet: `/start`, URL whitelist, duration/size limits, specific error messages, chat action, concurrency, cache.
- `ReelsBot` takes the first `url`/`text_link` entity of a message and calls `VideoDeliveryService`, which downloads via `YtDlpDownloader` and sends with `SendVideo` (width/height/duration passed explicitly so vertical videos keep their aspect ratio). Any `DownloadException`/`TelegramApiException` → one generic reply.
- `YtDlpDownloader` runs one yt-dlp process per request in its own temp dir under `downloader.work-dir`; stdout/stderr go to files in that dir, the result comes back as JSON via `--print after_move:...`. The process is killed after `downloader.timeout`. `DownloadedVideo` is `AutoCloseable` and deletes the whole dir.
- Format sort is `vcodec:h264` before `res:<max-height>` on purpose: TikTok often offers 720p only in HEVC, and H.264 compatibility wins over resolution.
- Updates are processed sequentially: `DefaultLongPollingUpdateConsumer` uses a single-thread executor.
- YouTube may answer "Sign in to confirm you're not a bot" depending on the IP; that needs cookies, not a code fix.
- Token flow: `TELEGRAM_BOT_TOKEN` → `TelegramBotProperties` (validated record, `toString()` masks the token) → `TelegramBotConfig`, the only place the token is used directly. It builds the `TelegramClient` (OkHttp) and `TelegramBotsLongPollingApplication` beans; everything else gets `TelegramClient` via DI.
- Bot registration calls `GetMe` at startup, so starting the Spring context makes a real Telegram API request — this includes `ReelsProjectApplicationTests` (`@SpringBootTest` with a fake token).
- JPA/PostgreSQL dependencies and `compose.yaml` exist, but `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration` and docker compose are disabled in `application.yaml` until the first `@Entity` appears.

## MVP Features
Implement only the features listed here.
- `/start` — greeting and a short instruction: send a link, get a video
- User sends a link → bot replies with the video file
- While processing, the bot shows the "sending video..." status
- Repeated link → video is sent instantly from cache, without downloading
- Multiple users can send links at the same time without waiting for each other
- Error messages for the user:
    - unsupported link or platform
    - video is longer than 3 minutes or larger than 50 MB
    - video is private, deleted or unavailable
    - download failed — try again later
    - download took too long — try again later

## Code Style Rules
- Don't use Lombok `@Data` on JPA entities: it breaks equals/hashCode with lazy loading
- Constructor injection only, no field `@Autowired`
- Fields are `private final` by default
- Use records for properties files and value objects instead of classes with getters
- Use classes for DTO
- Repository and service methods that may not find an object return `Optional<T>`
- Never use `Optional` as a field or method parameter

## Security
- Never store secrets in source code.
- Telegram credentials must come from environment variables.
- Never log the Telegram bot token.
- Validate supported URL hosts before downloading.

## Testing
Don't add unit tests for this project.
