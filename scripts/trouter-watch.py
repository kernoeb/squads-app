#!/usr/bin/env python3
"""Watch Teams messages in real-time via Trouter WebSocket."""
import asyncio
import html
import json
import re
import uuid
import time
import urllib.request
import urllib.parse
import urllib.error
from datetime import datetime

import websockets

TOKEN_PATH = "/Users/noewen/Library/Caches/squads-cli.squads-cli/tokens.json"


def load_token():
    with open(TOKEN_PATH) as f:
        tokens = json.load(f)["tokens"]
    t = tokens["https://ic3.teams.office.com/.default"]
    if t["expires"] < time.time():
        print("[!] IC3 token expired — run: squads-cli auth refresh")
        raise SystemExit(1)
    return t["value"]


def register(token, surl, epid):
    body = json.dumps({
        "clientDescription": {
            "appId": "TeamsCDLWebWorker",
            "aesKey": "",
            "languageId": "en-US",
            "platform": "chrome",
            "templateKey": "TeamsCDLWebWorker_2.6",
            "platformUIVersion": "1415/26022704215",
        },
        "registrationId": epid,
        "nodeId": "",
        "transports": {"TROUTER": [{"context": "", "path": surl, "ttl": 3600}]},
    }).encode()
    req = urllib.request.Request(
        "https://teams.cloud.microsoft/registrar/prod/V2/registrations",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "X-MS-Migration": "True",
        },
        method="POST",
    )
    resp = urllib.request.urlopen(req)
    return resp.status


def parse_frame_data(frame):
    """Extract data portion from Socket.IO v0.9 frame (after 3rd colon)."""
    colons = 0
    for i, c in enumerate(frame):
        if c == ":":
            colons += 1
            if colons == 3:
                return frame[i + 1:]
    return ""


def get_seq(frame):
    parts = frame.split(":", 2)
    return parts[1].replace("+", "") if len(parts) >= 2 else None


def ts():
    return datetime.now().strftime("%H:%M:%S")


async def watch():
    token = load_token()
    epid = str(uuid.uuid4())
    params = urllib.parse.urlencode({
        "tc": json.dumps({"cv": "2026.07.01.1", "ua": "SquadsWatch", "hr": "", "v": "0.1.0"}),
        "timeout": "40",
        "epid": epid,
        "ccid": "",
        "cor_id": str(uuid.uuid4()),
        "con_num": f"{int(time.time() * 1000)}_0",
    })
    url = f"wss://go-eu.trouter.teams.microsoft.com/v4/c?{params}"

    print(f"[{ts()}] Connecting...")
    async with websockets.connect(url, additional_headers={"Origin": "https://teams.cloud.microsoft"}) as ws:
        # 1:: connected
        await asyncio.wait_for(ws.recv(), timeout=10)

        # Authenticate
        auth = json.dumps({"name": "user.authenticate", "args": [{"headers": {
            "Authorization": f"Bearer {token}", "X-MS-Migration": "True"
        }}]})
        await ws.send(f"5:::{auth}")

        # Wait for trouter.connected
        surl = None
        while True:
            frame = str(await asyncio.wait_for(ws.recv(), timeout=10))
            if "trouter.connected" in frame:
                data = json.loads(parse_frame_data(frame))
                surl = data["args"][0]["surl"]
                break
            if "+" in (frame.split(":", 2)[1] if len(frame.split(":", 2)) > 1 else ""):
                await ws.send(f"6:::{get_seq(frame)}+[]")

        # Drain message_loss frames
        try:
            while True:
                frame = str(await asyncio.wait_for(ws.recv(), timeout=2))
                seq = get_seq(frame)
                if seq and "+" in frame.split(":", 2)[1]:
                    await ws.send(f"6:::{seq}+[]")
        except (asyncio.TimeoutError, TimeoutError):
            pass

        # Register
        status = register(token, surl, epid)
        print(f"[{ts()}] Connected & registered (status {status})")
        print(f"[{ts()}] Watching for messages... (Ctrl+C to stop)\n")

        # Watch loop
        while True:
            try:
                frame = str(await asyncio.wait_for(ws.recv(), timeout=45))

                # Ping/pong
                if '"name":"ping"' in frame:
                    await ws.send(f'6:::{get_seq(frame)}+["pong"]')
                    continue

                # Notification
                if frame.startswith("3:::"):
                    payload = json.loads(frame[4:])
                    url_path = payload.get("url", "")
                    body = json.loads(payload.get("body", "{}"))
                    resource = body.get("resource", {})
                    rtype = body.get("resourceType", "?")
                    mtype = resource.get("messagetype", "")

                    # ACK
                    ack = json.dumps({"id": payload["id"], "status": 200, "headers": {}, "body": ""})
                    await ws.send(f"3:::{ack}")

                    # Extract sender MRI for disambiguation
                    from_mri = resource.get("from", "")
                    # e.g. "https://.../contacts/8:orgid:c4f6cd7a-..."
                    sender_id = from_mri.split("/")[-1] if from_mri else ""

                    # Format output
                    if mtype == "Control/Typing":
                        sender = resource.get("imdisplayname", "?")
                        tag = f" ({sender_id})" if sender_id else ""
                        print(f"  [{ts()}] \033[90m✎ {sender}{tag} is typing...\033[0m")
                    elif mtype == "Control/ClearTyping":
                        pass  # silently ignore stop-typing
                    elif mtype == "ThreadActivity/MemberConsumptionHorizonUpdate":
                        pass  # read receipts — noisy, skip
                    elif rtype == "NewMessage":
                        sender = resource.get("imdisplayname", "?")
                        content = resource.get("content", "")
                        text = html.unescape(re.sub(r"<[^>]+>", "", content)).strip()
                        chat_raw = resource.get("conversationLink", "").split("/")[-1]
                        chat = urllib.parse.unquote(chat_raw)
                        thread = resource.get("threadtype", "")
                        label = "📝" if thread == "streamofnotes" else "✉"
                        tag = f" \033[90m({sender_id})\033[0m" if sender_id else ""
                        print(f"  [{ts()}] {label} \033[1m{sender}\033[0m{tag}: {text}")
                        print(f"           \033[90mchat={chat[:60]}  id={resource.get('id', '?')}\033[0m")
                    elif rtype == "MessageUpdate":
                        print(f"  [{ts()}] \033[90m↻ MessageUpdate\033[0m")
                    elif rtype == "ConversationUpdate":
                        pass  # chat metadata updates — skip
                    else:
                        suffix = url_path.split("/")[-1] if "/" in url_path else url_path
                        print(f"  [{ts()}] ◆ {rtype} ({suffix})")

                # Other frames (message_loss, etc)
                elif "message_loss" in frame:
                    seq = get_seq(frame)
                    if seq and "+" in frame.split(":", 2)[1]:
                        await ws.send(f"6:::{seq}+[]")

            except (asyncio.TimeoutError, TimeoutError):
                print(f"  [{ts()}] ... (no activity)")


if __name__ == "__main__":
    try:
        asyncio.run(watch())
    except KeyboardInterrupt:
        print(f"\n[{datetime.now().strftime('%H:%M:%S')}] Stopped.")
