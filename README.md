# Squads

A native Android client for Microsoft Teams & Outlook — built with Jetpack Compose and Material 3.

Squads lets you access your chats, emails, calendar, and teams through a clean, fast, alternative interface. It authenticates via Microsoft's device code OAuth flow and talks directly to the Graph API and Teams APIs.

## Features

- **Chats** — real-time conversations with message polling, inline images, reactions, reply quotes, and profile photos
- **Mail** — inbox with importance badges, attachment indicators, and detail view
- **Calendar** — today/week view with event details, attendees, and meeting links
- **Teams** — hierarchical navigation through teams, channels, and messages
- **Search** — cross-service search across chats and mail
- **Demo mode** — explore the app with realistic sample data, no account needed

## Screenshots

*Coming soon*

## Requirements

- Android 8.0+ (API 26)
- A Microsoft work/school account (organization accounts only)
- [just](https://github.com/casey/just) command runner (optional, for dev commands)

## Getting started

```bash
# Clone
git clone https://github.com/user/squads-app.git
cd squads-app

# Build and install on a connected device
just run

# Or use Gradle directly
./gradlew installDebug
```

On first launch, sign in with a Microsoft work/school account via the device code flow, or tap **Continue with demo data** to explore without credentials.

## Development

```bash
just              # list all available commands
just build        # build debug APK
just run          # build + install + launch
just lint         # run lint checks
just ktlint       # check code style
just format       # auto-format code
just test         # run unit tests
just logcat       # filtered logcat output
just restart      # kill + relaunch
```

## Tech stack

| Layer | Tech |
|-------|------|
| UI | Jetpack Compose, Material 3, Material You |
| Navigation | Navigation Compose |
| Architecture | MVVM, StateFlow, coroutines |
| DI | Hilt |
| Auth | Microsoft OAuth device code flow |
| API | Microsoft Graph, Teams Chat Service, IC3 |
| HTML | Jsoup |
| Effects | Haze (glassmorphism nav bar) |
| Linter | ktlint via ktlint-gradle |
| Font | Inter via Google Fonts |

## Project structure

```
app/src/main/java/com/squads/app/
├── auth/           # OAuth flow & token management
├── data/           # API client, data models, HTML parser, emoji manager
├── di/             # Hilt dependency injection
├── ui/
│   ├── auth/       # Login screen
│   ├── calendar/   # Calendar views
│   ├── chats/      # Chat list & detail
│   ├── components/ # Shared UI (Avatar, badges, loading)
│   ├── mail/       # Mail list & detail
│   ├── profile/    # Profile & settings
│   ├── search/     # Search screen
│   ├── teams/      # Teams, channels, messages
│   └── theme/      # Colors, typography
└── viewmodel/      # ViewModels for each feature
```

## License

Private — not open source.
