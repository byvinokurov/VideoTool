# ReelsBot

Telegram bot that downloads short videos (YouTube Shorts, TikTok, Instagram Reels, VK Clips) and sends them back to the chat.

## Set bot token

**macOS:** run `open -e ~/.zshrc`, add the line exactly as shown (with `export`, without quotes), save and run `source ~/.zshrc`.

```bash
export TELEGRAM_BOT_TOKEN=123456789:AAH...
```

**Windows:** press `Win + R`, run `rundll32 sysdm.cpl,EditEnvironmentVariables`, add a user variable `TELEGRAM_BOT_TOKEN` with the token and open a new terminal.

Check — prints `ok`:

```bash
printenv TELEGRAM_BOT_TOKEN > /dev/null && echo ok   # macOS
```

```powershell
if ($env:TELEGRAM_BOT_TOKEN) { 'ok' }                # Windows PowerShell
```

## Run bot

```bash
cd deploy
docker compose up --build      # run (Ctrl+C to stop)
docker compose up --build -d   # run in the background
docker compose down            # stop
```
