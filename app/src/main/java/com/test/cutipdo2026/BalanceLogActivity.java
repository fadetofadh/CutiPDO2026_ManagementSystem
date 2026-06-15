package com.test.cutipdo2026;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BalanceLogActivity extends AppCompatActivity {

    private RecyclerView rvBalanceLog;
    private TextView tvLogTitle, tvNoLogData;
    private ProgressBar pbLogLoader;
    private SwipeRefreshLayout swipeRefreshLog;
    private ImageButton btnBackFromLog;

    private GoogleSheetsApi googleSheetsApi;
    private List<LeaveRequestData> logList = new ArrayList<>();
    private LogAdapter logAdapter;

    private String employeeName;
    private String leaveType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balance_log);

        employeeName = getIntent().getStringExtra("EMPLOYEE_NAME");
        leaveType = getIntent().getStringExtra("LEAVE_TYPE");

        rvBalanceLog = findViewById(R.id.rvBalanceLog);
        tvLogTitle = findViewById(R.id.tvLogTitle);
        tvNoLogData = findViewById(R.id.tvNoLogData);
        pbLogLoader = findViewById(R.id.pbLogLoader);
        swipeRefreshLog = findViewById(R.id.swipeRefreshLog);
        btnBackFromLog = findViewById(R.id.btnBackFromLog);

        tvLogTitle.setText(employeeName + " - " + leaveType + " Log");

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://script.google.com/macros/s/AKfycbxJTEynitpq3WVq9WC6KxbpNuBiVcrERBQSkYmKZ3HiebQ11QlcJRorJjGEYBYeSwre/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        googleSheetsApi = retrofit.create(GoogleSheetsApi.class);

        rvBalanceLog.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogAdapter(logList);
        rvBalanceLog.setAdapter(logAdapter);

        swipeRefreshLog.setOnRefreshListener(this::fetchLogs);
        btnBackFromLog.setOnClickListener(v -> finish());

        fetchLogs();
    }

    private void fetchLogs() {
        if (!swipeRefreshLog.isRefreshing()) {
            pbLogLoader.setVisibility(View.VISIBLE);
        }
        tvNoLogData.setVisibility(View.GONE);

        // 💡 Cache Buster: Ensures you see LIVE data every time you refresh
        String cb = String.valueOf(System.currentTimeMillis());

        // 🛡️ Passing "all" for filterClass to bypass server-side department filtering
        googleSheetsApi.getAllRequests("all", "all", cb).enqueue(new Callback<List<LeaveRequestData>>() {
            @Override
            public void onResponse(@NonNull Call<List<LeaveRequestData>> call, @NonNull Response<List<LeaveRequestData>> response) {
                pbLogLoader.setVisibility(View.GONE);
                swipeRefreshLog.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    logList.clear();
                    for (LeaveRequestData data : response.body()) {
                        // 🛡️ Safe Filtering: Match Name and Leave Type exactly
                        if (data.employeeName != null && data.employeeName.trim().equalsIgnoreCase(employeeName.trim()) && 
                            data.leaveType != null && data.leaveType.trim().equalsIgnoreCase(leaveType.trim())) {
                            logList.add(data);
                        }
                    }

                    if (logList.isEmpty()) {
                        tvNoLogData.setVisibility(View.VISIBLE);
                        tvNoLogData.setText(getString(R.string.log_no_history, employeeName, leaveType));
                    } else {
                        tvNoLogData.setVisibility(View.GONE);
                    }
                    logAdapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(BalanceLogActivity.this, "Server error or no data found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<LeaveRequestData>> call, @NonNull Throwable t) {
                pbLogLoader.setVisibility(View.GONE);
                swipeRefreshLog.setRefreshing(false);
                Toast.makeText(BalanceLogActivity.this, "Connection Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {
        private final List<LeaveRequestData> items;

        LogAdapter(List<LeaveRequestData> items) { this.items = items; }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_balance_log, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            LeaveRequestData item = items.get(position);
            
            holder.tvLogDate.setText(item.actionType != null ? item.actionType.toUpperCase() : "ACTIVITY");
            holder.tvLogAction.setText(item.approvalId != null && !item.approvalId.isEmpty() ? item.approvalId : "-");
            holder.tvLogLeaveDates.setText(holder.itemView.getContext().getString(R.string.log_dates_format, item.getFormattedDate()));
            holder.tvLogDays.setText(holder.itemView.getContext().getString(R.string.log_days_format, item.totalDays));
            holder.tvLogDescription.setText(item.description != null ? item.description : "-");

            String status = item.status != null ? item.status : "";
            if (status.equalsIgnoreCase("Approved")) {
                holder.tvLogAction.setBackgroundColor(Color.parseColor("#C6F6D5")); 
                holder.tvLogAction.setTextColor(Color.parseColor("#2F855A"));     
            } else if (status.equalsIgnoreCase("Pending")) {
                holder.tvLogAction.setBackgroundColor(Color.parseColor("#FEEBC8")); 
                holder.tvLogAction.setTextColor(Color.parseColor("#C05621"));     
            } else if (status.equalsIgnoreCase("System")) {
                holder.tvLogAction.setBackgroundColor(Color.parseColor("#BEE3F8")); 
                holder.tvLogAction.setTextColor(Color.parseColor("#2B6CB0"));     
            } else if (status.equalsIgnoreCase("Cancelled") || status.equalsIgnoreCase("Declined")) {
                holder.tvLogAction.setBackgroundColor(Color.parseColor("#FED7D7")); 
                holder.tvLogAction.setTextColor(Color.parseColor("#C53030"));     
            } else {
                holder.tvLogAction.setBackgroundColor(Color.parseColor("#EDF2F7"));
                holder.tvLogAction.setTextColor(Color.parseColor("#4A5568"));
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class LogViewHolder extends RecyclerView.ViewHolder {
            TextView tvLogDate, tvLogAction, tvLogLeaveDates, tvLogDays, tvLogDescription;
            LogViewHolder(View v) {
                super(v);
                tvLogDate = v.findViewById(R.id.tvLogDate);
                tvLogAction = v.findViewById(R.id.tvLogAction);
                tvLogLeaveDates = v.findViewById(R.id.tvLogLeaveDates);
                tvLogDays = v.findViewById(R.id.tvLogDays);
                tvLogDescription = v.findViewById(R.id.tvLogDescription);
            }
        }
    }
}
