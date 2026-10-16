package com.example.knockcollector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayDeque;

public class KnockService extends Service implements SensorEventListener {

    public static final String TAG = "KnockLauncher";
    public static volatile boolean RUNNING = false;
    public static volatile double PEAK_ACCEL = 0, PEAK_MIC = 0;
    public static volatile int LAST_TOUCH_EVENTS = 0;
    public static volatile String TOUCH_STATUS = "-";
    public static volatile long TOUCH_LINES = 0;
    public static volatile int HIT_COUNT = 0;
    public static volatile String MIC_STATUS = "-";
    public static volatile long LAST_HIT = 0;

    private static final String DEV = "/dev/input/event7";
    private static final double ACCEL_THR = 3.5;
    private static final double MIC_THR = 250.0;
    private static final int TOUCH_MIN_EVENTS = 25;
    private static final long WIN = 260;
    private static final long COOLDOWN = 1200;
    private static final long PEAK_HOLD = 2500;

    private final ArrayDeque<long[]> micQ = new ArrayDeque<long[]>();
    private final ArrayDeque<long[]> touchQ = new ArrayDeque<long[]>();

    private SensorManager sm;
    private AudioRecord ar;
    private Process evdev;
    private volatile boolean run = true;
    private double prevMag = 0;
    private long lastPeak = 0, accelPeakAt = 0, micPeakAt = 0;

    @Override public void onCreate() {
        super.onCreate();
        RUNNING = true;
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
        startForegroundNotif();
        startMic();
        startEvdev();
    }

    private void startForegroundNotif() {
        String ch = "knock";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel c = new NotificationChannel(ch, "Knock Launcher", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(c);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, ch) : new Notification.Builder(this);
        b.setContentTitle("Knock Launcher")
         .setContentText("\u76d1\u542c\u4e2d")
         .setSmallIcon(android.R.drawable.ic_menu_view)
         .setContentIntent(pi)
         .setOngoing(true);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1001, n, 0x00000080);
        else startForeground(1001, n);
    }

    private void startMic() {
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    int sr = 48000;
                    int min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    AudioRecord rec = null;
                    try {
                        rec = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, sr,
                                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, sr) * 2);
                        if (rec.getState() != AudioRecord.STATE_INITIALIZED) { rec.release(); rec = null; }
                    } catch (Exception e) { rec = null; }
                    if (rec == null) {
                        rec = new AudioRecord(MediaRecorder.AudioSource.MIC, sr,
                                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, sr) * 2);
                    }
                    ar = rec;
                    rec.startRecording();
                    MIC_STATUS = "ok";
                    short[] buf = new short[1024];
                    long frames = 0;
                    long base = SystemClock.elapsedRealtime();
                    while (run) {
                        int n = rec.read(buf, 0, buf.length);
                        if (n <= 0) continue;
                        for (int i = 0; i + 48 <= n; i += 48) {
                            double s = 0;
                            for (int j = 0; j < 48; j++) { double v = buf[i + j]; s += v * v; }
                            double rms = Math.sqrt(s / 48.0);
                            long now = SystemClock.elapsedRealtime();
                            if (rms > PEAK_MIC || now - micPeakAt > PEAK_HOLD) { PEAK_MIC = rms; micPeakAt = now; }
                            long t = base + (frames + i) / 48;
                            if (rms > MIC_THR / 2.5) {
                                synchronized (micQ) { micQ.addLast(new long[]{t, (long) rms}); trim(micQ, t); }
                            }
                        }
                        frames += n;
                    }
                    try { rec.stop(); } catch (Exception ignored) {}
                    rec.release();
                } catch (Exception e) { MIC_STATUS = "err:" + e; Log.e(TAG, "mic", e); }
            }
        });
        t.setDaemon(true); t.start();
    }

    private void startEvdev() {
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    TOUCH_STATUS = "\u542f\u52a8getevent...";
                    ProcessBuilder pb = new ProcessBuilder("su", "-c", "/system/bin/getevent -lt " + DEV);
                    pb.redirectErrorStream(true);
                    evdev = pb.start();
                    BufferedReader r = new BufferedReader(new InputStreamReader(evdev.getInputStream()));
                    String line;
                    long start = -1; int count = 0;
                    while (run && (line = r.readLine()) != null) {
                        TOUCH_LINES++;
                        if (TOUCH_LINES == 1) TOUCH_STATUS = "ok";
                        if (line.contains("ABS_MT_TRACKING_ID")) {
                            if (line.contains("ffffffff")) {
                                if (start > 0) {
                                    synchronized (touchQ) { touchQ.addLast(new long[]{start, count}); trim(touchQ, start); }
                                    LAST_TOUCH_EVENTS = count;
                                    start = -1; count = 0;
                                }
                            } else if (start < 0) {
                                start = SystemClock.elapsedRealtime(); count = 0;
                            }
                        } else if (start > 0) count++;
                    }
                    TOUCH_STATUS = "\u7ec8\u6b62(\u884c\u6570=" + TOUCH_LINES + ")";
                } catch (Exception e) { TOUCH_STATUS = "err:" + e; Log.e(TAG, "evdev", e); }
            }
        });
        t.setDaemon(true); t.start();
    }

    private void trim(ArrayDeque<long[]> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst()[0] > 2000) q.pollFirst();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        double mag = Math.sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2]);
        double dev = Math.abs(mag - 9.80665);
        long now = SystemClock.elapsedRealtime();
        if (dev > PEAK_ACCEL || now - accelPeakAt > PEAK_HOLD) { PEAK_ACCEL = dev; accelPeakAt = now; }
        if (dev > ACCEL_THR && dev > prevMag && now - lastPeak > 200) { lastPeak = now; checkHit(now); }
        prevMag = dev;
    }

    private void checkHit(long now) {
        if (now - LAST_HIT < COOLDOWN) return;
        boolean mic = false, touch = false;
        synchronized (micQ) { for (long[] m : micQ) if (Math.abs(m[0]-now) <= WIN && m[1] >= MIC_THR) { mic = true; break; } }
        synchronized (touchQ) { for (long[] t : touchQ) if (Math.abs(t[0]-now) <= WIN && t[1] >= TOUCH_MIN_EVENTS) { touch = true; break; } }
        Log.i(TAG, "hitcheck accel mic=" + mic + " touch=" + touch + " touchQ=" + touchQ.size());
        if (mic && touch) {
            LAST_HIT = now; HIT_COUNT++;
            android.content.SharedPreferences sp = getSharedPreferences(PickerActivity.PREFS, MODE_PRIVATE);
            String dp = sp.getString("default_pkg", "");
            String dc = sp.getString("default_cls", "");
            if (dp != null && dp.length() > 0 && dc != null && dc.length() > 0) {
                LaunchUtil.start(this, dp, dc);
            } else {
                Intent i = new Intent(KnockService.this, PickerActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                try { startActivity(i); } catch (Exception e) { Log.e(TAG, "start", e); }
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onDestroy() {
        run = false; RUNNING = false;
        try { sm.unregisterListener(this); } catch (Exception ignored) {}
        if (evdev != null) { try { evdev.destroy(); } catch (Exception ignored) {} }
        if (ar != null) { try { ar.stop(); } catch (Exception ignored) {} }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
