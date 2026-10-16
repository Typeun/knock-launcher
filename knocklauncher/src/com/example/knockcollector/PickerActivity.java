package com.example.knockcollector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PickerActivity extends Activity {

    public static final String PREFS = "knock";

    private static class Item {
        String pkg, cls, label, sub;
        Drawable icon;
        boolean isActivity;
    }

    private final List<Item> all = new ArrayList<Item>();
    private final List<Item> shown = new ArrayList<Item>();
    private AppsAdapter adapter;
    private EditText search;
    private Button modeBtn;
    private boolean activityMode = false;

    private int dp(float v) { return (int) (getResources().getDisplayMetrics().density * v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        int primary = Color.parseColor("#3F51B5");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(primary);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(10), dp(10));
        bar.setElevation(dp(6));
        TextView title = new TextView(this);
        title.setText("\u6253\u5f00\u4ec0\u4e48\uff1f");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        modeBtn = new Button(this);
        modeBtn.setText("\u5e94\u7528");
        modeBtn.setAllCaps(false);
        modeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { activityMode = !activityMode; reload(); }
        });
        bar.addView(modeBtn);
        root.addView(bar);

        search = new EditText(this);
        search.setHint("\u8f93\u5165\u5173\u952e\u8bcd\u641c\u7d22\uff08\u5e94\u7528\u540d / \u5305\u540d / \u7c7b\u540d\uff09");
        search.setSingleLine(true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(dp(12), dp(12), dp(12), dp(6));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable box = new GradientDrawable();
        box.setColor(Color.WHITE);
        box.setCornerRadius(dp(8));
        box.setStroke(dp(1), Color.parseColor("#DDDDDD"));
        search.setBackground(box);
        root.addView(search, sp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(6));
        list.setPadding(dp(12), dp(6), dp(12), dp(12));
        list.setClipToPadding(false);
        adapter = new AppsAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        reload();
        search.requestFocus();
        search.postDelayed(new Runnable() {
            public void run() {
                try {
                    InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    im.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
                } catch (Exception ignored) {}
            }
        }, 350);
    }

    private void reload() {
        modeBtn.setText(activityMode ? "Activity" : "\u5e94\u7528");
        all.clear();
        PackageManager pm = getPackageManager();
        if (!activityMode) {
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ri = pm.queryIntentActivities(main, 0);
            for (ResolveInfo r : ri) {
                Item it = new Item();
                it.pkg = r.activityInfo.packageName;
                it.cls = r.activityInfo.name;
                it.label = r.loadLabel(pm).toString();
                it.sub = it.pkg;
                it.icon = r.loadIcon(pm);
                it.isActivity = false;
                if (!it.pkg.equals(getPackageName())) all.add(it);
            }
        } else {
            List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);
            for (PackageInfo pi : pkgs) {
                if (pi.activities == null) continue;
                if (pi.packageName.equals(getPackageName())) continue;
                CharSequence appLabel = pm.getApplicationLabel(pi.applicationInfo);
                Drawable appIcon = null;
                try { appIcon = pm.getApplicationIcon(pi.packageName); } catch (Exception ignored) {}
                for (ActivityInfo ai : pi.activities) {
                    Item it = new Item();
                    it.pkg = ai.packageName;
                    it.cls = ai.name;
                    it.label = appLabel + " \u203a " + shortName(ai.name);
                    it.sub = ai.name;
                    it.icon = appIcon;
                    it.isActivity = true;
                    all.add(it);
                }
            }
        }
        Collections.sort(all, new Comparator<Item>() {
            public int compare(Item a, Item b) { return a.label.compareToIgnoreCase(b.label); }
        });
        filter(search.getText().toString());
    }

    private static String shortName(String cls) {
        int i = cls.lastIndexOf('.');
        return i >= 0 ? cls.substring(i + 1) : cls;
    }

    private void filter(String q) {
        shown.clear();
        String s = q.trim().toLowerCase();
        int cap = 80, total = 0;
        for (Item it : all) {
            if (s.length() == 0 || it.label.toLowerCase().contains(s)
                    || it.pkg.toLowerCase().contains(s) || it.cls.toLowerCase().contains(s)) {
                total++;
                if (shown.size() < cap) shown.add(it);
            }
        }
        adapter.notifyDataSetChanged();
        setTitle("\u5171 " + total + " \u9879" + (total > cap ? " \uff08\u663e\u793a\u524d " + cap + "\uff0c\u8bf7\u8f93\u5165\u7b5b\u9009\uff09" : ""));
    }

    private class AppsAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout row = new LinearLayout(PickerActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setElevation(dp(2));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(8));
            row.setBackground(bg);

            final Item it = shown.get(pos);
            ImageView ic = new ImageView(PickerActivity.this);
            if (it.icon != null) ic.setImageDrawable(it.icon);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(38), dp(38));
            ip.setMargins(0, 0, dp(12), 0);
            row.addView(ic, ip);

            LinearLayout col = new LinearLayout(PickerActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(PickerActivity.this);
            t1.setText(it.label);
            t1.setTextSize(15);
            t1.setTextColor(Color.parseColor("#212121"));
            TextView t2 = new TextView(PickerActivity.this);
            t2.setText(it.sub);
            t2.setTextSize(11);
            t2.setTextColor(Color.parseColor("#9E9E9E"));
            col.addView(t1); col.addView(t2);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { launch(it); }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { askDefault(it); return true; }
            });
            return row;
        }
    }

    private void askDefault(final Item it) {
        new AlertDialog.Builder(this)
                .setTitle("\u8bbe\u4e3a\u9ed8\u8ba4\uff1f")
                .setMessage(it.label + "\n\n\u8bbe\u540e\uff1a\u6572\u4e00\u4e0b\u76f4\u63a5\u6253\u5f00\u5b83\uff0c\u4e0d\u518d\u5f39\u9009\u62e9\u5668\u3002")
                .setPositiveButton("\u8bbe\u4e3a\u9ed8\u8ba4", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                        sp.edit().putString("default_pkg", it.pkg)
                                 .putString("default_cls", it.cls)
                                 .putString("default_label", it.label).apply();
                        Toast.makeText(PickerActivity.this, "\u5df2\u8bbe\u4e3a\u9ed8\u8ba4", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
    }

    private void launch(Item it) {
        LaunchUtil.start(this, it.pkg, it.cls);
        finish();
    }

    @Override public void onBackPressed() { finish(); }
}
