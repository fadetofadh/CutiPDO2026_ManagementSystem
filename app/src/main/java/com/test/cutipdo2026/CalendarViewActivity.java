package com.test.cutipdo2026;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CalendarViewActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvNoSchedule, tvFilterStatus;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Button btnSelectDate;
    private ImageButton btnClearFilter;
    private ScheduleAdapter adapter;
    private GoogleSheetsApi googleSheetsApi;

    private List<LeaveRequestData> fullList = new ArrayList<>();
    private String selectedFilterDate = null; // Format: dd/MM/yyyy

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar_view);

        recyclerView = findViewById(R.id.rvBookedSchedule);
        progressBar = findViewById(R.id.pbCalendarLoading);
        tvNoSchedule = findViewById(R.id.tvNoSchedule);
        tvFilterStatus = findViewById(R.id.tvFilterStatus);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshCalendar);
        btnSelectDate = findViewById(R.id.btnSelectFilterDate);
        btnClearFilter = findViewById(R.id.btnClearFilter);
        Button btnBack = findViewById(R.id.btnBackFromCalendar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScheduleAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://script.google.com/macros/s/AKfycbxJTEynitpq3WVq9WC6KxbpNuBiVcrERBQSkYmKZ3HiebQ11QlcJRorJjGEYBYeSwre/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        googleSheetsApi = retrofit.create(GoogleSheetsApi.class);

        fetchSchedules();

        swipeRefreshLayout.setOnRefreshListener(this::fetchSchedules);
        
        btnSelectDate.setOnClickListener(v -> showDatePicker());
        btnClearFilter.setOnClickListener(v -> clearFilter());
        btnBack.setOnClickListener(v -> finish());
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Pilih Tanggal")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(selection);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            selectedFilterDate = sdf.format(calendar.getTime());
            applyFilter();
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void clearFilter() {
        selectedFilterDate = null;
        btnSelectDate.setText(R.string.btn_check_date);
        btnClearFilter.setVisibility(View.GONE);
        tvFilterStatus.setText(R.string.filter_all);
        applyFilter();
    }

    private void fetchSchedules() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoSchedule.setVisibility(View.GONE);

        googleSheetsApi.getAllRequests("approved", null, String.valueOf(System.currentTimeMillis())).enqueue(new Callback<List<LeaveRequestData>>() {
            @Override
            public void onResponse(@NonNull Call<List<LeaveRequestData>> call, @NonNull Response<List<LeaveRequestData>> response) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    fullList = response.body();
                    
                    // 📉 SORTING: Use shared utility
                    ListSorter.sortNewestFirst(fullList);

                    applyFilter();
                } else {
                    Toast.makeText(CalendarViewActivity.this, "Gagal memuat jadwal", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<LeaveRequestData>> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(CalendarViewActivity.this, "Kesalahan Jaringan: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilter() {
        tvNoSchedule.setTextColor(0xFF718096); // Default gray
        tvNoSchedule.setText(R.string.no_approved_schedules);

        if (selectedFilterDate == null) {
            adapter.updateList(fullList);
            tvNoSchedule.setVisibility(fullList.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }

        btnSelectDate.setText(selectedFilterDate);
        btnClearFilter.setVisibility(View.VISIBLE);
        tvFilterStatus.setText(getString(R.string.filter_active, selectedFilterDate));

        List<LeaveRequestData> filtered = new ArrayList<>();
        for (LeaveRequestData req : fullList) {
            if (isDateInRange(selectedFilterDate, req.getFormattedDate())) {
                filtered.add(req);
            }
        }

        adapter.updateList(filtered);
        
        if (filtered.isEmpty()) {
            tvNoSchedule.setVisibility(View.VISIBLE);
            tvNoSchedule.setText(R.string.msg_date_available);
            tvNoSchedule.setTextColor(0xFF38A169); // Green
        } else {
            tvNoSchedule.setVisibility(View.GONE);
        }
    }

    private boolean isDateInRange(String filterDateStr, String rangeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date filterDate = sdf.parse(filterDateStr);
            
            if (rangeStr.contains(" to ")) {
                String[] parts = rangeStr.split(" to ");
                Date start = sdf.parse(parts[0]);
                Date end = sdf.parse(parts[1]);
                return filterDate != null && !filterDate.before(start) && !filterDate.after(end);
            } else {
                Date target = sdf.parse(rangeStr);
                return filterDate != null && filterDate.equals(target);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {
        private List<LeaveRequestData> items;

        ScheduleAdapter(List<LeaveRequestData> items) { this.items = items; }

        void updateList(List<LeaveRequestData> newList) {
            this.items = newList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_booked_schedule, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LeaveRequestData req = items.get(position);
            holder.tvEmpName.setText(req.employeeName);
            holder.tvLeaveType.setText(req.leaveType);
            holder.tvDates.setText(String.format("%s (%d Hari)", req.getFormattedDate(), req.totalDays));
            holder.tvDescription.setText(req.description);

            if ("CUTI".equalsIgnoreCase(req.leaveType)) {
                holder.tvLeaveType.setBackgroundColor(0xFFEBF8FF);
                holder.tvLeaveType.setTextColor(0xFF2B6CB0);
            } else {
                holder.tvLeaveType.setBackgroundColor(0xFFF0FDF4);
                holder.tvLeaveType.setTextColor(0xFF166534);
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvEmpName, tvLeaveType, tvDates, tvDescription;
            ViewHolder(View v) {
                super(v);
                tvEmpName = v.findViewById(R.id.tvEmpName);
                tvLeaveType = v.findViewById(R.id.tvLeaveType);
                tvDates = v.findViewById(R.id.tvDates);
                tvDescription = v.findViewById(R.id.tvDescription);
            }
        }
    }
}