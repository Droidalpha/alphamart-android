package com.alphamart.smsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restart the sender after the phone reboots, if it was running. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (c.getSharedPreferences("gw", 0).getBoolean("running", false)) {
            try { c.startForegroundService(new Intent(c, GatewayService.class)); } catch (Exception ignored) { }
        }
    }
}
