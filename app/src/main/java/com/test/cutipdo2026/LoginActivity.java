package com.test.cutipdo2026;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.test.cutipdo2026.BuildConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginActivity extends AppCompatActivity {

    private RadioButton rbKadiv, rbSpv;
    private EditText etPasscode;
    private Button btnLogin;
    private GoogleSheetsApi googleSheetsApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force the application to ALWAYS render in Light Mode layout state
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        );

        setContentView(R.layout.activity_login);

        rbKadiv = findViewById(R.id.rbKadiv);
        rbSpv = findViewById(R.id.rbSpv);
        etPasscode = findViewById(R.id.etPasscode);
        btnLogin = findViewById(R.id.btnLogin);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Instead of going back to Splash (which re-launches Login), move app to background
                moveTaskToBack(true);
            }
        });

        okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://script.google.com/macros/s/AKfycbxJTEynitpq3WVq9WC6KxbpNuBiVcrERBQSkYmKZ3HiebQ11QlcJRorJjGEYBYeSwre/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        googleSheetsApi = retrofit.create(GoogleSheetsApi.class);

        btnLogin.setOnClickListener(v -> {
            String inputCode = etPasscode.getText().toString().trim();

            // Verification Path A: Head of Division Portal (KADIV)
            if (rbKadiv.isChecked()) {
                if (Objects.equals(inputCode, "teknis")) {
                    fetchDataAndNavigateToKadiv("Teknis", false);
                } else if (Objects.equals(inputCode, "guide")) {
                    fetchDataAndNavigateToKadiv("Guide", false);
                } else if (Objects.equals(inputCode, "haka")) {
                    fetchDataAndNavigateToKadiv("HK", false);
                } else if (Objects.equals(inputCode, "superadmin")) {
                    fetchDataAndNavigateToSuperAdmin();
                } else {
                    Toast.makeText(LoginActivity.this, getString(R.string.toast_incorrect_kadiv_passcode), Toast.LENGTH_SHORT).show();
                }
            }

            // Verification Path B: Supervisor Dashboard (SPV)
            else if (rbSpv.isChecked()) {
                if (Objects.equals(inputCode, "spv")) {
                    fetchDataAndNavigateToKadiv("all", true);
                } else if (Objects.equals(inputCode, "superadmin")) {
                    etPasscode.setText("");
                    Intent intent = new Intent(LoginActivity.this, SuperAdminSPVActivity.class);
                    startActivity(intent);
                } else {
                    Toast.makeText(LoginActivity.this, getString(R.string.toast_incorrect_spv_passcode), Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button btnCheckBalancePortal = findViewById(R.id.btnCheckBalancePortal);
        btnCheckBalancePortal.setOnClickListener(v -> {
            Intent intent = new Intent(this, CheckBalanceActivity.class);
            startActivity(intent);
        });

        TextView tvCreditPlaceholder = findViewById(R.id.tvCreditPlaceholder);
        tvCreditPlaceholder.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_credits_title)
                    .setMessage(getString(R.string.dialog_credits_message, BuildConfig.VERSION_NAME))
                    .setPositiveButton(R.string.btn_close, null)
                    .show();
        });
    }

    private void fetchDataAndNavigateToSuperAdmin() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.msg_sync_staff_directory));
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Fetch all balances (filterClass = null)
        googleSheetsApi.getBalances("balances", null).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<EmployeeBalance>> call, @NonNull Response<List<EmployeeBalance>> response) {
                if (progressDialog.isShowing()) progressDialog.dismiss();

                if (response.isSuccessful() && response.body() != null) {
                    @SuppressWarnings("unchecked")
                    ArrayList<EmployeeBalance> fullList = new ArrayList<>(response.body());
                    etPasscode.setText("");
                    Intent intent = new Intent(LoginActivity.this, SuperAdminActivity.class);
                    intent.putExtra("FULL_EMPLOYEE_LIST", fullList);
                    startActivity(intent);
                } else {
                    Toast.makeText(LoginActivity.this, getString(R.string.toast_server_sync_failed), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<EmployeeBalance>> call, @NonNull Throwable t) {
                if (progressDialog.isShowing()) progressDialog.dismiss();
                Toast.makeText(LoginActivity.this, getString(R.string.toast_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchDataAndNavigateToKadiv(String filterClass, final boolean isSpv) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.msg_sync_roster_balances));
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Step 1: Download balances data
        googleSheetsApi.getBalances("balances", filterClass).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<EmployeeBalance>> call, @NonNull Response<List<EmployeeBalance>> response) {
                if (response.isSuccessful() && response.body() != null) {

                    // Explicitly construct an concrete ArrayList container to avoid internal class parcelable crashes
                    ArrayList<EmployeeBalance> balanceList = new ArrayList<>(response.body());

                    // Step 2: Download Employee Names roster sequentially
                    googleSheetsApi.getEmployees("employees", filterClass).enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<List<String>> call2, @NonNull Response<List<String>> response2) {
                            if (progressDialog.isShowing()) progressDialog.dismiss();

                            if (response2.isSuccessful() && response2.body() != null) {
                                ArrayList<String> nameList = new ArrayList<>(response2.body());

                                etPasscode.setText("");

                                ArrayList<EmployeeBalance> filteredBalances = balanceList;
                                ArrayList<String> filteredNames = nameList;

                                if (isSpv) {
                                    filteredBalances = new ArrayList<>();
                                    filteredNames = new ArrayList<>();
                                    for (EmployeeBalance b : balanceList) {
                                        if (b.empClass != null && b.empClass.equalsIgnoreCase("SPV")) {
                                            filteredBalances.add(b);
                                            filteredNames.add(b.name);
                                        }
                                    }
                                }

                                Class<?> targetActivity = isSpv ? SpvPortalActivity.class : KadivPortalActivity.class;
                                Intent intent = new Intent(LoginActivity.this, targetActivity);
                                intent.putExtra("PRE_FETCHED_BALANCES", filteredBalances);
                                intent.putExtra("PRE_FETCHED_NAMES", filteredNames);
                                intent.putExtra("FILTER_CLASS", isSpv ? "all" : filterClass);
                                startActivity(intent);
                            } else {
                                Toast.makeText(LoginActivity.this, getString(R.string.toast_failed_loading_staff), Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<List<String>> call2, @NonNull Throwable t2) {
                            if (progressDialog.isShowing()) progressDialog.dismiss();
                            Toast.makeText(LoginActivity.this, getString(R.string.toast_network_error, t2.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });

                } else {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(LoginActivity.this, getString(R.string.toast_balance_sync_failed_generic), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<EmployeeBalance>> call, @NonNull Throwable t) {
                if (progressDialog.isShowing()) progressDialog.dismiss();
                Toast.makeText(LoginActivity.this, getString(R.string.toast_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }
}