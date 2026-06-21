package com.test.cutipdo2026;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CheckBalanceActivity extends AppCompatActivity {

    private Spinner spBalanceEmployeeName;
    private LinearLayout layoutBalanceCards;
    private TextView tvCutiBalanceAmount, tvPdoBalanceAmount;
    private SwipeRefreshLayout swipeRefreshBalance;
    private MaterialCardView cardCuti, cardPdo;

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
        Button btnBackToLogin = findViewById(R.id.btnBackToLogin);
        swipeRefreshBalance = findViewById(R.id.swipeRefreshBalance);
        cardCuti = findViewById(R.id.cardCuti);
        cardPdo = findViewById(R.id.cardPdo);

        swipeRefreshBalance.setOnRefreshListener(this::fetchLiveBalances);

        employeeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, employeeList);
        employeeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBalanceEmployeeName.setAdapter(employeeAdapter);

        googleSheetsApi = RetrofitClient.getApi(this);

        // 💡 NEW: Show loader immediately when activity layout inflation finishes
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.msg_downloading_balances));
        progressDialog.setCancelable(false);
        progressDialog.show();

        fetchLiveBalances();

        spBalanceEmployeeName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedName = employeeList.get(position);

                if (selectedName.equals(getString(R.string.prompt_select_employee_name))) {
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

        cardCuti.setOnClickListener(v -> openLog("CUTI"));
        cardPdo.setOnClickListener(v -> openLog("PDO"));
    }

    private void openLog(String type) {
        String selectedName = spBalanceEmployeeName.getSelectedItem().toString();
        if (selectedName.equals(getString(R.string.prompt_select_employee_name))) {
            Toast.makeText(this, getString(R.string.toast_select_employee_first), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, BalanceLogActivity.class);
        intent.putExtra("EMPLOYEE_NAME", selectedName);
        intent.putExtra("LEAVE_TYPE", type);
        startActivity(intent);
    }

    private void fetchLiveBalances() {
        if (!swipeRefreshBalance.isRefreshing()) {
            progressDialog.show();
        }

        employeeList.clear();
        employeeList.add(getString(R.string.prompt_select_employee_name));
        employeeAdapter.notifyDataSetChanged();

        googleSheetsApi.getBalances("balances", null).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<EmployeeBalance>> call, @NonNull Response<List<EmployeeBalance>> response) {
                // Dismiss loading overlay dialogue smoothly
                if (progressDialog.isShowing()) progressDialog.dismiss();
                swipeRefreshBalance.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    for (EmployeeBalance b : response.body()) {
                        balanceMap.put(b.name, b);
                        employeeList.add(b.name);
                    }
                    employeeAdapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(CheckBalanceActivity.this, getString(R.string.toast_balance_sync_failed), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<EmployeeBalance>> call, @NonNull Throwable t) {
                if (progressDialog.isShowing()) progressDialog.dismiss();
                swipeRefreshBalance.setRefreshing(false);
                Toast.makeText(CheckBalanceActivity.this, getString(R.string.toast_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }
}