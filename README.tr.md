# Android TV için Soundbar Wake

[English](README.md) · **Türkçe**

HDMI ARC ile bağlı soundbar, **TV açıkken otomatik bekleme moduna geçtiğinde onu kendiliğinden yeniden uyandıran** küçük bir arka plan uygulaması. Root ya da ek donanım gerekmez. Kurulum için bir kez ADB kullanman yeter.

Test edilen cihazlar:

| Cihaz | Model | Not |
|---|---|---|
| TV | Philips 55PUS7304/12 (TPM191E, Android TV 9) | HDMI 4 = ARC |
| Soundbar | Philips TAB6405/10 | HDMI ARC ile bağlı |

Aynı şekilde davranan başka Android TV'lerde (Philips, Sony, TCL, Hisense, Sharp …) ve başka soundbar/AV alıcılarda da çalışması beklenir. Bkz. [Uyumluluk](#uyumluluk).

---

## Sorun

Philips TAB6405 (AB'de satılan birçok soundbar gibi) **~15–20 dakika ses gelmezse bekleme moduna geçer**. Bu AB enerji yönetmeliği gereği zorunludur ve kapatılamaz.

Uyuduğunda:

- TV ya da Apple TV kumandasından ses açmak onu **uyandırmaz**.
- Video oynatmaya başlamak onu **uyandırmaz**.
- Geri getirmenin tek yolları: soundbar'ın kendi kumandası, üzerindeki güç düğmesi ya da TV'yi kapatıp açmak.

## Arka planda ne oluyor (HDMI-CEC)

TV'nin CEC loguna göre (`adb logcat -s HDMI`), soundbar (CEC mantıksal adres **5**, "Audio System") uykuya geçerken şunları gönderir:

```
[R]:<Terminate ARC> 50:C5
[R]:<Set System Audio Mode> 5F:72:00
```

Önemli bulgu: **soundbar bekleme modundayken CEC hattını dinlemeye devam ediyor.** TV'nin taramasına hâlâ cevap veriyor. TV'yi kapatıp açınca uyanmasının sebebi de bu. TV açılırken şunu gönderir:

```
[S]:<System Audio Mode Request> 05:70:20:00
[R]:<Set System Audio Mode> 50:72:01    <- soundbar uyandı
[R]:<Initiate ARC> 50:C0                <- ses tekrar soundbar'da
```

Yani TV'yi kapatmadan `<System Audio Mode Request>` mesajını yeniden göndertmek yeterli. Sıradan bir uygulama doğrudan CEC mesajı gönderemez. Ama TV'nin `hdmi_system_audio_control_enabled` genel ayarını **kapatıp açmak** tam olarak bunu yapar. Bu, TV menüsünde *Ses çıkışı*'nı *TV hoparlörleri*ne alıp tekrar *HDMI ses sistemi*ne döndürmekle aynı şey.

## Çözüm

`Soundbar Wake` ~13 KB'lık bir arka plan servisi. Şunları yapar:

1. TV'nin `HDMI` loglarını izler (`READ_LOGS` izni gerekir).
2. Ses sisteminden gelen `<Terminate ARC> 50:C5` ya da `<Set System Audio Mode> 5x:72:00` mesajını bekler.
3. 1 sn bekler. Bu arada **ekran kapandıysa** TV'nin kendisi kapanıyor demektir. O zaman hiçbir şey yapmaz, soundbar TV ile birlikte uyur.
4. *Ses çıkışı* *TV hoparlörleri* seçiliyse (ayar = 0) hiçbir şey yapmaz.
5. Aksi halde `hdmi_system_audio_control_enabled` ayarını 0 → 1 yapar (`WRITE_SECURE_SETTINGS` izni gerekir). Soundbar uykuya geçtikten yaklaşık **1,7 sn** sonra uyanır.
6. Bir uyandırmadan sonraki 20 sn boyunca yeni mesajları yok sayar. Böylece kendi yaptığı değişiklik onu döngüye sokamaz.

Açılışta kendiliğinden başlar. Ana ekranda simgesi ve arayüzü yoktur.

**Yan etki:** TV açıkken soundbar'ı kendi kumandasıyla kapatırsan biraz sonra yeniden açılır. Bunun yerine TV'yi kapat ya da uygulamayı kaldır.

---

## Kurulum rehberi

**ADB** kurulu bir bilgisayar gerekir ([Android platform-tools](https://developer.android.com/tools/releases/platform-tools)). macOS'ta: `brew install --cask android-platform-tools`.

### 1. TV'de geliştirici seçeneklerini ve hata ayıklamayı aç

1. **Ayarlar → Cihaz Tercihleri → Hakkında** (Philips: *Ayarlar → Tüm ayarlar → … → Hakkında*). **Derleme** (Build) satırına gel ve üzerinde OK tuşuna **7 kez** bas.
2. **Ayarlar → Cihaz Tercihleri → Geliştirici seçenekleri** → **USB hata ayıklama**'yı aç. TV'nde **Ağ üzerinden hata ayıklama / ADB over network** seçeneği varsa onu da aç.
3. TV'nin IP adresini not et (**Ayarlar → Ağ**).

### 2. Bağlan

```sh
adb connect <TV-IP>:5555
```

TV'de çıkan **"USB hata ayıklamaya izin verilsin mi?"** sorusunu onayla (*Her zaman izin ver* kutusunu işaretle).

### 3. TV'nin CEC mesajlarını logladığını kontrol et

```sh
adb logcat -s HDMI
```

TV'de ses çıkışını TV hoparlörlerine alıp tekrar soundbar'a döndür. `[S]:<System Audio Mode Request> 05:70...` ve `[R]:<Set System Audio Mode> 50:72:01` gibi satırlar görmelisin.

Hiçbir şey görmüyorsan log etiketini açıp tekrar dene:

```sh
adb shell setprop persist.log.tag.HDMI D
```

Bu tür satırlar hâlâ yoksa uygulama senin TV'nde işe yaramaz. Bkz. [Uyumluluk](#uyumluluk).

### 4. Kur

`soundbar-wake.apk` dosyasını [Releases](../../releases/latest) sayfasından indir, sonra:

```sh
adb install soundbar-wake.apk
adb shell pm grant io.github.odcaner.soundbarwake android.permission.READ_LOGS
adb shell pm grant io.github.odcaner.soundbarwake android.permission.WRITE_SECURE_SETTINGS
adb shell am start-foreground-service -n io.github.odcaner.soundbarwake/.WakeService
```

Bu kadar. Her yeniden başlatmadan sonra da kendiliğinden çalışır.

### 5. Test et (20 dakika beklemeden)

Sahte bir "soundbar uykuya geçti" mesajı gönder:

```sh
adb shell log -p d -t HDMI "'[R]:<Terminate ARC> 50:C5'"
adb logcat -d -s SoundbarWake
```

Beklenen çıktı:

```
I SoundbarWake: standby detected: ... <Terminate ARC> 50:C5
I SoundbarWake: waking soundbar
```

Ses bir saniye kesilip soundbar'dan geri gelir.

Gerçek test: TV'yi sessiz bırak, soundbar uykuya geçsin. Yaklaşık 2 saniye içinde kendiliğinden geri açılmalı.

---

## Ayarlar

Tüm değerler milisaniye cinsindendir ve yeniden kurmadan değiştirilebilir:

| Ayar | Varsayılan | Anlamı |
|---|---|---|
| `soundbar_wake_delay_ms` | 1000 | uyandırmadan önce bekleme; "TV kapanıyor mu" kontrolü için |
| `soundbar_wake_toggle_ms` | 500 | ayarı kapatıp açma arasındaki süre |
| `soundbar_wake_quiet_ms` | 20000 | bir uyandırmadan sonra yeni mesajları yok sayma süresi |

```sh
adb shell settings put global soundbar_wake_delay_ms 3000
```

Soundbar bazen **TV'yi kapattıktan hemen sonra yeniden açılıyorsa** `soundbar_wake_delay_ms` değerini 2000–3000 yap. Uyandırma bazen tutmuyorsa `soundbar_wake_toggle_ms` değerini 2000 yap.

## Kaldırma

```sh
adb uninstall io.github.odcaner.soundbarwake
```

## Uyumluluk

- **Android TV 8–11:** çalışması beklenir. Bu sürümler `hdmi_system_audio_control_enabled` genel ayarını ve AOSP CEC log biçimini (`<Terminate ARC> 50:C5`) kullanır.
- **Android 12+ / Google TV:** test edilmedi. CEC ayarları `HdmiCecConfig`'e taşındı, bu yüzden genel ayarı değiştirmek orada işe yaramayabilir. Geri bildirim ve PR'lar memnuniyetle karşılanır.
- **Soundbar/AV alıcılar:** yalnızca cihaz bekleme modunda CEC hattında kalıyor ve `<System Audio Mode Request>` ile uyanıyorsa çalışır. Bu normal CEC davranışıdır. Hızlı kontrol: TV'yi kapatıp açmak soundbar'ını uyandırıyorsa büyük ihtimalle çalışır.
- Philips ya da TP Vision ile bir bağlantısı yoktur.

## Sorun giderme

```sh
adb shell dumpsys hdmi_control | grep -E "logical_address|mArcEstablished"   # soundbar (0x05) hatta mı?
adb logcat -s SoundbarWake HDMI                                               # canlı log
adb shell dumpsys activity services io.github.odcaner.soundbarwake            # servis çalışıyor mu?
```

**ARC tamamen koptuysa** (TV *"Ses sistemine geçilemiyor, TV hoparlörü etkin"* diyorsa ve logda `No AVR device` görünüyorsa): soundbar CEC'den tamamen düşmüş demektir. Bu uygulama bunu düzeltemez. **Soundbar'ın fişini çekip 1–2 dakika bekle.** Başka bir CEC 2.0 cihazı (ör. Apple TV) da kısa süreliğine adres 5'i kapabilir. Bu durumda Apple TV'de *TV'leri ve Alıcıları Denetle* ayarını kapatmak işe yarar.

## Kaynaktan derleme

Gradle gerekmez. JDK 17+ ve Android SDK (build-tools + 26 ya da üstü herhangi bir platform) yeterli:

```sh
export ANDROID_HOME=/path/to/android/sdk
./build.sh          # yerel bir debug anahtarıyla imzalı soundbar-wake.apk oluşturur
```

## Lisans

MIT
