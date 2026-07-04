package com.bunnyhop.app;

public class Alert {

    private final long id;
    private final String deviceName;
    private final String message;
    private final boolean active;
    private final String channels;

    public Alert(long id, String deviceName, String message, boolean active) {
        this(id, deviceName, message, active, "BLE,NOTIFICATION");
    }

    public Alert(long id, String deviceName, String message, boolean active, String channels) {
        this.id = id;
        this.deviceName = deviceName;
        this.message = message;
        this.active = active;
        this.channels = channels == null ? "BLE,NOTIFICATION" : channels;
    }

    public long getId() {
        return id;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getMessage() {
        return message;
    }

    public boolean isActive() {
        return active;
    }

    public String getChannels() {
        return channels;
    }

    public String getZoneLabel() {
        return BunnyHopConfig.zoneLabelForDeviceName(deviceName);
    }

    @Override
    public String toString() {
        return getZoneLabel() + " — " + message + (active ? "" : " (inativo)");
    }
}
