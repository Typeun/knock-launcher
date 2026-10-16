package com.example.quickshot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        final String dir = "/sdcard/Pictures/Screenshots";
        final String file = dir + "/Screenshot_" + ts + ".png";
        new Thread(new Runnable() {
            @Override public void run() {
                String tmp;
                try {
                    String cmd = "mkdir -p " + dir + " && screencap -p " + file + " && chmod 666 " + file;
                    ProcessBuilder pb = new ProcessBuilder("su", "-c", cmd);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    StringBuilder out = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) out.append(line);
                    int code = p.waitFor();
                    if (code == 0) tmp = "\u5df2\u622a\u5c4f\uff1a" + file;
                    else tmp = "\u5931\u8d25(" + code + ") " + out;
                } catch (Exception e) {
                    tmp = "\u9700\u8981 root\uff1a" + e;
                }
                final String msg = tmp;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        }).start();
    }
}
