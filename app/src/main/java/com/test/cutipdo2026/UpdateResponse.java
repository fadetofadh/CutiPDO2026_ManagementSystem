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

    public int getLatestVersion() { return latestVersion; }
    public String getDownloadUrl() { return downloadUrl; }
    public boolean isForceUpdate() { return isForceUpdate; }
    public String getVersionName() { return versionName; }
    public String getChangelog() { return changelog; }
}