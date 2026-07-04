package com.bunnyhop.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.StrictMode;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class AlertDispatcher {

    private static final String TAG = "AlertDispatcher";

    private AlertDispatcher() {
    }

    public static void dispatch(@NonNull Context context, @NonNull String zoneLabel, @NonNull String message, @NonNull String channels) {
        String normalized = (channels == null ? "" : channels).toUpperCase(Locale.ROOT);

        if (normalized.contains("SMS")) {
            sendSms(context, zoneLabel, message);
        }
        if (normalized.contains("USSD")) {
            triggerUssd(context, zoneLabel, message);
        }
        if (normalized.contains("HTTP")) {
            sendHttp(zoneLabel, message);
        }

        if (normalized.isEmpty()) {
            Log.d(TAG, "Sem canais configurados; apenas BLE/notificação local.");
        }
    }

    private static void sendSms(@NonNull Context context, @NonNull String zoneLabel, @NonNull String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            String body = String.format(Locale.getDefault(), "ALERTA CHEIAS - %s: %s", zoneLabel, message);
            smsManager.sendTextMessage(BunnyHopConfig.DEFAULT_SMS_NUMBER, null, body, null, null);
            Log.d(TAG, "SMS enviado para " + BunnyHopConfig.DEFAULT_SMS_NUMBER);
        } catch (Exception ex) {
            Log.w(TAG, "Falha ao enviar SMS", ex);
        }
    }

    private static void triggerUssd(@NonNull Context context, @NonNull String zoneLabel, @NonNull String message) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + BunnyHopConfig.DEFAULT_USSD_CODE));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "USSD iniciado: " + BunnyHopConfig.DEFAULT_USSD_CODE);
        } catch (Exception ex) {
            Log.w(TAG, "Falha ao abrir USSD", ex);
        }
    }

    private static void sendHttp(@NonNull String zoneLabel, @NonNull String message) {
        try {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);

            String payload = String.format(Locale.US, "{\"zone\":\"%s\",\"message\":\"%s\"}",
                    zoneLabel.replace("\"", "\\\""),
                    message.replace("\"", "\\\""));

            URL url = new URL(BunnyHopConfig.DEFAULT_HTTP_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(4_000);
            conn.setReadTimeout(4_000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                Log.d(TAG, "HTTP enviado com sucesso");
            } else {
                Log.w(TAG, "HTTP falhou com status " + status);
            }
        } catch (Exception ex) {
            Log.w(TAG, "Falha ao enviar HTTP; o sistema continua em modo local", ex);
        }
    }
}
