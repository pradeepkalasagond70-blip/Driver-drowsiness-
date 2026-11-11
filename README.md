# Driver Drowsiness Monitor — Android starter project

This is a **native Android (Kotlin + Jetpack Compose)** project, not a website.

## Included
- Front-camera monitoring using CameraX and Google ML Kit Face Detection.
- Live metric cards for eye-open probability (EAR-labelled proxy), mouth-opening ratio (MAR estimate), rolling eye-closure percentage (PERCLOS-style), blink events, head pitch and a heuristic risk score.
- WebSocket connection screen for your existing Python/OpenCV/MediaPipe service.
- Bidirectional JSON metrics messages.
- GitHub Actions workflow to build a debug APK.

## Important accuracy notes
This starter uses ML Kit's eye-open classification probability as an eye-closure signal. It is **not a geometric EAR calculation**. The mouth ratio is a rough contour-derived estimate, not a validated MAR implementation. The rolling eye-closure percentage is a PERCLOS-style estimate; calibrate and validate thresholds against labelled driving data before any safety use. The displayed risk score is a prototype heuristic, not a medical or certified safety assessment. No real-world driving safety guarantee is made.

The current camera analyzer computes metrics from the front camera. It does not yet render a camera preview in the UI. Use a secure phone mount and test while parked. Do not interact with the phone while driving.

## Connect your Python backend

Set up a WebSocket endpoint, e.g. `ws://YOUR_LAN_IP:8000/ws/metrics` for local testing or `wss://your-domain/ws/metrics` in production. The phone and computer must be on a network that can reach each other. For production use HTTPS/WSS, authentication, TLS, and access controls. Do not expose an unauthenticated endpoint to the public internet.

The app sends:
```json
{
  "type": "driver_metrics",
  "timestamp": 1730000000000,
  "ear": 0.72,
  "mar": 0.18,
  "perclos_percent": 4,
  "blinks_per_minute": 12,
  "head_pitch_degrees": 2,
  "risk_score": 5,
  "risk_label": "Low",
  "status": "Monitoring",
  "yawn_count": 0,
  "alert_count": 0,
  "source": "Phone camera / ML Kit"
}
```

Your Python server can send the same schema back to the app. The app updates its dashboard when it receives a message with `type: "driver_metrics"`.

### Minimal FastAPI WebSocket example
```python
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import json

app = FastAPI()
clients = set()

@app.websocket("/ws/metrics")
async def metrics_socket(ws: WebSocket):
    await ws.accept()
    clients.add(ws)
    try:
        while True:
            payload = await ws.receive_text()
            data = json.loads(payload)
            # TODO: forward to your existing OpenCV/MediaPipe pipeline,
            # persist metrics, and/or broadcast to authenticated viewers.
            await ws.send_json(data)  # echo for connection testing
    except WebSocketDisconnect:
        clients.discard(ws)
```

## Build the APK

### Easiest: GitHub Actions
1. Create a GitHub repository and upload this project.
2. Push the files to the default branch.
3. Open **Actions → Build Android APK**.
4. Download the `driver-drowsiness-debug-apk` artifact from the completed workflow.
5. Transfer the APK to your Android phone and install it. Android may ask you to allow installs from that source.

### Local build
Install Android Studio (with Android SDK 35) and JDK 17, open this folder, sync Gradle, then run:
`./gradlew assembleDebug`

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Next integration step
Replace the sample WebSocket endpoint with the URL and payload contract of your actual Python project. If your Python project currently exports CSV only, add the WebSocket route or a small bridge service.
