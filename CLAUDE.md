# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# ReelsBot
It is Telegram bot for downloading short videos. The user sends a link to a video, the bot downloads it with yt-dlp and sends the video file back to the chat. Main targets are YouTube Shorts, TikTok, Instagram Reels and VK Clips. The main goal is minimal response latency.

## Scope Decisions
Decided by the owner; they override the original MVP spec.
- No platform whitelist for now: any link yt-dlp can download is accepted.
- No duration or size limits for now. The real ceiling is the Telegram Bot API upload limit of 50 MB (up to 2 GB with a Local Bot API Server, see roadmap).
- Quality: up to 720p, prefer H.264 + AAC in mp4 (plays in all Telegram clients).
- All limits are configurable in `application.yaml`, never hardcoded.
- Group chats are postponed: privacy mode is on, so in groups the bot only sees commands and replies to itself.
- Bot texts address the user informally («ты»), plain tone, no jokes.

## Tech Stack
- Java 25
- Maven
- Spring Boot 4
- TelegramBots (rubenlagus): `telegrambots-longpolling`, `telegrambots-client`, `telegrambots-extensions` (commands)
- yt-dlp (external process) + ffmpeg + Deno (JS runtime, required for YouTube)
- Caffeine: in-memory state for limits now, `file_id` cache later

## Architecture
```
Telegram update
    ↓
AntispamService (anti-spam)
    ↓
Router: command → CommandRegistry | link → LinkRequestHandler | other text → hint
    ↓
Limits: bot-wide queue → per-user quotas (planned)
    ↓
Cache check by file_id (planned)
    ↓
yt-dlp download → SendVideo → temp files cleanup
```

## Commands
```bash
export TELEGRAM_BOT_TOKEN=...      # required: startup fails validation without it
./mvnw spring-boot:run             # run the bot
./mvnw clean package               # build (runs tests)
./mvnw -DskipTests package         # build without tests
./mvnw test -Dtest=ReelsProjectApplicationTests#contextLoads  # single test

# Docker (image = JRE 25 + yt-dlp + ffmpeg + Deno), run from deploy/ with TELEGRAM_BOT_TOKEN exported
docker compose up --build          # build and run the bot
docker compose build --no-cache    # update yt-dlp (unpinned, installed at image build)
```
No linter or formatter is configured. Running without Docker requires `yt-dlp`, `ffmpeg` and `deno` on `PATH` (or `DOWNLOADER_YT_DLP_PATH`).

