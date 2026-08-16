package com.test.cutipdo2026;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.NonNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MaintenanceHelper {

    public static void checkMaintenance(Activity activity) {
        GoogleSheetsApi api = RetrofitClient.getApi(activity);
        String cacheBuster = System.currentTimeMillis() + "";

        api.checkAppUpdate("checkUpdate", cacheBuster).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<UpdateResponse> call, @NonNull Response<UpdateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UpdateResponse data = response.body();

                    // 1. Check Maintenance
                    if (data.isMaintenance()) {
                        Intent intent = new Intent(activity, MaintenanceActivity.class);
                        intent.putExtra("MAINTENANCE_TITLE", data.getMaintenanceTitle());
                        intent.putExtra("MAINTENANCE_MESSAGE", data.getMaintenanceMessage());
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        activity.startActivity(intent);
                        activity.finish();
                        return;
                    }

                    // 2. Check for Updates (on every resume)
                    UpdateHelper.handleUpdateCheck(activity, data, false, null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<UpdateResponse> call, @NonNull Throwable t) {
                // Ignore network failures for heartbeat check to avoid annoying user
            }
        });
    }
}