# TwinSpace

Run multiple, fully isolated instances ("clones") of installed apps on one device —
two messaging accounts, work + personal social, multiple game accounts — with no
data leakage and no artificial logouts between them.

## Status

| Area | State |
|---|---|
| Architecture & isolation design | ✅ Done — see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Clone management (create / rename / badge icons / delete / reset) | ✅ Implemented |
| Per-clone storage layout + lazy environment init | ✅ Implemented |
| Per-clone virtual device identity (ANDROID_ID, Advertising ID) | ✅ Implemented (identity provider + persistence) |
| PIN / biometric lock (app-level and per-clone) | ✅ Implemented (Keystore-backed) |
| Launcher UI, onboarding, app picker, settings (Compose, Material 3) | ✅ Implemented |
| RAM-aware clone limit | ✅ Implemented (default 10, adjustable) |
| Background cache maintenance (WorkManager) | ✅ Implemented |
| **Virtualization engine core** (IO redirection, service hooks) | ⚠️ Interface + integration plan ready; engine core must be linked (see ARCHITECTURE.md §13) |
| Work Profile backend (OS-supported alternative) | ✅ Implemented — functional when the app holds profile/device owner |

## Why the engine core is an interface, not vaporware

A from-scratch VirtualApp-style engine is ~50k+ lines of native + framework hooking
code, and every Android release since 9 (Pie) has deliberately broken parts of that
technique (hidden-API greylist, package visibility, scoped storage, dynamic-code
restrictions). Shipping a half-hook that crashes WhatsApp is worse than shipping a
clean seam. `VirtualEngine` is that seam: the rest of the app is complete and
testable against it, and either an adapted open-source engine core or the included
Work Profile backend plugs in without touching UI, data, or security code.

## Build

Requires Android Studio Hedgehog+ / AGP 8.5, JDK 17.

```bash
./gradlew :app:assembleDebug
```

## Distribution note

Apps that host/execute third-party code are not distributable on Google Play
(policy: repackaging + `QUERY_ALL_PACKAGES`). Plan for direct APK distribution,
as Island/Shelter/Parallel-Space-class apps do. The Work Profile backend *is*
Play-compatible if activation is done via ADB/device-owner.

## Size budget

Host APK target < 30 MB. Clones reference the original app's APK in place — no
APK duplication — so total footprint = host + per-clone data dirs only. Well under
the 500 MB ceiling for typical use. See ARCHITECTURE.md §12.
