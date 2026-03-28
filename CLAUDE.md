# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
just run          # build + install + launch on connected device
just build        # debug APK
just release      # release APK (minified + R8)
just format       # auto-format with ktlint
just ktlint       # check code style
just lint         # Android lint
just test         # unit tests
just test-device  # instrumented tests (requires device/emulator)
just maestro      # run all Maestro UI test flows
just logcat       # filtered logcat for com.squads.app
just restart      # kill + relaunch
just ship 0.3.0   # tag, commit, push, wait for CI, download APK
```

Gradle directly: `./gradlew assembleDebug`, `./gradlew installDebug`, `./gradlew ktlintCheck`.

Maestro binary: `~/.maestro/bin/maestro` (not in PATH). Run individual flows with `~/.maestro/bin/maestro test .maestro/<flow>.yaml`.

## Architecture

MVVM with Jetpack Compose. Single-activity app (`MainActivity`) → `SquadsApp()` composable root → conditional auth gate → `MainApp` with 5 bottom-nav tabs.

**Navigation**: Navigation 3 with type-safe serializable routes (`ui/navigation/Routes.kt`). `NavigationState` manages independent back stacks per tab. `SharedViewModelStoreNavEntryDecorator` enables ViewModel sharing between parent/child entries.

**Data flow**: Domain-focused API classes (`TeamsApiClient` for chat/presence/teams ~790 LOC, `MailApi`, `CalendarApi`) → Repositories (`ChatRepository`, `MailRepository`) → Room DB → ViewModels expose `StateFlow` → Compose UI collects state. Shared JSON helpers in `JsonExtensions.kt` (`str()`, `objects()`).

**Real-time**: `TrouterClient` maintains a WebSocket to Teams' Trouter service for instant message notifications and presence updates. Falls back to polling (15-60s for chat list, 10-30s for active chat, 5min for presence). `NetworkMonitor` provides reactive connectivity state.

**Auth**: Microsoft device code OAuth flow via `AuthManager`. Uses a public client ID (no client secret). Tokens cached in-memory by scope with 60s expiry buffer, refresh token persisted in SharedPreferences.

**Demo mode**: `authManager.mockLogin()` sets a sentinel refresh token (`"mock_refresh_token"`). `TeamsApiClient` checks `isDemoMode` at the top of every public API method and routes to `MockRepository` for demo data. Check `authViewModel.isDemoMode` to branch UI behavior.

**Session lifecycle**: Hilt-scoped ViewModels survive logout (scoped to Activity, not Compose). Each ViewModel observes `AuthManager.onSessionStart()` (a `Flow` that emits on login after logout) to reinitialize. `ChatsViewModel` also handles logout teardown (cancel polling, stop Trouter). `AuthViewModel.logout()` orchestrates cleanup: clear Room DB + disk cache on IO, then clear API client state + memory cache, then reset auth.

## Key Conventions

- **Compose**: Material 3 with Inter font (Google Fonts). Haze library for glassmorphism nav bar.
- **Shared components** live in `ui/components/` — `Avatar`, `GroupAvatar`, `ScreenHeader`, `UnreadBadge`, `PresenceBadge`, `LoadingScreen`, etc. Use `ScreenHeader(title, actions)` for tab headers (ensures consistent 48dp min height).
- **ktlint**: Android mode, Composable function naming rule suppressed (`.editorconfig`). Run `just format` before committing.
- **DI**: Hilt. `@HiltViewModel` on ViewModels, `AppModule` provides OkHttpClient, ImageLoader (Coil 3 with auth interceptor), Room DB, and DAOs.
- **ProGuard**: Release builds minified. Rules in `app/proguard-rules.pro` keep data models, Hilt, Room, Nav3, and Jsoup classes.
- **Optimistic UI**: Chat messages sent locally with `id="local-{timestamp}"` before API confirmation, then merged with server response. Opening a chat marks it as read locally (Room) then sends consumption horizon to the API. Send via `sendTextMessage()`/`sendHtmlMessage()` (typed API, no boolean toggle).
- **Presence**: Type-safe `PresenceAvailability` enum (in `Models.kt`) with `fromString()`, `displayName`, and `isOnline`. `Avatar` accepts an optional `presence: PresenceAvailability?` to overlay a `PresenceBadge`. Presence data flows through `ChatsViewModel.presenceMap: Map<String, PresenceAvailability>` fed by two sources: (1) real-time Trouter subscription via UPS pubsub, and (2) 5-minute polling fallback. The Trouter subscription is established on connect by `onTrouterConnected()` and re-established on reconnect.
- **ChatDetailScreen**: Split into `ChatDetailScreen.kt` (~310 LOC), `ChatDetailComponents.kt` (message bubbles, input bar), and `ImageViewer.kt`. `ChatAvatar` composable (in `ui/components/`) deduplicates GroupAvatar/Avatar selection logic.
- **HTML messages**: Teams messages are HTML. `HtmlParser` extracts text, `EmojiManager` normalizes emoji rendering. Replies use `<blockquote>` formatting.

## API & Token Management

`TeamsApiClient` handles four Microsoft service endpoints (Graph, ChatSvcAgg, IC3, Presence) with separate token scopes. `MailApi` and `CalendarApi` reuse TeamsApiClient's HTTP/token helpers via `internal` access. Token acquisition uses `Mutex` for thread-safety. Image loading goes through Coil with a custom `AuthHeaderInterceptor` that injects Bearer tokens for Graph photo URLs.

## Testing

- **Unit tests** (JUnit 6): `HtmlParserTest`, `JsonExtensionsTest`, `PresenceAvailabilityTest`, `TimeExtensionsTest` in `app/src/test/`. Run with `just test`.
- **UI tests** (Maestro): 6 flows in `.maestro/` covering demo login, tab navigation, chat open, search, teams/channels, profile/logout. Run with `just maestro`.
