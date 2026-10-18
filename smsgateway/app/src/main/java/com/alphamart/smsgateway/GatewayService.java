package com.alphamart.smsgateway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.SmsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Keeps asking the server for waiting login codes and sends each one from this phone's SIM.
 * Safety: never more than MAX_PER_30_MIN messages in 30 minutes (Android pauses apps that send more).
 */
public class GatewayService extends Service {
    static final String ACTION_SENT = "com.alphamart.smsgateway.SENT";
    static final int MAX_PER_30_MIN = 20;
    private static final String CH = "gw";

    private volatile boolean running;
    private Thread worker;
    private PowerManager.WakeLock wake;
    private final ArrayList<Long> sentTimes = new ArrayList<>();
    private SharedPreferences prefs;

    private final BroadcastReceiver sentReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            final long id = i.getLongExtra("id", -1);
            final boolean ok = getResultCode() == android.app.Activity.RESULT_OK;
            final String err = ok ? null : "radio error " + getResultCode();
            new Thread(() -> report(id, ok, err)).start();
            prefs.edit().putString("last", (ok ? "Sent " : "FAILED ") + java.text.DateFormat.getTimeInstance().format(new java.util.Date())).apply();
        }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        prefs = getSharedPreferences("gw", 0);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "SMS sender", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CH).setContentTitle("ALPHAMART SMS sender is running")
                .setContentText("Sending login codes from this SIM").setSmallIcon(android.R.drawable.stat_notify_chat).setOngoing(true).build();
        startForeground(1, n);
        if (!running) {
            running = true;
            IntentFilter f = new IntentFilter(ACTION_SENT);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(sentReceiver, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(sentReceiver, f);
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alphamart:gw");
            wake.acquire();
            worker = new Thread(this::loop, "gw-loop");
            worker.start();
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        try { unregisterReceiver(sentReceiver); } catch (Exception ignored) { }
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }

    private void loop() {
        while (running) {
            try {
                long now = System.currentTimeMillis();
                sentTimes.removeIf(t -> now - t > 30 * 60 * 1000L);
                if (sentTimes.size() < MAX_PER_30_MIN) {
                    JSONObject r = http("GET", "/sms-gateway/pull?device=" + java.net.URLEncoder.encode(Build.MODEL, "UTF-8"), null);
                    JSONArray ms = r.optJSONArray("messages");
                    for (int i = 0; ms != null && i < ms.length() && running; i++) {
                        JSONObject m = ms.getJSONObject(i);
                        send(m.getLong("id"), m.getString("phone"), m.getString("body"));
                        sentTimes.add(System.currentTimeMillis());
                    }
                    prefs.edit().putString("conn", "Connected " + java.text.DateFormat.getTimeInstance().format(new java.util.Date())).apply();
                }
            } catch (Exception e) {
                prefs.edit().putString("conn", "Cannot reach server: " + e.getMessage()).apply();
            }
            try { Thread.sleep(4000); } catch (InterruptedException ignored) { }
        }
    }

    private void send(long id, String phone, String body) {
        try {
            SmsManager sm = Build.VERSION.SDK_INT >= 31 ? getSystemService(SmsManager.class) : SmsManager.getDefault();
            Intent in = new Intent(ACTION_SENT).setPackage(getPackageName()).putExtra("id", id);
            PendingIntent pi = PendingIntent.getBroadcast(this, (int) (id % 100000), in, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
            ArrayList<String> parts = sm.divideMessage(body);
            if (parts.size() > 1) {
                ArrayList<PendingIntent> pis = new ArrayList<>();
                for (int i = 0; i < parts.size(); i++) pis.add(i == parts.size() - 1 ? pi : null);
                sm.sendMultipartTextMessage(phone, null, parts, pis, null);
            } else {
                sm.sendTextMessage(phone, null, body, pi, null);
            }
        } catch (Exception e) {
            report(id, false, e.getClass().getSimpleName());
            prefs.edit().putString("last", "FAILED: " + e.getMessage()).apply();
        }
    }

    private void report(long id, boolean ok, String err) {
        try {
            JSONObject b = new JSONObject().put("id", id).put("ok", ok);
            if (err != null) b.put("error", err);
            http("POST", "/sms-gateway/report", b.toString());
        } catch (Exception ignored) { }
    }

    private JSONObject http(String method, String path, String body) throws Exception {
        String base = prefs.getString("url", "").replaceAll("/+$", "");
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(8000); c.setReadTimeout(10000);
        c.setRequestProperty("x-gateway-token", prefs.getString("token", ""));
        if (body != null) { c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json"); try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes("UTF-8")); } }
        int code = c.getResponseCode();
        if (code == 401) throw new Exception("wrong token");
        if (code >= 300) throw new Exception("server " + code);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[2048]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new JSONObject(bo.toString("UTF-8"));
        }
    }
}
