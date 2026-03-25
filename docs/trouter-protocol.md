# Teams Trouter WebSocket Protocol

Real-time notification system for Teams. Pushes messages, typing indicators, presence updates instantly instead of polling.

## Connection Flow

### 1. Open WebSocket

```
wss://go-eu.trouter.teams.microsoft.com/v4/c?
  tc={"cv":"2026.07.01.1","ua":"SquadsApp","hr":"","v":"0.1.0"}
  &timeout=40
  &epid=<random-uuid>
  &ccid=
  &cor_id=<random-uuid>
  &con_num=<timestamp-ms>_0
```

Regional endpoints: `go-eu.trouter.teams.microsoft.com` (EU), `go.trouter.teams.microsoft.com` (US).

### 2. Server sends `1::` (connected)

### 3. Authenticate

```
→ 5:::{"name":"user.authenticate","args":[{"headers":{"Authorization":"Bearer <IC3_TOKEN>","X-MS-Migration":"True"}}]}
```

Token scope: `https://ic3.teams.office.com/.default`

### 4. Server responds `trouter.connected`

```
← 5:1::{"name":"trouter.connected","args":[{
    "surl": "https://pub-ent-euwe-04-f.trouter.teams.microsoft.com:3443/v4/f/<ID>/",
    "reconnectUrl": "wss://pub-ent-euwe-04-t.trouter.teams.microsoft.com:443/v4/c",
    "registrarUrl": "https://communications.svc.cloud.microsoft/registrar/eu/v3/registrations",
    "ttl": "576814",
    "connectparams": {"sig":"...","sr":"...","se":"...","st":"..."}
  }]}
```

Key fields: `surl` (notification URL for registrar), `reconnectUrl` (for reconnection), `ttl` (connection lifetime).

### 5. ACK `message_loss` frames

Server sends `message_loss` + `processed_message_loss` frames. ACK the ones with `+` in their ID:

```
← 5:1+::{"name":"trouter.processed_message_loss","args":[...]}
→ 6:::1+[]
```

### 6. Register at Registrar

```http
POST https://teams.cloud.microsoft/registrar/prod/V2/registrations
Authorization: Bearer <IC3_TOKEN>
Content-Type: application/json
X-MS-Migration: True
```

```json
{
  "clientDescription": {
    "appId": "TeamsCDLWebWorker",
    "aesKey": "",
    "languageId": "en-US",
    "platform": "chrome",
    "templateKey": "TeamsCDLWebWorker_2.6",
    "platformUIVersion": "1415/26022704215"
  },
  "registrationId": "<same-epid-as-websocket>",
  "nodeId": "",
  "transports": {
    "TROUTER": [{"context": "", "path": "<surl>", "ttl": 3600}]
  }
}
```

Response: `202 Accepted`.

**CRITICAL:** `appId` must be `"TeamsCDLWebWorker"` with `templateKey: "TeamsCDLWebWorker_2.6"`. The old `"SkypeSpacesWeb"` / `"SkypeSpacesWeb_2.4"` is accepted (202) but notifications are silently never delivered.

### 7. Ping/Pong

Server pings every ~40s. Extract sequence number, respond with ACK:

```
← 5:3+::{"name":"ping"}
→ 6:::3+["pong"]
```

## Socket.IO v0.9 Frame Format

```
<type>:<id><+?>:<endpoint>:<data>
```

| Type | Meaning |
|------|---------|
| `1` | Connect (`1::`) |
| `3` | Notification payload |
| `5` | Event (ping, auth, message_loss) |
| `6` | ACK |

`+` in ID = requires ACK from receiver.

## Notification Payloads

All arrive as type-3 frames. The `body` field is a JSON string.

```
← 3:::{"id":787794897,"method":"POST","url":"/v4/f/<ID>/messaging","headers":{...},"body":"<json-string>"}
```

ACK: `→ 3:::{"id":787794897,"status":200,"headers":{},"body":""}`

### Message body structure

```json
{
  "resourceType": "NewMessage",
  "resource": {
    "content": "<p>hello</p>",
    "from": "https://notifications.skype.net/v1/users/ME/contacts/8:orgid:<sender-uuid>",
    "imdisplayname": "Noéwen Boisnard",
    "id": "1774448969957",
    "messagetype": "RichText/Html",
    "conversationLink": "https://notifications.skype.net/v1/users/ME/conversations/<chat-id>",
    "threadtype": "chat"
  }
}
```

### Notification types

| `messagetype` | `resourceType` | Description |
|------|-------------|------------|
| `RichText/Html` | `NewMessage` | Chat message (HTML content) |
| `Text` | `NewMessage` | Plain text message |
| `Control/Typing` | `NewMessage` | Typing indicator |
| `Control/ClearTyping` | `NewMessage` | Stopped typing |
| `ThreadActivity/MemberConsumptionHorizonUpdate` | `NewMessage` | Read receipts |
| — | `ConversationUpdate` | Chat metadata (pin, mute) |
| — | `MessageUpdate` | Message edited/deleted |

URL path suffix: `/messaging` for all chat events, `/unifiedPresenceService` for presence.

### Key fields for the app

- `resource.conversationLink` → extract chat ID (last path segment, URL-decode)
- `resource.imdisplayname` → sender display name
- `resource.content` → message HTML
- `resource.from` → sender MRI (extract `8:orgid:<uuid>` from end)
- `resource.id` → message ID
- `resource.threadtype` → `"chat"`, `"streamofnotes"` (personal notes), etc.

## Presence Subscriptions (Optional)

Not required for messaging. Only for real-time presence (online/busy/offline).

```http
POST https://teams.cloud.microsoft/ups/emea/v1/pubsub/subscriptions/<endpoint-id>
Authorization: Bearer <PRESENCE_TOKEN>
x-ms-endpoint-id: <endpoint-id>
```

```json
{
  "trouterUri": "<surl>/unifiedPresenceService",
  "shouldPurgePreviousSubscriptions": true,
  "subscriptionsToAdd": [{"mri": "8:orgid:<user-id>", "source": "ups"}],
  "subscriptionsToRemove": []
}
```

Token scope: `https://presence.teams.microsoft.com/.default`

## Token Scopes

| Scope | Used for |
|-------|----------|
| `https://ic3.teams.office.com/.default` | WebSocket auth + Registrar |
| `https://presence.teams.microsoft.com/.default` | Presence subscriptions (optional) |
| `https://graph.microsoft.com/.default` | Graph API (already in app) |
| `https://chatsvcagg.teams.microsoft.com/.default` | Chat service (already in app) |

## Watch Script

Live monitoring: `python3 scripts/trouter-watch.py`

Reads IC3 token from squads-cli cache. Displays messages, typing indicators in real-time.

## Implementation Plan for Android

### New files

1. **`TrouterClient.kt`** — WebSocket client (OkHttp)
   - Socket.IO v0.9 framing, auth, ping/pong, ACK
   - Parse notifications, emit to Flow/callback

2. **`TrouterService.kt`** — Android foreground service
   - Lifecycle management, reconnection with exponential backoff
   - Show Android notifications for new messages

### Reconnection strategy

1. On disconnect: reconnect using `reconnectUrl` after 1s
2. Exponential backoff: 1s, 2s, 4s, 8s, max 60s
3. On token expiry: refresh IC3 token, then reconnect
4. On `message_loss`: fetch missed messages via REST API

### Migration from polling

1. Keep polling as fallback (increase interval to 60s when Trouter connected)
2. On `NewMessage` notification: update messages immediately
3. On Trouter disconnect: re-enable fast polling
