# Soundbar Wake for Android TV

**English** · [Türkçe](README.tr.md)

A tiny background app for Android TV that **wakes your HDMI ARC soundbar back up when it goes to auto-standby while the TV is still on**. No root, no extra hardware. You only need ADB once, for installation.

Tested on:

| Device | Model | Notes |
|---|---|---|
| TV | Philips 55PUS7304/12 (TPM191E, Android TV 9) | HDMI 4 = ARC |
| Soundbar | Philips TAB6405/10 | connected via HDMI ARC |

It should work on other Android TVs (Philips, Sony, TCL, Hisense, Sharp …) and other soundbars/AV receivers that behave the same way. See [Compatibility](#compatibility).

---

## The problem

The Philips TAB6405 (like many soundbars sold in the EU) goes to **standby after ~15–20 minutes without audio**. This is required by EU energy regulations, and you can't turn it off.

When it sleeps:

- Pressing volume on the TV or Apple TV remote does **not** wake it.
- Starting playback does **not** wake it.
- The only ways back are the soundbar's own remote, its power button, or turning the TV off and on.

## What's actually happening (HDMI-CEC)

From the TV's CEC log (`adb logcat -s HDMI`), when the soundbar (CEC logical address **5**, "Audio System") falls asleep it says goodbye:

```
[R]:<Terminate ARC> 50:C5
[R]:<Set System Audio Mode> 5F:72:00
```

The important discovery: **in standby the soundbar is still listening on the CEC bus.** It still answers the TV's polling, which is why turning the TV off and on wakes it. When the TV starts up it sends:

```
[S]:<System Audio Mode Request> 05:70:20:00
[R]:<Set System Audio Mode> 50:72:01    <- soundbar is awake
[R]:<Initiate ARC> 50:C0                <- audio back on the soundbar
```

So we only need to make the TV send `<System Audio Mode Request>` again without being turned off. An ordinary app can't send raw CEC messages. But toggling the TV's global setting `hdmi_system_audio_control_enabled` **off → on** does exactly that. This is the same as switching *Sound output* to *TV speakers* and back to *HDMI sound system* in the TV menu.

## The solution

`Soundbar Wake` is a ~13 KB foreground service that:

1. Follows the TV's `HDMI` log tag (needs `READ_LOGS`).
2. Watches for `<Terminate ARC> 50:C5` or `<Set System Audio Mode> 5x:72:00` coming from the audio system.
3. Waits 1 s. If the **screen is off** in the meantime, the TV itself is turning off, so the app does nothing and the soundbar sleeps with the TV.
4. If *Sound output* is set to *TV speakers* (setting = 0), it does nothing.
5. Otherwise it toggles `hdmi_system_audio_control_enabled` 0 → 1 (needs `WRITE_SECURE_SETTINGS`). The soundbar wakes about **1.7 s** after it went to sleep.
6. For 20 s after a wake it ignores further messages, so its own toggle can't make it loop.

It starts automatically on boot. It has no launcher icon and no UI.

**Side effect:** while the TV is on, if you switch the soundbar off with its own remote, it comes back on after a moment. Turn off the TV instead, or uninstall the app.

---

## Installation guide

You need a computer with **ADB** ([Android platform-tools](https://developer.android.com/tools/releases/platform-tools)). On macOS: `brew install --cask android-platform-tools`.

### 1. Enable developer options and debugging on the TV

1. **Settings → Device Preferences → About** (Philips: *Settings → All settings → … → About*). Scroll to **Build** and press OK on it **7 times**.
2. **Settings → Device Preferences → Developer options** → turn on **USB debugging**. If your TV has **Network debugging / ADB over network**, turn that on too.
3. Note the TV's IP address (**Settings → Network**).

### 2. Connect

```sh
adb connect <TV-IP>:5555
```

Accept the **"Allow USB debugging?"** prompt on the TV (tick *Always allow*).

### 3. Check that your TV logs CEC messages

```sh
adb logcat -s HDMI
```

Switch the TV's sound output to TV speakers and back to the soundbar. You should see lines like `[S]:<System Audio Mode Request> 05:70...` and `[R]:<Set System Audio Mode> 50:72:01`.

If you see nothing, enable the log tag and try again:

```sh
adb shell setprop persist.log.tag.HDMI D
```

If there are still no lines like these, the app can't help on your TV. See [Compatibility](#compatibility).

### 4. Install

Download `soundbar-wake.apk` from [Releases](../../releases/latest), then:

```sh
adb install soundbar-wake.apk
adb shell pm grant io.github.odcaner.soundbarwake android.permission.READ_LOGS
adb shell pm grant io.github.odcaner.soundbarwake android.permission.WRITE_SECURE_SETTINGS
adb shell am start-foreground-service -n io.github.odcaner.soundbarwake/.WakeService
```

That's it. It also starts by itself after every reboot.

### 5. Test it (without waiting 20 minutes)

Fake a "soundbar went to sleep" message:

```sh
adb shell log -p d -t HDMI "'[R]:<Terminate ARC> 50:C5'"
adb logcat -d -s SoundbarWake
```

Expected:

```
I SoundbarWake: standby detected: ... <Terminate ARC> 50:C5
I SoundbarWake: waking soundbar
```

The sound drops for a second and comes back on the soundbar.

Real test: leave the TV idle with no sound until the soundbar sleeps. It should come back by itself within about 2 seconds.

---

## Tuning

All values are in milliseconds and can be changed without reinstalling:

| Setting | Default | Meaning |
|---|---|---|
| `soundbar_wake_delay_ms` | 1000 | wait before waking; used to detect "TV is turning off" |
| `soundbar_wake_toggle_ms` | 500 | gap between setting off → on |
| `soundbar_wake_quiet_ms` | 20000 | ignore new triggers after a wake |

```sh
adb shell settings put global soundbar_wake_delay_ms 3000
```

If the soundbar sometimes **wakes up right after you turn off the TV**, increase `soundbar_wake_delay_ms` to 2000–3000. If the wake sometimes doesn't take, increase `soundbar_wake_toggle_ms` to 2000.

## Uninstall

```sh
adb uninstall io.github.odcaner.soundbarwake
```

## Compatibility

- **Android TV 8–11:** should work. These versions use the `hdmi_system_audio_control_enabled` global setting and AOSP's CEC log format (`<Terminate ARC> 50:C5`).
- **Android 12+ / Google TV:** untested. CEC settings moved to `HdmiCecConfig`, so toggling the global setting may have no effect there. Feedback and PRs welcome.
- **Soundbars/AVRs:** this only works if the device stays on the CEC bus in standby and wakes on `<System Audio Mode Request>`. That's the normal CEC behaviour. Quick check: if turning the TV off and on wakes your soundbar, this will most likely work.
- Not affiliated with Philips or TP Vision.

## Troubleshooting

```sh
adb shell dumpsys hdmi_control | grep -E "logical_address|mArcEstablished"   # is the soundbar (0x05) on the bus?
adb logcat -s SoundbarWake HDMI                                               # live log
adb shell dumpsys activity services io.github.odcaner.soundbarwake            # is the service running?
```

**ARC lost completely** (TV says *"Cannot switch to sound system, TV speakers enabled"*, and the log shows `No AVR device`): the soundbar has dropped off CEC entirely. This app can't fix that. **Unplug the soundbar's power for 1–2 minutes.** Another CEC 2.0 device (e.g. an Apple TV) can also briefly claim address 5. In that case turning off *Control TVs and Receivers* on the Apple TV helps.

## Building from source

No Gradle needed. You need a JDK 17+ and an Android SDK (build-tools + any platform ≥ 26):

```sh
export ANDROID_HOME=/path/to/android/sdk
./build.sh          # creates soundbar-wake.apk, signed with a local debug key
```

## License

MIT
