package com.test.cutipdo2026;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

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

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inputCode = etPasscode.getText().toString().trim();

                // Verification Path A: Head of Division Portal (KADIV)
                if (rbKadiv.isChecked()) {
                    if (inputCode.equals("kadiv")) {
                        fetchDataAndNavigateToKadiv();
                    } else {
                        Toast.makeText(LoginActivity.this, "Incorrect Head of Division Passcode!", Toast.LENGTH_SHORT).show();
                    }
                }

                // Verification Path B: Supervisor Dashboard (SPV)
                else if (rbSpv.isChecked()) {
                    if (inputCode.equals("spv")) {
                        etPasscode.setText("");
                        Intent intent = new Intent(LoginActivity.this, SupervisorActivity.class);
                        startActivity(intent);
                    } else {
                        Toast.makeText(LoginActivity.this, "Incorrect Supervisor Passcode!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        Button btnCheckBalancePortal = findViewById(R.id.btnCheckBalancePortal);
        btnCheckBalancePortal.setOnClickListener(v -> {
            Intent intent = new Intent(this, CheckBalanceActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onBackPressed() {
        // Instead of going back to Splash (which re-launches Login), move app to background
        moveTaskToBack(true);
    }

    private void fetchDataAndNavigateToKadiv() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Synchronizing roster & balances... Please wait.");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Step 1: Download balances data
        googleSheetsApi.getBalances("balances").enqueue(new Callback<List<EmployeeBalance>>() {
            @Override
            public void onResponse(Call<List<EmployeeBalance>> call, Response<List<EmployeeBalance>> response) {
                if (response.isSuccessful() && response.body() != null) {

                    // Explicitly construct an concrete ArrayList container to avoid internal class parcelable crashes
                    ArrayList<EmployeeBalance> balanceList = new ArrayList<>();
                    balanceList.addAll(response.body());

                    // Step 2: Download Employee Names roster sequentially
                    googleSheetsApi.getEmployees("employees").enqueue(new Callback<List<String>>() {
                        @Override
                        public void onResponse(Call<List<String>> call2, Response<List<String>> response2) {
                            if (progressDialog.isShowing()) progressDialog.dismiss();

                            if (response2.isSuccessful() && response2.body() != null) {
                                ArrayList<String> nameList = new ArrayList<>();
                                nameList.addAll(response2.body());

                                etPasscode.setText("");

                                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                intent.putExtra("PRE_FETCHED_BALANCES", balanceList);
                                intent.putExtra("PRE_FETCHED_NAMES", nameList);
                                startActivity(intent);
                            } else {
                                Toast.makeText(LoginActivity.this, "Failed loading staff roster names.", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<List<String>> call2, Throwable t2) {
                            if (progressDialog.isShowing()) progressDialog.dismiss();
                            Toast.makeText(LoginActivity.this, "Network error path 2: " + t2.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });

                } else {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(LoginActivity.this, "Server balance synchronization failed.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<EmployeeBalance>> call, Throwable t) {
                if (progressDialog.isShowing()) progressDialog.dismiss();
                Toast.makeText(LoginActivity.this, "Network error path 1: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}