package io.github.odcaner.soundbarwake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

/**
 * Watches the TV's HDMI-CEC log. When the audio system (CEC logical address 5)
 * goes to standby while the TV screen is on, toggles the TV's
 * "hdmi_system_audio_control_enabled" setting off and on. The TV then sends
 * &lt;System Audio Mode Request&gt;, which wakes the soundbar/AVR.
 */
public class WakeService extends Service {
    static final String TAG = "SoundbarWake";
    static final String SAM_SETTING = "hdmi_system_audio_control_enabled";

    // Tunable via: adb shell settings put global <key> <ms>
    static final String KEY_DELAY = "soundbar_wake_delay_ms";   // wait before waking (TV-off detection)
    static final String KEY_GAP = "soundbar_wake_toggle_ms";    // off -> on gap
    static final String KEY_QUIET = "soundbar_wake_quiet_ms";  // ignore our own echo
    static final int DEF_DELAY = 1000, DEF_GAP = 500, DEF_QUIET = 20000;

    // Messages from logical address 5 (audio system) meaning "I'm going to sleep":
    // <Terminate ARC> 50:C5, <Set System Audio Mode> 5F:72:00 / 50:72:00
    static final Pattern STANDBY = Pattern.compile(
            "\\[R\\]:<Terminate ARC> 50:C5|\\[R\\]:<Set System Audio Mode> 5[0F]:72:00");

    volatile long lastWake;

    @Override
    public void onCreate() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("watch", "Soundbar Wake", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "watch")
                .setContentTitle("Soundbar Wake is running")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .build();
        startForeground(1, n);
        new Thread(this::watch, "cec-watch").start();
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    int setting(String key, int def) {
        return Settings.Global.getInt(getContentResolver(), key, def);
    }

    void watch() {
        while (true) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "brief", "-T", "1", "-s", "HDMI:D"});
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                r.readLine(); // -T 1 replays one old line, skip it
                String line;
                while ((line = r.readLine()) != null) {
                    if (STANDBY.matcher(line).find()) {
                        Log.i(TAG, "standby detected: " + line);
                        new Thread(this::wake).start();
                    }
                }
                p.destroy();
            } catch (Exception e) {
                Log.w(TAG, "cannot read logcat", e);
            }
            SystemClock.sleep(5000);
        }
    }

    synchronized void wake() {
        if (SystemClock.elapsedRealtime() - lastWake < setting(KEY_QUIET, DEF_QUIET)) return;
        // If the TV itself is turning off, let the soundbar sleep too
        SystemClock.sleep(setting(KEY_DELAY, DEF_DELAY));
        if (!getSystemService(PowerManager.class).isInteractive()) {
            Log.i(TAG, "screen off, not waking");
            return;
        }
        ContentResolver cr = getContentResolver();
        // User picked TV speakers: leave it alone
        if (Settings.Global.getInt(cr, SAM_SETTING, 1) != 1) {
            Log.i(TAG, "system audio control disabled, not waking");
            return;
        }
        lastWake = SystemClock.elapsedRealtime();
        Log.i(TAG, "waking soundbar");
        Settings.Global.putInt(cr, SAM_SETTING, 0);
        SystemClock.sleep(setting(KEY_GAP, DEF_GAP));
        Settings.Global.putInt(cr, SAM_SETTING, 1);
    }
}
