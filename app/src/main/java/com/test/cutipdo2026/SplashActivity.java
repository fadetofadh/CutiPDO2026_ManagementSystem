package com.test.cutipdo2026;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "UpdatePrefs";
    private static final String KEY_LAST_REMINDER_MS = "last_reminder_ms";
    private static final long COOLDOWN_12H = 12 * 60 * 60 * 1000; // 12 Hours in milliseconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        checkApplicationVersionSmart();
    }

    private void checkApplicationVersionSmart() {
        GoogleSheetsApi api = RetrofitClient.getApi(this);
        String cacheBuster = System.currentTimeMillis() + "";

        api.checkAppUpdate("checkUpdate", cacheBuster).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<UpdateResponse> call, @NonNull Response<UpdateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    int serverVersion = response.body().getLatestVersion();
                    String apkUrl = response.body().getDownloadUrl();
                    boolean isForce = response.body().isForceUpdate();
                    String serverVersionName = response.body().getVersionName();
                    String serverChangelog = response.body().getChangelog();

                    int localVersion = BuildConfig.VERSION_CODE;

                    if (serverVersion > localVersion) {
                        if (isForce) {
                            showUpdateDialog(apkUrl, true, serverVersionName, serverChangelog, serverVersion);
                        } else {
                            // 💡 CHECK COOLDOWN: If not forced, check if we should remind now or skip
                            if (shouldShowReminder(serverVersion)) {
                                showUpdateDialog(apkUrl, false, serverVersionName, serverChangelog, serverVersion);
                            } else {
                                proceedToLogin();
                            }
                        }
                    } else {
                        proceedToLogin();
                    }
                } else {
                    proceedToLogin();
                }
            }

            @Override
            public void onFailure(@NonNull Call<UpdateResponse> call, @NonNull Throwable t) {
                proceedToLogin();
            }
        });
    }

    private boolean shouldShowReminder(int serverVersionCode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastReminder = prefs.getLong(KEY_LAST_REMINDER_MS + "_" + serverVersionCode, 0);
        long now = System.currentTimeMillis();

        // Show if first time or if 12 hours have passed since last 'Remind Me Later' click
        return (now - lastReminder) > COOLDOWN_12H;
    }

    private void saveReminderCooldown(int serverVersionCode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_REMINDER_MS + "_" + serverVersionCode, System.currentTimeMillis()).apply();
    }

    private void showUpdateDialog(final String apkUrl, final boolean isForce, String versionName, String changelog, final int serverVersion) {
        String finalVersionName = (versionName == null || versionName.isEmpty()) ? getString(R.string.update_new_build) : versionName;
        String finalChangelog = (changelog == null || changelog.isEmpty()) ? getString(R.string.update_changelog_default) : changelog;
        String formattedChangelog = finalChangelog.replace("\\n", "\n");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_available_title, finalVersionName))
                .setMessage(getString(R.string.update_message_format, formattedChangelog))
                .setCancelable(false);

        builder.setPositiveButton(R.string.btn_update_now, (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
            startActivity(intent);
            if (isForce) finish();
        });

        if (!isForce) {
            builder.setNegativeButton(R.string.btn_later, (dialog, which) -> {
                // 💡 SAVE COOLDOWN: Mark that user clicked 'Later', don't remind for 24 hours
                saveReminderCooldown(serverVersion);
                dialog.dismiss();
                proceedToLogin();
            });
        }

        builder.show();
    }

    private void proceedToLogin() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}