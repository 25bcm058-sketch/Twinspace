# TwinSpace — Architecture

## 1. Isolation approach: decision

**Chosen: app virtualization (VirtualApp-style engine), per product requirement #2** —
a consumer app installable as a plain APK, no MDM, no ADB, no device-owner ceremony.

A **Work Profile backend is also implemented** (`WorkProfileEngine`) because it is the
only OS-supported isolation primitive and is the correct fallback on devices where
virtualization compatibility fails. The rest of the app talks only to the
`VirtualEngine` interface, so the backend is swappable per device/Android version.

Trade-offs, stated plainly:

| | Virtualization (chosen) | Work Profile (fallback) |
|---|---|---|
| Setup friction | None — install and go | Profile-owner activation (ADB or MDM) |
| Isolation strength | Strong for local data; depends on hook coverage | OS-enforced, strongest available |
| Android 12–15 compatibility | Degraded; needs per-version maintenance | Fully supported |
| Clones of *any* app | In principle; in practice a curated compatibility list | Any app that allows profile install |
| Play Store | Not distributable | Distributable |

## 2. Compatibility reality (read before touching the engine)

- **Android 9+**: hidden-API greylist blocks reflection into `ActivityThread`,
  `ServiceManager`, etc. Mitigation: `VMRuntime` hidden-API exemption where still
  reachable; otherwise restrict hooks to public SDK surface + native IO layer.
- **Android 11+**: package visibility (`<queries>`), scoped storage. Clones cannot
  see each other's `File` paths anyway because their `Context` is virtualized.
- **Android 14+**: dynamically loaded DEX must be read-only; JIT restrictions.
  Mitigation: load clone code from the original, already-installed APK path
  (`sourceDir`) instead of copying/extracting — also key to the size budget.
- **Consequence**: "clone any arbitrary app perfectly" is not an honest promise on
  stock Android 13–15. Target a **curated compatibility list** (top messaging /
  social / game apps) with per-app hook profiles, and degrade to the Work Profile
  backend elsewhere.

## 3. Process model

- Host UI runs in the default process.
- Each clone runs in its own process: `app.twinspace:clone_<id8>`.
  - Real OS-level memory isolation between clones.
  - Lets us call `WebView.setDataDirectorySuffix(cloneId)` per process (API 28+),
    giving per-clone WebView cookies/storage **for free, OS-enforced**.
  - OS can kill a backgrounded clone process under memory pressure exactly like a
    real app — state restoration follows normal Android lifecycle, **no logout**.
- Engine registers clone components dynamically (manifest stubs + instrumentation
  dispatch), the standard VirtualApp technique.

## 4. Storage isolation

Per-clone root: `/data/data/app.twinspace/clones/<cloneId>/`

```
files/          → clone's Context.getFilesDir()
shared_prefs/   → SharedPreferences redirection target
databases/      → SQLite/Room redirection target
cache/          → cacheDir (trimmed by MaintenanceWorker)
code_cache/     → dex/jit artifacts
webview/        → WebView data directory suffix target
identity.json   → virtual ANDROID_ID / AAID (Keystore-wrapped)
```

- **Lazy init**: directories and the virtual environment are created on first
  launch, not at clone-creation time.
- **Delete/reset** wipes exactly this subtree. No other clone is touched.
- Clones share the **original APK in place** (`sourceDir` of the installed
  package) — zero APK duplication.

## 5. IO redirection strategy

Two layers, in order of preference:

1. **Java layer** — virtual `Context`/`ContextWrapper` returned by the engine's
   instrumentation: `getFilesDir()`, `getSharedPreferences()`, `openOrCreateDatabase()`,
   `getCacheDir()` resolve into the clone subtree. Covers well-behaved apps.
2. **Native layer** — inline hook on `open/openat/fopen/stat` (libc) inside the clone
   process, path-rewriting `/data/data/<original.pkg>/...` → clone subtree. Covers
   native libs and apps that bypass `Context`. This is the part that needs
   per-Android-version maintenance (§2).

## 6. Identity virtualization ("unique ID like BlueStacks")

Goal: Clone 1 and Clone 2 of the same app present as **different devices/accounts
to the app**, while sharing the real OS.

Per-clone, generated once at creation, persisted in `identity.json`:

- `Settings.Secure.ANDROID_ID` — random 64-bit value per clone. (On real Android
  this is already per-app-per-user; inside a clone we intercept and return ours.)
- Advertising ID — random UUID per clone; AAID reset requests are honored per clone.
- `Build.SERIAL` / IMEI / MAC — already unavailable to non-system apps on
  Android 10+; hooks return the same "unavailable" the real OS would, so clones
  don't look anomalous.
