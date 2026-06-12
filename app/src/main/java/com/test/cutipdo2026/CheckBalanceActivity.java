package com.test.cutipdo2026;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CheckBalanceActivity extends AppCompatActivity {

    private Spinner spBalanceEmployeeName;
    private LinearLayout layoutBalanceCards;
    private TextView tvCutiBalanceAmount, tvPdoBalanceAmount;
    private Button btnBackToLogin;

    private GoogleSheetsApi googleSheetsApi;
    private List<String> employeeList = new ArrayList<>();
    private Map<String, EmployeeBalance> balanceMap = new HashMap<>();
    private ArrayAdapter<String> employeeAdapter;

    // 💡 NEW: Handle full layout coverage during balance sheet parsing
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_balance);

        spBalanceEmployeeName = findViewById(R.id.spBalanceEmployeeName);
        layoutBalanceCards = findViewById(R.id.layoutBalanceCards);
        tvCutiBalanceAmount = findViewById(R.id.tvCutiBalanceAmount);
        tvPdoBalanceAmount = findViewById(R.id.tvPdoBalanceAmount);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);

        employeeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, employeeList);
        employeeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBalanceEmployeeName.setAdapter(employeeAdapter);

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

        // 💡 NEW: Show loader immediately when activity layout inflation finishes
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Downloading current live balances... Please wait.");
        progressDialog.setCancelable(false);
        progressDialog.show();

        fetchLiveBalances();

        spBalanceEmployeeName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedName = employeeList.get(position);

                if (selectedName.equals("-- Select Employee Name --")) {
                    layoutBalanceCards.setVisibility(View.INVISIBLE);
                } else {
                    EmployeeBalance balance = balanceMap.get(selectedName);
                    if (balance != null) {
                        tvCutiBalanceAmount.setText(String.valueOf(balance.cutiBalance));
                        tvPdoBalanceAmount.setText(String.valueOf(balance.pdoBalance));
                        layoutBalanceCards.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnBackToLogin.setOnClickListener(v -> finish());
    }

    private void fetchLiveBalances() {
        employeeList.clear();
        employeeList.add("-- Select Employee Name --");
        employeeAdapter.notifyDataSetChanged();

        googleSheetsApi.getBalances("balances", null).enqueue(new Callback<List<EmployeeBalance>>() {
            @Override
            public void onResponse(Call<List<EmployeeBalance>> call, Response<List<EmployeeBalance>> response) {
                // Dismiss loading overlay dialogue smoothly
                if (progressDialog.isShowing()) progressDialog.dismiss();

                if (response.isSuccessful() && response.body() != null) {
                    for (EmployeeBalance b : response.body()) {
                        balanceMap.put(b.name, b);
                        employeeList.add(b.name);
                    }
                    employeeAdapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(CheckBalanceActivity.this, "Server balance database sync failed.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<EmployeeBalance>> call, Throwable t) {
                if (progressDialog.isShowing()) progressDialog.dismiss();
                Toast.makeText(CheckBalanceActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}