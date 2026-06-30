package com.test.cutipdo2026;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ErrorReporter {

    public static void report(Context context, String employeeName, String activityName, String errorMessage, String errorType) {
        GoogleSheetsApi api = RetrofitClient.getApi(context);
        ErrorLog log = new ErrorLog(employeeName, activityName, errorMessage, errorType);

        api.reportError(log).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                Log.d("ErrorReporter", "Error reported to server. Code: " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("ErrorReporter", "Failed to report error: " + t.getMessage());
            }
        });
    }
}