- Google account / phone number — **not** virtualized. Onboarding is explicit:
  we isolate local data and device identifiers, not identity from remote services.
  If the user logs into the same account in both clones, the *service* will link
  them. That is expected and documented.

Inter-clone communication: clones get distinct UIDs-per-process illusion via the
virtual package manager; binder calls between clone processes are not bridged;
`ContentProvider`/`Intent` resolution is scoped to the clone's own virtual package
space. **No shared clipboard by default** (§8), no shared storage, no shared prefs.

## 7. Notifications

- Per-clone notification channels: `<channelId>@clone:<cloneId>`, created lazily.
- Posted notifications are rewritten in the clone process: label prefixed with the
  clone's custom name ("Work WhatsApp • …"), tap-intent routed back through
  `CloneLauncher` so tapping opens the *correct* clone.
- Distinct channel per clone → user can mute "Work WhatsApp" without muting
  "Personal WhatsApp" from system settings.

## 8. Clipboard

- `ClipboardManager` service proxy per clone process.
- Default: clone clipboard is private; paste inside a clone sees only what was
  copied inside that clone. Host ↔ clone and clone ↔ clone sharing is **off**.
- Opt-in per clone ("Allow clipboard sharing") in clone settings.

## 9. Session handling

- Nothing in TwinSpace logs a clone out. Ever. Sessions live in the clone's own
  storage and expire per the cloned app's own token policy.
- Process death under memory pressure = normal Android lifecycle; the clone
  restores state on next launch like a real app.
- No forced re-auth on app switch, backgrounding, or reboot.

## 10. Performance

- **Lazy-load**: virtual environment, hooks, and identity are initialized on first
  launch of a clone, not at boot or creation.
- **Background trim**: `onTrimMemory` in clone processes releases caches;
  `MaintenanceWorker` (WorkManager, daily, idle+charging) prunes `cache/` of clones
  unused for 7+ days.
- **Clone limit**: default cap 10, computed from `ActivityManager.memoryClass`;
  advanced setting raises it on high-RAM devices (≥ 512 MB memory class → up to 15).
  This is a real RAM concern, not an arbitrary paywall.
- Switching cost target: normal cold/warm start of the cloned app + one-time
  environment attach (< 150 ms warm).

## 11. Security

- **Launcher lock**: optional PIN (PBKDF2-HMAC-SHA256, 120k iterations, per-device
  salt) or biometric (`BiometricPrompt`, `BIOMETRIC_STRONG`).
- **Per-clone lock**: flag on the clone; launching routes through the lock screen.
- **At rest**: Android file-based encryption (FBE) covers clone data on any
  encrypted device (all modern devices). Clone *metadata* DB passphrase and
  identity files are additionally wrapped with an AES-256-GCM key in
  Android Keystore (non-exportable).
- Lock timeout: re-lock after 5 min backgrounded (configurable).

## 12. Size budget (< 500 MB)

| Component | Budget |
|---|---|
| Host APK (Kotlin + Compose + engine AAR) | < 30 MB |
| Clone code | 0 — original APK referenced in place |
| Per-clone data | whatever the cloned app itself stores (user-controlled) |
| Engine native libs (arm64-v8a, armeabi-v7a) | ~6 MB |

The 500 MB ceiling is met structurally: we never duplicate APKs, and cache trimming
bounds our own overhead. The cloned apps' own data dominates, as it should.

## 13. Engine integration plan

`VirtualEngine` is the seam. Phases:

1. **Phase 0 (this repo)** — everything except the engine core; `StubVirtualEngine`
   returns a clear "engine not linked" state; `WorkProfileEngine` is functional.
2. **Phase 1** — integrate an engine core (adapt an open-source virtualization
   core, Apache/GPL licensing review required, or contract one): virtual package
   manager, instrumentation dispatch, Java-layer IO redirection. Target the
   curated compatibility list.
3. **Phase 2** — native IO hooks + per-app hook profiles; compatibility lab
   (Firebase Test Lab / device farm) across API 26–35.
4. **Phase 3** — hardening: anti-tamper on the host, hook coverage telemetry
   (opt-in), per-version fallback matrix to Work Profile backend.

## 14. Policy & distribution

- Google Play: virtualization backend is **not** Play-distributable (executes
  third-party code; `QUERY_ALL_PACKAGES`). Distribute APK directly.
- Work Profile backend alone **is** Play-compatible.
- Be upfront in onboarding and store copy: local-data isolation, not anonymity
  from remote services.
