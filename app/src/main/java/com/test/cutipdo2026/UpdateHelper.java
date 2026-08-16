package com.test.cutipdo2026;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.appcompat.app.AlertDialog;

public class UpdateHelper {

    private static final String PREFS_NAME = "UpdatePrefs";
    private static final String KEY_LAST_REMINDER_MS = "last_reminder_ms";
    private static final long COOLDOWN_12H = 12 * 60 * 60 * 1000;

    public interface UpdateCheckCallback {
        void onNoUpdate();
    }

    public static void handleUpdateCheck(Activity activity, UpdateResponse data, boolean isSplash, UpdateCheckCallback callback) {
        int serverVersion = data.getLatestVersion();
        int localVersion = BuildConfig.VERSION_CODE;

        if (serverVersion > localVersion) {
            boolean isForce = data.isForceUpdate();
            String apkUrl = data.getDownloadUrl();
            String versionName = data.getVersionName();
            String changelog = data.getChangelog();

            if (isForce) {
                showUpdateDialog(activity, apkUrl, true, versionName, changelog, serverVersion, callback);
            } else {
                // 💡 Control Logic: Check if we should remind now
                if (shouldShowReminder(activity, serverVersion, data.getPushId())) {
                    showUpdateDialog(activity, apkUrl, false, versionName, changelog, serverVersion, callback);
                } else if (isSplash) {
                    callback.onNoUpdate();
                }
            }
        } else if (isSplash) {
            callback.onNoUpdate();
        }
    }

    private static boolean shouldShowReminder(Context context, int serverVersionCode, String currentPushId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // 💡 SMART TRIGGER: If you change the PushId in the sheet, it resets the timer for everyone
        String lastSeenPushId = prefs.getString("last_update_push_id", "");
        if (!currentPushId.equals(lastSeenPushId)) {
            return true; 
        }

        long lastReminder = prefs.getLong(KEY_LAST_REMINDER_MS + "_" + serverVersionCode, 0);
        long now = System.currentTimeMillis();
        return (now - lastReminder) > COOLDOWN_12H;
    }

    private static void showUpdateDialog(Activity activity, String apkUrl, boolean isForce, String versionName, String changelog, int serverVersion, UpdateCheckCallback callback) {
        String finalVersionName = (versionName == null || versionName.isEmpty()) ? activity.getString(R.string.update_new_build) : versionName;
        String finalChangelog = (changelog == null || changelog.isEmpty()) ? activity.getString(R.string.update_changelog_default) : changelog.replace("\\n", "\n");

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_available_title, finalVersionName))
                .setMessage(activity.getString(R.string.update_message_format, finalChangelog))
                .setCancelable(false);

        builder.setPositiveButton(R.string.btn_update_now, (dialog, which) -> {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)));
                if (isForce) activity.finish();
            } catch (Exception e) {
                android.widget.Toast.makeText(activity, R.string.msg_no_app_found, android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        if (!isForce) {
            builder.setNegativeButton(R.string.btn_later, (dialog, which) -> {
                saveReminderCooldown(activity, serverVersion);
                dialog.dismiss();
                if (callback != null) callback.onNoUpdate();
            });
        }

        if (!activity.isFinishing()) {
            builder.show();
        }
    }

    private static void saveReminderCooldown(Context context, int serverVersionCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_REMINDER_MS + "_" + serverVersionCode, System.currentTimeMillis()).apply();
    }
}
