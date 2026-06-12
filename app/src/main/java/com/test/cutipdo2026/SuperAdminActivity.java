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
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

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

        setupRetrofit();

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
                Toast.makeText(this, "Please select at least one employee!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (reason.isEmpty()) {
                Toast.makeText(this, "Please enter a description/reason!", Toast.LENGTH_SHORT).show();
                return;
            }

            submitBatchPdo(new ArrayList<>(selectedNames), reason);
        });
    }

    private void setupRetrofit() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://script.google.com/macros/s/AKfycbxJTEynitpq3WVq9WC6KxbpNuBiVcrERBQSkYmKZ3HiebQ11QlcJRorJjGEYBYeSwre/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        googleSheetsApi = retrofit.create(GoogleSheetsApi.class);
    }

    private void submitBatchPdo(List<String> names, String reason) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Adding PDO to selected employees...");
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
                        Toast.makeText(SuperAdminActivity.this, "Successfully added PDO to all selected staff!", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    count[0]++;
                    if (count[0] == names.size()) {
                        progressDialog.dismiss();
                        Toast.makeText(SuperAdminActivity.this, "Batch process finished with some errors.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            });
        }
    }

    private void updateDaysDisplay() {
        String dayText = pdoToAdd + (pdoToAdd == 1 ? " day" : " days");
        tvDaysValue.setText(dayText);
    }
}
