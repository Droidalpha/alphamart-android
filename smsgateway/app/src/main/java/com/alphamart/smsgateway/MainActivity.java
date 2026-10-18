package com.alphamart.smsgateway;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One screen: server address, secret token, Start / Stop. */
public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status;
    private final Handler h = new Handler();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("gw", 0);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);
        setContentView(root);

        TextView t = new TextView(this); t.setText("ALPHAMART SMS sender"); t.setTextSize(22); root.addView(t);
        TextView hint = new TextView(this); hint.setText("Sends shop login codes from this phone's SIM. Keep this phone on, charged and with signal. Use a spare SIM, not your main number."); hint.setPadding(0, pad / 2, 0, pad); root.addView(hint);

        final EditText url = new EditText(this); url.setHint("Server address"); url.setText(prefs.getString("url", "https://shop.thealphamart.com")); url.setInputType(InputType.TYPE_TEXT_VARIATION_URI); root.addView(url);
        final EditText token = new EditText(this); token.setHint("Secret token (from server .env)"); token.setText(prefs.getString("token", "")); token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); root.addView(token);

        Button start = new Button(this); start.setText("Save and start"); root.addView(start);
        Button stop = new Button(this); stop.setText("Stop"); root.addView(stop);
        status = new TextView(this); status.setPadding(0, pad, 0, 0); root.addView(status);

        start.setOnClickListener((View v) -> {
            prefs.edit().putString("url", url.getText().toString().trim()).putString("token", token.getText().toString().trim()).putBoolean("running", true).apply();
            askPermissions();
            if (checkSelfPermission(Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startForegroundService(new Intent(this, GatewayService.class));
            }
        });
        stop.setOnClickListener((View v) -> {
            prefs.edit().putBoolean("running", false).apply();
            stopService(new Intent(this, GatewayService.class));
        });
        tick();
    }

    private void askPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS}, 1);
            else requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 1);
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (prefs.getBoolean("running", false) && checkSelfPermission(Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startForegroundService(new Intent(this, GatewayService.class));
        }
    }

    private void tick() {
        status.setText((prefs.getBoolean("running", false) ? "Running\n" : "Stopped\n") + prefs.getString("conn", "") + "\nLast SMS: " + prefs.getString("last", "none yet"));
        h.postDelayed(this::tick, 2000);
    }
}
