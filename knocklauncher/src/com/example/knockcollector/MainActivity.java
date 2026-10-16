package com.example.knockcollector;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    private TextView status;
    private Button btnToggle;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile String rootResult = "-";

    private int dp(float v) { return (int) (getResources().getDisplayMetrics().density * v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int primary = Color.parseColor("#3F51B5");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(primary);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(18), dp(16), dp(18));
        bar.setElevation(dp(6));
        TextView title = new TextView(this);
        title.setText("\u6307\u5173\u8282\u542f\u52a8\u5668");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        bar.addView(title);
        root.addView(bar);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(Color.parseColor("#424242"));
        status.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable card = new GradientDrawable();
        card.setColor(Color.WHITE);
        card.setCornerRadius(dp(10));
        status.setBackground(card);
        status.setElevation(dp(2));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1f);
        cp.setMargins(dp(14), dp(14), dp(14), dp(8));
        root.addView(status, cp);

        btnToggle = button("\u5f00\u542f\u670d\u52a1", new View.OnClickListener() {
            public void onClick(View v) { toggle(); }
        });
        addBtn(root, btnToggle);

        addBtn(root, button("\u5f55\u6837\u672c\uff08\u8c03\u53c2\u6570\uff09", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, RecorderActivity.class)); }
        }));
        addBtn(root, button("\u6d4b\u8bd5 Root\uff08\u8bfb\u89e6\u6478\uff09", new View.OnClickListener() {
            public void onClick(View v) { testRoot(); }
        }));
        addBtn(root, button("\u6253\u5f00\u9009\u62e9\u5668\uff08\u6d4b\u8bd5\uff09", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, PickerActivity.class)); }
        }));
        addBtn(root, button("\u6e05\u9664\u9ed8\u8ba4\u76ee\u6807", new View.OnClickListener() {
            public void onClick(View v) {
                getSharedPreferences(PickerActivity.PREFS, MODE_PRIVATE).edit().clear().apply();
            }
        }));
        addBtn(root, button("\u6388\u6743\uff1a\u60ac\u6d6e\u7a97", new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); } catch (Exception e) { }
            }
        }));
        addBtn(root, button("\u6388\u6743\uff1a\u9ea6\u514b\u98ce / \u901a\u77e5", new View.OnClickListener() {
            public void onClick(View v) { askPerms(); }
        }));

        setContentView(root);
        askPerms();
        h.post(tick);
    }

    private Button button(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void addBtn(LinearLayout root, Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(14), dp(5), dp(14), dp(0));
        root.addView(b, p);
    }

    private void askPerms() {
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;
        boolean notif = Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        if (mic || notif) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}, 1);
            else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    private void testRoot() {
        rootResult = "\u6d4b\u8bd5\u4e2d...";
        new Thread(new Runnable() {
            @Override public void run() {
                StringBuilder sb = new StringBuilder();
                try {
                    ProcessBuilder pb = new ProcessBuilder("su", "-c", "id; /system/bin/getevent -pl /dev/input/event7 | head -3");
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append(" | ");
                    p.waitFor();
                } catch (Exception e) { sb.append("ERR ").append(e); }
                rootResult = sb.toString();
            }
        }).start();
    }

    private void toggle() {
        if (KnockService.RUNNING) stopService(new Intent(this, KnockService.class));
        else {
            Intent i = new Intent(this, KnockService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        }
        h.postDelayed(tick, 400);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            btnToggle.setText(KnockService.RUNNING ? "\u505c\u6b62\u670d\u52a1\uff08\u8fd0\u884c\u4e2d\uff09" : "\u5f00\u542f\u670d\u52a1");
            boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(MainActivity.this);
            StringBuilder sb = new StringBuilder();
            sb.append("\u670d\u52a1\uff1a").append(KnockService.RUNNING ? "\u8fd0\u884c\u4e2d \u25cf" : "\u505c\u6b62").append("\n");
            sb.append("\u60ac\u6d6e\u7a97\uff1a").append(overlay ? "\u2713" : "\u2717 \u672a\u6388\u6743").append("\n");
            sb.append("\u9ea6\u514b\u98ce\uff1a").append(KnockService.MIC_STATUS).append("\n");
            sb.append("\u89e6\u6478\u901a\u9053\uff1a").append(KnockService.TOUCH_STATUS)
              .append("  (\u884c\u6570 ").append(KnockService.TOUCH_LINES).append(")\n");
            sb.append("Root\u6d4b\u8bd5\uff1a").append(rootResult).append("\n\n");
            sb.append("\u5cf0\u503c\uff08\u4fdd\u6301 2.5 \u79d2\uff09\uff1a\n");
            sb.append(String.format("  \u632f\u52a8 %.1f m/s2  \uff08\u9600\u503c 6.0\uff09%n", KnockService.PEAK_ACCEL));
            sb.append(String.format("  \u58f0\u97f3 %.0f  \uff08\u9600\u503c 500\uff09%n", KnockService.PEAK_MIC));
            sb.append("  \u89e6\u63a7\u4e8b\u4ef6 ").append(KnockService.LAST_TOUCH_EVENTS).append("  \uff08\u9600\u503c 15\uff09\n");
            String dpkg = getSharedPreferences(PickerActivity.PREFS, MODE_PRIVATE).getString("default_label", "");
            sb.append("\n\u9ed8\u8ba4\u76ee\u6807\uff1a").append(dpkg == null || dpkg.length() == 0 ? "\u65e0\uff08\u5f39\u9009\u62e9\u5668\uff09" : dpkg);
            sb.append("\n\u547d\u4e2d\u6b21\u6570\uff1a").append(KnockService.HIT_COUNT);
            status.setText(sb.toString());
            h.postDelayed(this, 400);
        }
    };

    @Override protected void onDestroy() { h.removeCallbacks(tick); super.onDestroy(); }
}