## Current Implementation
Implemented so far: `/start`, link → download → send → cleanup, parallel processing, "sending video" status, anti-spam, bot-wide download queue limit. Not yet: per-user quotas, specific error messages, blocking the generic extractor, cache.
- `ReelsBot` is only a router: every text message first goes through `AntispamService`, then a command → `CommandRegistry`, the first `url`/`text_link` entity → `LinkRequestHandler`, anything else → a hint. Text replies go through `ChatReplier`.
- Commands are `IBotCommand` beans in `command/`, registered in `CommandRegistry` by `TelegramBotConfig` (startup fails on duplicate names). `ReelsBot.initCommands()` publishes the menu via `SetMyCommands` in `@Order` order (the registry is a `HashMap` and loses order); a menu failure is only logged. `/language` and `/donate` are empty stubs.
- `/start` → `StartCommand` sends `welcome.gif` from resources with the welcome text as caption. The GIF is uploaded once; the `file_id` from Telegram's reply is reused afterwards. If the animation fails, the greeting goes out as plain text.
- Limits live in `antispam/` and are configured under `limits:` (`limits.antispam`, `limits.queue`); user-facing limit texts are all in `LimitMessages`. `AntispamService` returns `AntispamResult` (`ALLOWED` / `MUTED` once when the limit is hit / `IGNORED` while muted): more than `max-messages` per `window` → the bot ignores the user for the next duration from `mutes` (15m → 1h → 24h); the count restarts when `forgive-after` passes after the end of the last mute. Per-user state is in a Caffeine cache kept for longest mute + `forgive-after`, in memory only (Caffeine refreshes access time with ~1 s precision, so test it with second-scale durations). `DownloadQueueLimit`: at most `queue.max-size` links in work bot-wide (downloading + waiting); extra links are rejected right away.
- `LinkRequestHandler` checks the limits, then `VideoDeliveryService` downloads via `YtDlpDownloader` and sends with `SendVideo` (width/height/duration passed explicitly so vertical videos keep their aspect ratio). Any `DownloadException`/`TelegramApiException` → one generic reply.
- `VideoDeliveryService` limits concurrent download + send with a fair `Semaphore` (`downloader.max-concurrent-downloads`); the slot is held until the temp files are deleted, so it also bounds disk usage. While a request waits or works, a separate virtual thread repeats the `upload_video` chat action every 4 s via `executeAsync`, so stopping it never interrupts HTTP I/O.
- `YtDlpDownloader` runs one yt-dlp process per request in its own temp dir under `downloader.work-dir`; stdout/stderr go to files in that dir, the result comes back as JSON via `--print after_move:...`. The process is killed after `downloader.timeout`. `DownloadedVideo` is `AutoCloseable` and deletes the whole dir.
- Format sort is `vcodec:h264` before `res:<max-height>` on purpose: TikTok often offers 720p only in HEVC, and H.264 compatibility wins over resolution.
- `ReelsBot` implements `LongPollingUpdateConsumer` directly (the library's `DefaultLongPollingUpdateConsumer` is single-threaded): each update is handled in its own virtual thread. `close()` waits for started deliveries, because Telegram has already confirmed those updates and won't resend them.
- Site blocks depend on the server IP, not on code: from a datacenter IP Instagram redirects to login (`is_from_rle`) and answers 429, YouTube may ask "Sign in to confirm you're not a bot". The owner declined cookies; the options left are a proxy for Instagram or another server.
- Token flow: `TELEGRAM_BOT_TOKEN` → `TelegramBotProperties` (validated record, `toString()` masks the token) → `TelegramBotConfig`, the only place the token is used directly. It builds the `TelegramClient` (OkHttp), `botUser` (`GetMe`, also gives the username for `/cmd@bot`), `CommandRegistry` and `TelegramBotsLongPollingApplication` beans; everything else gets `TelegramClient` via DI.
- Starting the Spring context makes a real Telegram API request (`GetMe`) — this includes `ReelsProjectApplicationTests` (`@SpringBootTest` with a fake token).
- JPA/PostgreSQL dependencies and `compose.yaml` exist, but `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration` and docker compose are disabled in `application.yaml` until the first `@Entity` appears.

## Roadmap
Work goes in stages on branch `feature-1`; each stage is reviewed separately and the owner commits. Implement only what is listed here.
1. ✅ Link → download → send → cleanup, Docker.
2. ✅ `/start` with GIF, command menu.
3. Clear error messages: download too long, unsupported link, private/deleted/unavailable, site not responding, photo-only post, file too large, any other failure. Block yt-dlp's generic extractor and live streams.
4. ✅ Parallel processing, "sending video" status, concurrent download limit.
5. Per-user limits: (a) ✅ anti-spam and bot-wide queue; (b) quotas — 3 links at once then 1 per 20 s, 25 per hour, 50 per day, 3 GB per day; (c) the owner's Telegram ID is exempt from limits.
6. Cache: repeated link → `file_id` without downloading; "this link is already downloading".
7. Local Bot API Server: uploads up to 2 GB.
8. Pick the best quality that fits the size limit (1080/720/480/360).
9. Server: run on the VPS again, daily yt-dlp update, decide on Instagram.
10. Group chats (postponed): per-chat queue (one video at a time, 3 waiting in private chats, 5 in groups, 👀 reaction), no error spam in groups.

## Code Style Rules
- One class per concern (for example one class per limit), no god classes; each method does one thing. Short comments only where the logic is not obvious.
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
- There is no host whitelist, so yt-dlp's generic extractor must be disabled (`--use-extractors default,-generic`) before the bot runs on a server again; otherwise any URL, including internal addresses, is fetched (SSRF). Planned in stage 3.

## Testing
Don't add unit tests for this project.
