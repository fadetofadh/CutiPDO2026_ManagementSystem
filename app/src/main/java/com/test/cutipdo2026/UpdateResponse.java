package com.test.cutipdo2026;

import com.google.gson.annotations.SerializedName;

public class UpdateResponse {
    @SerializedName("latestVersion")
    private int latestVersion;

    @SerializedName("downloadUrl")
    private String downloadUrl;

    @SerializedName("isForceUpdate")
    private boolean isForceUpdate;

    @SerializedName("versionName")
    private String versionName; // Added to catch Column D

    @SerializedName("changelog")
    private String changelog;   // Added to catch Column E

    @SerializedName("isMaintenance")
    private boolean isMaintenance;

    @SerializedName("maintenanceTitle")
    private String maintenanceTitle;

    @SerializedName("maintenanceMessage")
    private String maintenanceMessage;

    @SerializedName("pushTitle")
    private String pushTitle;

    @SerializedName("pushBody")
    private String pushBody;

    @SerializedName("pushId")
    private String pushId;

    public int getLatestVersion() { return latestVersion; }
    public String getDownloadUrl() { return downloadUrl; }
    public boolean isForceUpdate() { return isForceUpdate; }
    public String getVersionName() { return versionName; }
    public String getChangelog() { return changelog; }
    public boolean isMaintenance() { return isMaintenance; }
    public String getMaintenanceTitle() { return maintenanceTitle; }
    public String getMaintenanceMessage() { return maintenanceMessage; }
    public String getPushTitle() { return pushTitle; }
    public String getPushBody() { return pushBody; }
    public String getPushId() { return pushId; }
}