package com.test.cutipdo2026;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SuperAdminActivity extends AppCompatActivity {

    private RecyclerView rvEmployees;
    private TextView tvDaysValue;
    private Button btnDecrease, btnIncrease, btnSubmitPdo;
    private EditText etDescription;
    private EmployeeMarkAdapter adapter;
    private List<EmployeeBalance> fullEmployeeList = new ArrayList<>();
    private int pdoToAdd = 1;
    private GoogleSheetsApi googleSheetsApi;

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_super_admin);

        rvEmployees = findViewById(R.id.rvEmployees);
        tvDaysValue = findViewById(R.id.tvDaysValue);
        btnDecrease = findViewById(R.id.btnDecrease);
        btnIncrease = findViewById(R.id.btnIncrease);
        btnSubmitPdo = findViewById(R.id.btnSubmitPdo);
        etDescription = findViewById(R.id.etDescription);

        googleSheetsApi = RetrofitClient.getApi(this);

        // Extract pre-fetched data passed from LoginActivity
        ArrayList<EmployeeBalance> receivedList = (ArrayList<EmployeeBalance>) getIntent().getSerializableExtra("FULL_EMPLOYEE_LIST");
        
        fullEmployeeList.clear();
        if (receivedList != null) {
            for (EmployeeBalance eb : receivedList) {
                if (eb.empClass != null && !eb.empClass.trim().isEmpty()) {
                    fullEmployeeList.add(eb);
                }
            }
        }

        rvEmployees.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EmployeeMarkAdapter(fullEmployeeList);
        rvEmployees.setAdapter(adapter);

        updateDaysDisplay();

        btnDecrease.setOnClickListener(v -> {
            if (pdoToAdd > 1) {
                pdoToAdd--;
                updateDaysDisplay();
            }
        });

        btnIncrease.setOnClickListener(v -> {
            pdoToAdd++;
            updateDaysDisplay();
        });

        btnSubmitPdo.setOnClickListener(v -> {
            Set<String> selectedNames = adapter.getSelectedEmployees();
            String reason = etDescription.getText().toString().trim();

            if (selectedNames.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_select_at_least_one_employee), Toast.LENGTH_SHORT).show();
                return;
            }

            if (reason.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_enter_description), Toast.LENGTH_SHORT).show();
                return;
            }

            submitBatchPdo(new ArrayList<>(selectedNames), reason);
        });
    }

    private void submitBatchPdo(List<String> names, String reason) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.msg_adding_pdo));
        progressDialog.setCancelable(false);
        progressDialog.show();

        final int[] count = {0};
        for (String name : names) {
            LeaveRequest request = new LeaveRequest("add_pdo", name, pdoToAdd, "PDO", reason);
            googleSheetsApi.sendRequest(request).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    count[0]++;
                    if (count[0] == names.size()) {
                        progressDialog.dismiss();
                        Toast.makeText(SuperAdminActivity.this, getString(R.string.toast_pdo_added_success), Toast.LENGTH_LONG).show();
                        finish();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    count[0]++;
                    if (count[0] == names.size()) {
                        progressDialog.dismiss();
                        Toast.makeText(SuperAdminActivity.this, getString(R.string.toast_batch_finished_with_errors), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            });
        }
    }

    private void updateDaysDisplay() {
        String dayText = pdoToAdd == 1 ? getString(R.string.days_count_format, pdoToAdd) : getString(R.string.days_count_format_plural, pdoToAdd);
        tvDaysValue.setText(dayText);
    }
}
