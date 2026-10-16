package com.example.knockcollector;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecorderActivity extends Activity implements SensorEventListener {

    private static final String DEV = "/dev/input/event7";
    private static final int DUR = 6000;
    private static final int SR = 48000;

    private TextView status;
    private SensorManager sm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile boolean capturing = false;
    private long t0;
    private File outDir;
    private BufferedWriter accW;
    private Process proc;
    private AudioRecord ar;
    private int counter = 0;
    private String lastTouchErr = "";

    private int dp(float v) { return (int) (getResources().getDisplayMetrics().density * v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        outDir = getExternalFilesDir(null);
        try { stopService(new Intent(this, KnockService.class)); } catch (Exception ignored) {}

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));
        int primary = Color.parseColor("#3F51B5");

        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(primary);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(18), dp(16), dp(18));
        bar.setElevation(dp(6));
        TextView t = new TextView(this);
        t.setText("\u5f55\u6837\u672c\uff08\u8c03\u53c2\u6570\u7528\uff09");
        t.setTextColor(Color.WHITE); t.setTextSize(19);
        bar.addView(t);
        root.addView(bar);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(Color.parseColor("#424242"));
        status.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable card = new GradientDrawable();
        card.setColor(Color.WHITE); card.setCornerRadius(dp(10));
        status.setBackground(card); status.setElevation(dp(2));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1f);
        cp.setMargins(dp(14), dp(14), dp(14), dp(8));
        root.addView(status, cp);

        addBtn(root, "\u5f55\uff1a\u6307\u5173\u8282\u6572", "knuckle");
        addBtn(root, "\u5f55\uff1a\u624b\u6307\u7528\u529b\u6309", "press");
        setContentView(root);

        try { stopService(new Intent(this, KnockService.class)); } catch (Exception ignored) {}
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
        say("\u6bcf\u6b21\u5f55 6 \u79d2\u30023 \u79d2\u5012\u8ba1\u65f6\u540e\u5f00\u59cb\u3002\n\n"
          + "\u6307\u5173\u8282\uff1a\u7528\u4f60\u5e73\u65f6\u90a3\u4e2a\u529b\u5ea6\u6572 3~4 \u4e0b\uff08\u4e0d\u7528\u5f88\u91cd\uff09\n"
          + "\u624b\u6307\uff1a\u50cf\u521a\u624d\u90a3\u6837\u7528\u529b\u6309 3~4 \u4e0b\n\n"
          + "\u6bcf\u79cd\u5f55 4 \u6b21\u5c31\u591f\u3002\u5f55\u5b8c\u544a\u8bc9\u6211\u3002");
    }

    private void say(String s) { status.setText(s); }

    private void addBtn(LinearLayout root, final String label, final String key) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { start(key); }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(14), dp(5), dp(14), dp(0));
        root.addView(btn, p);
    }

    private void start(final String key) {
        if (capturing) return;
        final int[] left = {3};
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (left[0] > 0) { say("\u51c6\u5907... " + left[0]); left[0]--; h.postDelayed(this, 1000); }
                else begin(key);
            }
        };
        h.post(tick);
    }

    private void begin(String key) {
        capturing = true; counter++;
        t0 = SystemClock.elapsedRealtime();
        String st = new SimpleDateFormat("HHmmss", Locale.US).format(new Date());
        final File tf = new File(outDir, "sample_" + key + "_" + counter + "_" + st + ".touch.txt");
        File af = new File(outDir, "sample_" + key + "_" + counter + "_" + st + ".accel.txt");
        final File wf = new File(outDir, "sample_" + key + "_" + counter + "_" + st + ".wav");
        say("\u25cf \u5f55\u5236\u4e2d (" + key + ") 5 \u79d2\uff0c\u73b0\u5728\u5f00\u59cb\uff01");

        try {
            accW = new BufferedWriter(new FileWriter(af));
            accW.write("# t_ms,x,y,z\n");
            sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
        } catch (Exception e) { accW = null; }

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ProcessBuilder pb = new ProcessBuilder("su", "-c", "/system/bin/getevent -lt " + DEV);
                    pb.redirectErrorStream(true);
                    proc = pb.start();
                    BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    BufferedWriter w = new BufferedWriter(new FileWriter(tf));
                    String line; int n = 0;
                    while (capturing && (line = r.readLine()) != null) {
                        w.write("[" + (SystemClock.elapsedRealtime() - t0) + "] " + line);
                        w.newLine();
                        if (++n % 40 == 0) w.flush();
                    }
                    w.flush(); w.close();
                } catch (Exception e) { lastTouchErr = String.valueOf(e); }
            }
        }).start();

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    AudioRecord rec = null;
                    try {
                        rec = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, SR,
                                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, SR)*2);
                        if (rec.getState() != AudioRecord.STATE_INITIALIZED) { rec.release(); rec = null; }
                    } catch (Exception e) { rec = null; }
                    if (rec == null) rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SR,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, SR)*2);
                    ar = rec;
                    FileOutputStream fos = new FileOutputStream(wf);
                    fos.write(new byte[44]);
                    rec.startRecording();
                    byte[] buf = new byte[8192]; long total = 0;
                    while (capturing) { int n = rec.read(buf, 0, buf.length); if (n > 0) { fos.write(buf, 0, n); total += n; } }
                    try { rec.stop(); } catch (Exception ignored) {}
                    rec.release(); fos.close();
                    RandomAccessFile raf = new RandomAccessFile(wf, "rw");
                    raf.seek(0); raf.write(wavHeader(SR, 1, 16, total)); raf.close();
                } catch (Exception e) { }
            }
        }).start();

        h.postDelayed(new Runnable() { @Override public void run() { stop(); } }, DUR);
    }

    private static byte[] wavHeader(int sr, int ch, int bits, long dataLen) {
        int br = sr*ch*bits/8; byte[] hh = new byte[44];
        System.arraycopy("RIFF".getBytes(), 0, hh, 0, 4); putLE(hh, 4, 36+dataLen);
        System.arraycopy("WAVE".getBytes(), 0, hh, 8, 4);
        System.arraycopy("fmt ".getBytes(), 0, hh, 12, 4); putLE(hh, 16, 16);
        putLE16(hh, 20, 1); putLE16(hh, 22, ch); putLE(hh, 24, sr); putLE(hh, 28, br);
        putLE16(hh, 32, ch*bits/8); putLE16(hh, 34, bits);
        System.arraycopy("data".getBytes(), 0, hh, 36, 4); putLE(hh, 40, dataLen);
        return hh;
    }
    private static void putLE(byte[] b, int o, long v) { b[o]=(byte)(v&0xff); b[o+1]=(byte)((v>>8)&0xff); b[o+2]=(byte)((v>>16)&0xff); b[o+3]=(byte)((v>>24)&0xff); }
    private static void putLE16(byte[] b, int o, int v) { b[o]=(byte)(v&0xff); b[o+1]=(byte)((v>>8)&0xff); }

    private void stop() {
        capturing = false;
        try { sm.unregisterListener(this); } catch (Exception ignored) {}
        if (proc != null) { try { proc.destroy(); } catch (Exception ignored) {} }
        if (ar != null) { try { ar.stop(); } catch (Exception ignored) {} }
        try { if (accW != null) { accW.flush(); accW.close(); } } catch (Exception ignored) {}
        say("\u2713 \u7b2c " + counter + " \u6b21\u5b8c\u6210" + (lastTouchErr.isEmpty() ? "" : "\n(\u89e6\u6478\u8bfb\u53d6\u5931\u8d25: " + lastTouchErr + ")")
            + "\n\n\u7ee7\u7eed\u5f55\uff0c\u6216\u544a\u8bc9\u6211\u5b8c\u4e86\u3002");
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (!capturing || accW == null) return;
        try { accW.write((SystemClock.elapsedRealtime()-t0) + "," + e.values[0] + "," + e.values[1] + "," + e.values[2] + "\n"); } catch (Exception ignored) {}
    }
    @Override public void onAccuracyChanged(Sensor s, int a) {}
}
