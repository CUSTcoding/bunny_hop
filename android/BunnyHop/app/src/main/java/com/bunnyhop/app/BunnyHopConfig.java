package com.bunnyhop.app;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Must stay in sync with bunny_hop_beacon/bunny_hop_constants.py on the PC.
 */
public final class BunnyHopConfig {

    public static final String DEVICE_NAME_PREFIX = "CHEIAS_";
    public static final String SERVICE_UUID = "7f3a9c21-b5e4-4d8f-a1c6-2e8b4f9d0a73";

    public static final String DEFAULT_HTTP_ENDPOINT = "https://example.invalid/alerts";
    public static final String DEFAULT_USSD_CODE = "*123#";
    public static final String DEFAULT_SMS_NUMBER = "+351911000000";

    /** No scan result for this long ⇒ beacon considered out of range. */
    public static final long LOST_TIMEOUT_MS = 10_000L;

    public static final UUID SERVICE_UUID_PARSED = UUID.fromString(SERVICE_UUID);

    private static final Map<String, String> ZONE_LABELS;

    static {
        Map<String, String> zones = new LinkedHashMap<>();
        zones.put("CHEIAS_ZONA_1", "Zona 1 — Baixo Mondego");
        zones.put("CHEIAS_ZONA_2", "Zona 2 — Ribeira de Coimbra");
        zones.put("CHEIAS_ZONA_3", "Zona 3 — Figueira da Foz");
        ZONE_LABELS = Collections.unmodifiableMap(zones);
    }

    private BunnyHopConfig() {
    }

    @NonNull
    public static Map<String, String> getZoneLabels() {
        return ZONE_LABELS;
    }

    public static boolean isFloodBeaconName(@Nullable String deviceName) {
        return deviceName != null && deviceName.startsWith(DEVICE_NAME_PREFIX);
    }

    @NonNull
    public static String zoneLabelForDeviceName(@Nullable String deviceName) {
        if (deviceName == null) {
            return "zona desconhecida";
        }
        String known = ZONE_LABELS.get(deviceName);
        if (known != null) {
            return known;
        }
        if (deviceName.startsWith(DEVICE_NAME_PREFIX)) {
            return deviceName.substring(DEVICE_NAME_PREFIX.length()).replace('_', ' ');
        }
        return deviceName;
    }

    public static int notificationIdForZone(@NonNull String deviceName) {
        return Math.abs(deviceName.hashCode());
    }
}
