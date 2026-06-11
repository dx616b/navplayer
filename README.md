# NavPlayer

Minimal Navidrome / Subsonic client for Android head units.

## Features

- HTTPS login (saved with EncryptedSharedPreferences)
- **Random** — shuffle and play your full Navidrome library
- **Playlists** — tap a playlist to play it
- Landscape UI: large buttons, small cover art (64px)
- Player bar: seek, previous, play/pause, next
- Background playback via Media3

## Build

```bash
chmod +x scripts/setup-android-env.sh
./scripts/setup-android-env.sh
```

Installs JDK 17, Android SDK (API 35), Gradle wrapper, builds debug APK. Needs **sudo** once for apt packages.

```bash
./scripts/setup-android-env.sh --emulator   # also download emulator (~GB)
./scripts/setup-android-env.sh --run        # build + emulator + install app
```

Or open this folder in **Android Studio** and Run.

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Try locally (emulator)

No physical device needed. First run downloads the emulator + system image (~1–2 GB).

```bash
chmod +x scripts/run-local.sh
./scripts/run-local.sh
```

This builds the APK, starts a landscape emulator, installs NavPlayer, and opens the app.

**Navidrome URL in Settings:** use `https://10.0.2.2:4533` to reach a server on this PC from the emulator (`10.0.2.2` is the host loopback). Use your LAN IP instead if Navidrome runs on another machine.

Self-signed HTTPS: install your CA on the emulator (Settings → Security → Install certificate), or use a cert the emulator already trusts.

Emulator log: `/tmp/navplayer-emulator.log`

If the emulator fails immediately, install PulseAudio libs and grant KVM:

```bash
sudo apt-get install -y libpulse0
sudo gpasswd -a $USER kvm
./scripts/start-emulator.sh
```

(`start-emulator.sh` skips rebuild — use after `./scripts/run-local.sh` already built the APK.)

## First run (device or emulator)

1. Open **Settings** (gear icon).
2. Enter `https://your-navidrome-host:4533` ( `/rest` is added automatically).
3. Username + password, **Test connection**, **Save**.
4. For self-signed HTTPS on LAN, install your CA on the device (network config trusts user CAs).

## Head unit tips

NavPlayer is tuned for landscape dash screens. Configure under **Settings → Head unit**:

- **Start on boot** (on by default) — opens NavPlayer when the head unit powers on
- **Driving mode** — larger controls, single-column playlists, settings hidden; **long-press Playlists** to open settings

Also built in:

- **48dp+ touch targets** on transport, Random, playlists, and seek bar
- **Immersive fullscreen** — swipe from edge for system bars
- **Screen stays on while playing**
- **Steering wheel / Bluetooth buttons** — prev/next/play via MediaSession

Optional on the device: disable battery optimization for NavPlayer.

## Server

Works with Navidrome and other Subsonic API 1.16.1 servers over **HTTPS**.
