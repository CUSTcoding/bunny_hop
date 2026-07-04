package com.bunnyhop.app;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String NOTIFICATION_CHANNEL_ID = "bunny_hop_detections";
    private static final String TAG = "MainActivity";
    private static final long RELAY_DURATION_MS = 15_000L;
    private static final long RELAY_COOLDOWN_MS = 30_000L;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothLeAdvertiser bleAdvertiser;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Button toggleButton;
    private Button adminButton;
    private Button testAlertButton;
    private Button repeatAlertButton;
    private TextView statusText;

    private AlertDatabaseHelper dbHelper;
    private boolean scanning = false;
    /** deviceName → last advertising packet timestamp */
    private final Map<String, Long> lastSeenByZone = new HashMap<>();
    /** deviceName → currently in range */
    private final Map<String, Boolean> zonesInRange = new HashMap<>();
    /** deviceName → last relay timestamp */
    private final Map<String, Long> lastRelayByZone = new HashMap<>();
    private boolean relayingAlert = false;

    private final Runnable rangeWatchdog = new Runnable() {
        @Override
        public void run() {
            if (scanning) {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, Long> entry : new HashMap<>(lastSeenByZone).entrySet()) {
                    String deviceName = entry.getKey();
                    if (now - entry.getValue() >= BunnyHopConfig.LOST_TIMEOUT_MS) {
                        zonesInRange.put(deviceName, false);
                    }
                }
                refreshActiveZonesStatus();
            }
            if (scanning) {
                handler.postDelayed(this, 2_000L);
            }
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            String deviceName = resolveDeviceName(result);
            if (deviceName == null || !matchesBeacon(result, deviceName)) {
                return;
            }

            lastSeenByZone.put(deviceName, System.currentTimeMillis());
            boolean wasInRange = Boolean.TRUE.equals(zonesInRange.get(deviceName));

            if (!wasInRange) {
                zonesInRange.put(deviceName, true);
                onZoneDetected(deviceName, result);
            } else {
                refreshActiveZonesStatus();
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            toggleButton.setText(R.string.start_hop);
            updateStatus(getString(R.string.scan_failed_code, errorCode));
            Toast.makeText(MainActivity.this, R.string.scan_failed, Toast.LENGTH_LONG).show();
        }
    };

    private final Runnable relayStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopRelayAdvertising();
        }
    };

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            relayingAlert = true;
            Log.d(TAG, "Retransmissão BLE iniciada");
        }

        @Override
        public void onStartFailure(int errorCode) {
            relayingAlert = false;
            Log.w(TAG, "Falha ao iniciar retransmissão BLE: " + errorCode);
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (hasRequiredPermissions()) {
                            Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, R.string.permissions_denied, Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toggleButton = findViewById(R.id.toggleButton);
        adminButton = findViewById(R.id.adminButton);
        testAlertButton = findViewById(R.id.testAlertButton);
        repeatAlertButton = findViewById(R.id.repeatAlertButton);
        statusText = findViewById(R.id.statusText);

        dbHelper = new AlertDatabaseHelper(this);
        createNotificationChannel();

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null) {
            bluetoothAdapter = manager.getAdapter();
        }
        if (bluetoothAdapter != null) {
            bleAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        }

        if (bluetoothAdapter == null) {
            updateStatus(getString(R.string.no_bluetooth));
            toggleButton.setEnabled(false);
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        updateStatus(getString(R.string.idle));

        toggleButton.setOnClickListener(v -> {
            if (scanning) {
                stopHop();
            } else {
                if (!hasRequiredPermissions()) {
                    requestRequiredPermissions();
                    return;
                }
                if (!bluetoothAdapter.isEnabled()) {
                    Toast.makeText(this, R.string.enable_bluetooth, Toast.LENGTH_LONG).show();
                    return;
                }
                startHop();
            }
        });

        adminButton.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AdminActivity.class));
        });

        testAlertButton.setOnClickListener(v -> {
            String testZone = BunnyHopConfig.zoneLabelForDeviceName("CHEIAS_ZONA_1");
            AlertDispatcher.dispatch(this, testZone, "Teste MVP: zona em risco confirmada.", "SMS");
            Toast.makeText(this, "Teste de alerta enviado", Toast.LENGTH_SHORT).show();
        });

        repeatAlertButton.setOnClickListener(v -> {
            relayDetectedZone("CHEIAS_ZONA_1");
            updateStatus(getString(R.string.relay_active));
        });

        requestRequiredPermissions();
        updateStatus(getString(R.string.relay_inactive));
    }

    @Override
    protected void onDestroy() {
        stopHop();
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.onDestroy();
    }

    private void startHop() {
        if (bleScanner == null) {
            Toast.makeText(this, R.string.scanner_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BunnyHopConfig.SERVICE_UUID_PARSED))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        bleScanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        scanning = true;
        lastSeenByZone.clear();
        zonesInRange.clear();
        toggleButton.setText(R.string.stop_hop);
        updateStatus(getString(R.string.scanning));
        handler.post(rangeWatchdog);
    }

    private void stopHop() {
        handler.removeCallbacks(rangeWatchdog);
        handler.removeCallbacks(relayStopRunnable);
        stopRelayAdvertising();
        if (bleScanner != null && scanning) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED) {
                bleScanner.stopScan(scanCallback);
            }
        }
        scanning = false;
        lastSeenByZone.clear();
        zonesInRange.clear();
        if (toggleButton != null) {
            toggleButton.setText(R.string.start_hop);
        }
        if (statusText != null) {
            updateStatus(getString(R.string.idle));
        }
    }

    @Nullable
    private String resolveDeviceName(@NonNull ScanResult result) {
        if (result.getScanRecord() == null) {
            return null;
        }
        return result.getScanRecord().getDeviceName();
    }

    private boolean matchesBeacon(@NonNull ScanResult result, @NonNull String deviceName) {
        if (BunnyHopConfig.isFloodBeaconName(deviceName)) {
            return hasServiceUuid(result);
        }
        return false;
    }

    private boolean hasServiceUuid(@NonNull ScanResult result) {
        if (result.getScanRecord() == null) {
            return false;
        }
        List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();
        if (uuids != null) {
            for (ParcelUuid uuid : uuids) {
                if (BunnyHopConfig.SERVICE_UUID_PARSED.equals(uuid.getUuid())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void onZoneDetected(@NonNull String deviceName, @NonNull ScanResult result) {
        String zoneLabel = BunnyHopConfig.zoneLabelForDeviceName(deviceName);
        String address = result.getDevice().getAddress();
        String alertMessage = dbHelper.getActiveAlertMessage(deviceName);
        if (alertMessage == null) {
            alertMessage = getString(R.string.detection_message, zoneLabel);
        }

        Alert alert = null;
        for (Alert item : dbHelper.getAlerts()) {
            if (deviceName.equals(item.getDeviceName()) && item.isActive()) {
                alert = item;
                break;
            }
        }

        updateStatus(getString(R.string.zone_detected_status, zoneLabel, address));
        showDetectionNotification(zoneLabel, deviceName, alertMessage);
        showDetectionDialog(zoneLabel, alertMessage);
        relayDetectedZone(deviceName);

        if (alert != null) {
            AlertDispatcher.dispatch(this, zoneLabel, alertMessage, alert.getChannels());
        } else {
            AlertDispatcher.dispatch(this, zoneLabel, alertMessage, "BLE,NOTIFICATION");
        }
    }

    private void refreshActiveZonesStatus() {
        List<String> active = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : zonesInRange.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                active.add(BunnyHopConfig.zoneLabelForDeviceName(entry.getKey()));
            }
        }
        if (active.isEmpty()) {
            if (scanning) {
                updateStatus(getString(R.string.scanning));
            }
            return;
        }
        updateStatus(getString(R.string.zones_active_status, String.join(", ", active)));
    }

    private void relayDetectedZone(@NonNull String deviceName) {
        long now = System.currentTimeMillis();
        Long lastRelay = lastRelayByZone.get(deviceName);
        if (lastRelay != null && now - lastRelay < RELAY_COOLDOWN_MS) {
            return;
        }
        lastRelayByZone.put(deviceName, now);

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (bleAdvertiser == null) {
            bleAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        }
        if (bleAdvertiser == null) {
            Toast.makeText(this, "Não foi possível ativar a retransmissão BLE", Toast.LENGTH_SHORT).show();
            return;
        }

        if (relayingAlert) {
            stopRelayAdvertising();
        }

        bluetoothAdapter.setName(deviceName);

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(BunnyHopConfig.SERVICE_UUID_PARSED))
                .build();

        bleAdvertiser.startAdvertising(settings, data, advertiseCallback);
        handler.removeCallbacks(relayStopRunnable);
        handler.postDelayed(relayStopRunnable, RELAY_DURATION_MS);
        updateStatus(getString(R.string.relay_active));
        Toast.makeText(this, "Alerta retransmitido para aparelhos próximos", Toast.LENGTH_SHORT).show();
    }

    private void stopRelayAdvertising() {
        if (bleAdvertiser != null && relayingAlert) {
            bleAdvertiser.stopAdvertising(advertiseCallback);
        }
        relayingAlert = false;
        updateStatus(getString(R.string.relay_inactive));
    }

    private void showDetectionDialog(@NonNull String zoneLabel, @NonNull String alertMessage) {
        if (isFinishing()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.detection_title)
                .setMessage(alertMessage)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(true)
                .show();
    }

    private void showDetectionNotification(@NonNull String zoneLabel, @NonNull String deviceName, @NonNull String alertMessage) {
        if (!hasRequiredPermissions()) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.detection_title))
                .setContentText(alertMessage)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(
                BunnyHopConfig.notificationIdForZone(deviceName),
                builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(getString(R.string.notification_channel_desc));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void updateStatus(@NonNull String text) {
        statusText.setText(text);
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                    == PackageManager.PERMISSION_GRANTED
                    && hasNotificationPermission()
                    && hasSmsPermission();
        }
        return hasNotificationPermission() && hasSmsPermission();
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean hasSmsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.CALL_PHONE,
            });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.CALL_PHONE,
            });
        }
    }
}
