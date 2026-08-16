package com.test.cutipdo2026;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;
import com.test.cutipdo2026.BuildConfig;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Subscribe to FCM topic for announcements
        FirebaseMessaging.getInstance().subscribeToTopic("announcements")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM", "Subscribed to announcements topic");
                    }
                });

        checkApplicationVersionSmart();
    }

    private void checkApplicationVersionSmart() {
        GoogleSheetsApi api = RetrofitClient.getApi(this);
        String cacheBuster = System.currentTimeMillis() + "";

        api.checkAppUpdate("checkUpdate", cacheBuster).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<UpdateResponse> call, @NonNull Response<UpdateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UpdateResponse data = response.body();

                    // 1. Check Maintenance Mode FIRST
                    if (data.isMaintenance()) {
                        Intent maintenanceIntent = new Intent(SplashActivity.this, MaintenanceActivity.class);
                        maintenanceIntent.putExtra("MAINTENANCE_TITLE", data.getMaintenanceTitle());
                        maintenanceIntent.putExtra("MAINTENANCE_MESSAGE", data.getMaintenanceMessage());
                        startActivity(maintenanceIntent);
                        finish();
                        return;
                    }

                    // 2. Check for Updates
                    UpdateHelper.handleUpdateCheck(SplashActivity.this, data, true, () -> proceedToLogin());
                } else {
                    proceedToLogin();
                }
            }

            @Override
            public void onFailure(@NonNull Call<UpdateResponse> call, @NonNull Throwable t) {
                // Report the error to admin before proceeding
                String errorType = (t instanceof java.net.SocketTimeoutException) ? "Timeout" : "Network Error";
                ErrorReporter.report(SplashActivity.this, "System", "SplashActivity", "Update Check: " + t.getMessage(), errorType);

                if (t instanceof java.net.SocketTimeoutException) {
                    Toast.makeText(SplashActivity.this, R.string.toast_timeout_error, Toast.LENGTH_SHORT).show();
                }
                proceedToLogin();
            }
        });
    }

    private void proceedToLogin() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}