package com.test.cutipdo2026;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.test.cutipdo2026.BuildConfig;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Instead of waiting on a blind timer, check the spreadsheet immediately
        checkApplicationVersionSmart();
    }

    private void checkApplicationVersionSmart() {
        // 1. Configure the OkHttpClient to handle redirects safely just like your other activities
        okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        // 2. Initialize Retrofit directly here using your app's base URL
        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl("https://script.google.com/macros/s/AKfycbxJTEynitpq3WVq9WC6KxbpNuBiVcrERBQSkYmKZ3HiebQ11QlcJRorJjGEYBYeSwre/")
                .client(okHttpClient)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();

        // 3. Create the API interface instance
        GoogleSheetsApi api = retrofit.create(GoogleSheetsApi.class);

        String cacheBuster = String.valueOf(System.currentTimeMillis());

        // 4. Run the network call
        api.checkAppUpdate("checkUpdate", cacheBuster).enqueue(new Callback<UpdateResponse>() {
            @Override
            public void onResponse(Call<UpdateResponse> call, Response<UpdateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    int serverVersion = response.body().getLatestVersion();
                    String apkUrl = response.body().getDownloadUrl();
                    boolean isForce = response.body().isForceUpdate();

                    // ✨ FIXED: Extra variables extracted here from your model response payload
                    String serverVersionName = response.body().getVersionName();
                    String serverChangelog = response.body().getChangelog();

                    int localVersion = BuildConfig.VERSION_CODE;

                    if (serverVersion > localVersion) {
                        // ✨ FIXED: Passing all 4 required components into the dialog builder method
                        showUpdateDialog(apkUrl, isForce, serverVersionName, serverChangelog);
                    } else {
                        proceedToLogin();
                    }
                } else {
                    proceedToLogin();
                }
            }

            @Override
            public void onFailure(Call<UpdateResponse> call, Throwable t) {
                proceedToLogin();
            }
        });
    }

    private void showUpdateDialog(final String apkUrl, final boolean isForce, String versionName, String changelog) {
        // Handle fallback text safely in case the columns in the spreadsheet happen to be empty
        String finalVersionName = (versionName == null || versionName.isEmpty()) ? getString(R.string.update_new_build) : versionName;
        String finalChangelog = (changelog == null || changelog.isEmpty()) ? getString(R.string.update_changelog_default) : changelog;

        // Replace literal string tags with clean escape characters for multi-line support
        String formattedChangelog = finalChangelog.replace("\\n", "\n");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_available_title, finalVersionName))
                .setMessage(getString(R.string.update_message_format, formattedChangelog))
                .setCancelable(false); // FIXED: Force the user to choose (Update or Later)

        builder.setPositiveButton(R.string.btn_update_now, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                startActivity(intent);
                if (isForce) finish();
            }
        });

        if (!isForce) {
            builder.setNegativeButton(R.string.btn_later, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    proceedToLogin();
                }
            });
        }

        builder.show();
    }

    private void proceedToLogin() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); // Destroys SplashActivity so pressing 'Back' won't open it again
    }
}