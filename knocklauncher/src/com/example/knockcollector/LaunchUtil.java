package com.example.knockcollector;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class LaunchUtil {
    public static void start(Context ctx, String pkg, String cls) {
        Intent i = new Intent();
        i.setClassName(pkg, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception e) {
            Log.w(KnockService.TAG, "direct start failed, trying root: " + e);
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", "am start -n " + pkg + "/" + cls}); }
            catch (Exception e2) { Log.e(KnockService.TAG, "root start failed", e2); }
        }
    }
}
