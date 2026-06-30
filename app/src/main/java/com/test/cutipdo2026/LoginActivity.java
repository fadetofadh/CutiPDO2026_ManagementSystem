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

public class LoginActivity extends AppCompatActivity {

    private RadioButton rbSpv;
    private EditText etPasscode;
    private GoogleSheetsApi googleSheetsApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force the application to ALWAYS render in Light Mode layout state
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        );

        setContentView(R.layout.activity_login);

        rbSpv = findViewById(R.id.rbSpv);
        etPasscode = findViewById(R.id.etPasscode);
        Button btnLogin = findViewById(R.id.btnLogin);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Instead of going back to Splash (which re-launches Login), move app to background
                moveTaskToBack(true);
            }
        });

        googleSheetsApi = RetrofitClient.getApi(this);

        btnLogin.setOnClickListener(v -> {
            String inputCode = etPasscode.getText().toString().trim();
            if (inputCode.isEmpty()) {
                Toast.makeText(this, "Masukkan Kata Sandi!", Toast.LENGTH_SHORT).show();
                return;
            }

            String roleType = rbSpv.isChecked() ? "SPV" : "KADIV";

            ProgressDialog loginProgress = new ProgressDialog(this);
            loginProgress.setMessage("Memverifikasi Kredensial...");
            loginProgress.setCancelable(false);
            loginProgress.show();

            googleSheetsApi.verifyLogin("login", inputCode, roleType).enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(@NonNull Call<LoginResponse> call, @NonNull Response<LoginResponse> response) {
                    if (loginProgress.isShowing()) loginProgress.dismiss();

                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse res = response.body();
                        if ("success".equals(res.getStatus())) {
                            if ("Super_Admin".equals(res.getRoleName())) {
                                etPasscode.setText("");
                                if (Objects.equals(roleType, "SPV")) {
                                    startActivity(new Intent(LoginActivity.this, SuperAdminSPVActivity.class));
                                } else {
                                    fetchDataAndNavigateToSuperAdmin();
                                }
                            } else {
                                fetchDataAndNavigateToKadiv(res.getFilterClass(), res.isSpv());
                            }
                        } else {
                            String serverMsg = res.getMessage() != null ? res.getMessage() : "Kesalahan tidak diketahui";
                            Toast.makeText(LoginActivity.this, serverMsg, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        String errMsg = "HTTP Error " + response.code();
                        ErrorReporter.report(LoginActivity.this, "Passcode: " + inputCode, "LoginActivity", errMsg, "Server Error");
                        Toast.makeText(LoginActivity.this, "Kesalahan Server: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<LoginResponse> call, @NonNull Throwable t) {
                    if (loginProgress.isShowing()) loginProgress.dismiss();
                    handleNetworkError(t);
                }
            });
        });

        Button btnCheckBalancePortal = findViewById(R.id.btnCheckBalancePortal);
        btnCheckBalancePortal.setOnClickListener(v -> {
            Intent intent = new Intent(this, CheckBalanceActivity.class);
            startActivity(intent);
        });

        Button btnViewCalendar = findViewById(R.id.btnViewCalendar);
        btnViewCalendar.setOnClickListener(v -> {
            Intent intent = new Intent(this, CalendarViewActivity.class);
            startActivity(intent);
        });

        TextView tvCreditPlaceholder = findViewById(R.id.tvCreditPlaceholder);
        tvCreditPlaceholder.setOnClickListener(v -> showCreditsDialog());
    }

    private void showCreditsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_credits, null);
        TextView tvVersion = dialogView.findViewById(R.id.tvVersion);
        tvVersion.setText(getString(R.string.log_days_format, 0).replace("Hari: 0", "Versi " + BuildConfig.VERSION_NAME));
        // Using log_days_format as a dummy to get "Versi " prefix if I don't want to add a new string resource right now
        // Actually, it's better to just set it directly or use a better string.
        tvVersion.setText("Versi " + BuildConfig.VERSION_NAME);

        final int[] tapCount = {0};
        tvVersion.setOnClickListener(view -> {
            tapCount[0]++;
            if (tapCount[0] >= 5) {
                tapCount[0] = 0;
                startActivity(new Intent(this, DeveloperOptionsActivity.class));
            }
        });

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.dialog_credits_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_close, null)
                .show();
    }

    private void fetchDataAndNavigateToSuperAdmin() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.msg_sync_staff_directory));
        progressDialog.setCancelable(false);
        progressDialog.show();

        googleSheetsApi.getBalances("balances", null).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<EmployeeBalance>> call, @NonNull Response<List<EmployeeBalance>> response) {
                if (progressDialog.isShowing()) progressDialog.dismiss();

                if (response.isSuccessful() && response.body() != null) {
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

    private void fetchDataAndNavigateToKadiv(final String filterClass, final boolean isSpv) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.msg_sync_roster_balances));
        progressDialog.setCancelable(false);
        progressDialog.show();

        googleSheetsApi.getBalances("balances", filterClass).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<EmployeeBalance>> call, @NonNull Response<List<EmployeeBalance>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    final ArrayList<EmployeeBalance> balanceList = new ArrayList<>(response.body());

                    googleSheetsApi.getEmployees("employees", filterClass).enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<List<String>> call2, @NonNull Response<List<String>> response2) {
                            if (response2.isSuccessful() && response2.body() != null) {
                                final ArrayList<String> nameList = new ArrayList<>(response2.body());
                                etPasscode.setText("");

                                final ArrayList<EmployeeBalance> filteredBalances;
                                final ArrayList<String> filteredNames;

                                if (isSpv && !Objects.equals(filterClass, "Testing")) {
                                    filteredBalances = new ArrayList<>();
                                    filteredNames = new ArrayList<>();
                                    for (EmployeeBalance b : balanceList) {
                                        if (b.empClass != null && b.empClass.equalsIgnoreCase("SPV")) {
                                            filteredBalances.add(b);
                                            filteredNames.add(b.name);
                                        }
                                    }
                                } else {
                                    filteredBalances = balanceList;
                                    filteredNames = nameList;
                                }

                                // 💡 Fetch all approved requests for this division to prevent overlaps
                                googleSheetsApi.getAllRequests("all", filterClass, String.valueOf(System.currentTimeMillis())).enqueue(new Callback<>() {
                                    @Override
                                    public void onResponse(@NonNull Call<List<LeaveRequestData>> call3, @NonNull Response<List<LeaveRequestData>> response3) {
                                        if (progressDialog.isShowing()) progressDialog.dismiss();
                                        
                                        ArrayList<LeaveRequestData> approvedList = (response3.isSuccessful() && response3.body() != null) 
                                                ? new ArrayList<>(response3.body()) : new ArrayList<>();

                                        Class<?> targetActivity = isSpv ? SpvPortalActivity.class : KadivPortalActivity.class;
                                        Intent intent = new Intent(LoginActivity.this, targetActivity);
                                        intent.putExtra("PRE_FETCHED_BALANCES", filteredBalances);
                                        intent.putExtra("PRE_FETCHED_NAMES", filteredNames);
                                        intent.putExtra("PRE_FETCHED_APPROVED", approvedList);
                                        
                                        String finalFilter = (isSpv && !Objects.equals(filterClass, "Testing")) ? "all" : filterClass;
                                        intent.putExtra("FILTER_CLASS", finalFilter);
                                        startActivity(intent);
                                    }

                                    @Override
                                    public void onFailure(@NonNull Call<List<LeaveRequestData>> call3, @NonNull Throwable t3) {
                                        if (progressDialog.isShowing()) progressDialog.dismiss();
                                        Intent intent = new Intent(LoginActivity.this, isSpv ? SpvPortalActivity.class : KadivPortalActivity.class);
                                        intent.putExtra("PRE_FETCHED_BALANCES", filteredBalances);
                                        intent.putExtra("PRE_FETCHED_NAMES", filteredNames);
                                        intent.putExtra("PRE_FETCHED_APPROVED", new ArrayList<LeaveRequestData>());
                                        intent.putExtra("FILTER_CLASS", (isSpv && !Objects.equals(filterClass, "Testing")) ? "all" : filterClass);
                                        startActivity(intent);
                                    }
                                });
                            } else {
                                if (progressDialog.isShowing()) progressDialog.dismiss();
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
                handleNetworkError(t);
            }
        });
    }

    private void handleNetworkError(Throwable t) {
        String errorType = (t instanceof java.net.SocketTimeoutException) ? "Timeout" : "Network Error";
        ErrorReporter.report(this, "Attempt: " + etPasscode.getText().toString(), "LoginActivity", t.getMessage(), errorType);

        if (t instanceof java.net.SocketTimeoutException) {
            Toast.makeText(this, R.string.toast_timeout_error, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.toast_network_error, t.getMessage()), Toast.LENGTH_LONG).show();
        }
    }
}
