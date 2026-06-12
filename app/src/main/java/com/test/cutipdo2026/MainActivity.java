package com.test.cutipdo2026;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private EditText etSelectedDates, etLeaveDescription;
    private Button btnAddToBatch, btnReviewSubmit, btnCancelPortal;
    private TextView tvTotalDaysDisplay;
    private Spinner spEmployeeName, spLeaveType;

    private List<String> employeeList = new ArrayList<>();
    private Map<String, EmployeeBalance> balanceMap = new HashMap<>();
    private ArrayAdapter<String> employeeAdapter;

    private List<String> leaveTypeList = new ArrayList<>();
    private ArrayAdapter<String> leaveTypeAdapter;

    private ArrayList<QueuedRequest> batchQueue = new ArrayList<>();
    private QueueManager queueManager;
    private String selectedDateRangeString = "";
    private int calculatedDays = 0;

    private long currentStartMs = 0;
    private long currentEndMs = 0;
    private boolean isShowingAlert = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Bind UI Components
        queueManager = new QueueManager(this);
        batchQueue = queueManager.loadQueue();

        spEmployeeName = findViewById(R.id.spEmployeeName);
        spLeaveType = findViewById(R.id.spLeaveType);
        etSelectedDates = findViewById(R.id.etSelectedDates);
        etLeaveDescription = findViewById(R.id.etLeaveDescription);
        tvTotalDaysDisplay = findViewById(R.id.tvTotalDaysDisplay);
        btnAddToBatch = findViewById(R.id.btnAddToBatch);
        btnReviewSubmit = findViewById(R.id.btnReviewSubmit);
        btnCancelPortal = findViewById(R.id.btnCancelPortal);

        // 2. Configure Employee Dropdown
        employeeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, employeeList);
        employeeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEmployeeName.setAdapter(employeeAdapter);
        spEmployeeName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                evaluateWeekendRestrictions();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 3. Configure Leave Type Dropdown
        leaveTypeAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, leaveTypeList) {
            @Override
            public boolean isEnabled(int position) {
                if (position >= leaveTypeList.size()) return true;
                return !Objects.equals(leaveTypeList.get(position), getString(R.string.label_cuti_pdo));
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = (TextView) view.findViewById(android.R.id.text1);
                if (position < leaveTypeList.size() && leaveTypeList.get(position).equals(getString(R.string.label_cuti_pdo))) {
                    text.setTextColor(android.graphics.Color.GRAY);
                } else {
                    text.setTextColor(android.graphics.Color.BLACK);
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView text = (TextView) view.findViewById(android.R.id.text1);
                if (position < leaveTypeList.size() && leaveTypeList.get(position).equals(getString(R.string.label_cuti_pdo))) {
                    text.setTextColor(android.graphics.Color.GRAY);
                } else {
                    text.setTextColor(android.graphics.Color.BLACK);
                }
                return view;
            }
        };
        leaveTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLeaveType.setAdapter(leaveTypeAdapter);

        resetLeaveTypeOptions();

        // =========================================================================
        // 💡 4. EXTRACT PRE-FETCHED DATABASE DATA BUNDLES INSTANTLY
        // =========================================================================
        @SuppressWarnings("unchecked")
        ArrayList<EmployeeBalance> preFetchedBalances = (ArrayList<EmployeeBalance>) getIntent().getSerializableExtra("PRE_FETCHED_BALANCES");
        @SuppressWarnings("unchecked")
        ArrayList<String> preFetchedNames = (ArrayList<String>) getIntent().getSerializableExtra("PRE_FETCHED_NAMES");

        employeeList.clear();
        employeeList.add(getString(R.string.prompt_select_employee_name));

        if (preFetchedBalances != null) {
            for (EmployeeBalance b : preFetchedBalances) {
                balanceMap.put(b.name, b);
                Log.d("BALANCE_CHECK", "Loaded -> Emp: " + b.name + " Cuti: " + b.cutiBalance + " PDO: " + b.pdoBalance);
            }
        }

        if (preFetchedNames != null) {
            employeeList.addAll(preFetchedNames);
        }

        employeeAdapter.notifyDataSetChanged();

        // 5. Setup View Listeners
        etSelectedDates.setOnClickListener(v -> showStrictDatePicker());

        btnAddToBatch.setOnClickListener(v -> {
            String empName = spEmployeeName.getSelectedItem().toString();

            if (spLeaveType.getSelectedItem() == null || Objects.equals(spLeaveType.getSelectedItem().toString(), getString(R.string.label_cuti_pdo))) {
                Toast.makeText(MainActivity.this, getString(R.string.toast_select_leave_type), Toast.LENGTH_SHORT).show();
                return;
            }
            String leaveType = spLeaveType.getSelectedItem().toString();
            String description = etLeaveDescription.getText().toString().trim();

            if (Objects.equals(empName, getString(R.string.prompt_select_employee_name)) || selectedDateRangeString.isEmpty() || calculatedDays == 0) {
                Toast.makeText(MainActivity.this, getString(R.string.toast_select_employee_dates), Toast.LENGTH_SHORT).show();
                return;
            }

            EmployeeBalance balance = balanceMap.get(empName);
            if (balance != null) {
                if (Objects.equals(leaveType, getString(R.string.cuti)) && balance.cutiBalance < calculatedDays) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_insufficient_cuti, balance.cutiBalance), Toast.LENGTH_LONG).show();
                    return;
                }
                if (Objects.equals(leaveType, getString(R.string.pdo)) && balance.pdoBalance < calculatedDays) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_insufficient_pdo, balance.pdoBalance), Toast.LENGTH_LONG).show();
                    return;
                }

                if (Objects.equals(leaveType, getString(R.string.cuti))) balance.cutiBalance -= calculatedDays;
                else balance.pdoBalance -= calculatedDays;
            }

            QueuedRequest newRequest = new QueuedRequest(empName, selectedDateRangeString, calculatedDays, leaveType, description);
            batchQueue.add(newRequest);
            queueManager.saveQueue(batchQueue);

            Toast.makeText(MainActivity.this, getString(R.string.toast_added_to_batch, batchQueue.size()), Toast.LENGTH_SHORT).show();

            selectedDateRangeString = "";
            calculatedDays = 0;
            currentStartMs = 0;
            currentEndMs = 0;
            etSelectedDates.setText("");
            etLeaveDescription.setText("");
            tvTotalDaysDisplay.setText(R.string.duration_zero);
            resetLeaveTypeOptions();
        });

        btnReviewSubmit.setOnClickListener(v -> {
            if (batchQueue.isEmpty()) {
                Toast.makeText(MainActivity.this, getString(R.string.toast_batch_empty), Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(MainActivity.this, ReviewQueueActivity.class);
            intent.putExtra("batchDataList", batchQueue);
            startActivity(intent);
        });

        btnCancelPortal.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CancelPortalActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 🔄 Sync the local memory queue with the persistent storage
        // This ensures that if the queue was cleared in ReviewQueueActivity,
        // MainActivity will also show it as empty.
        if (queueManager != null) {
            batchQueue.clear();
            batchQueue.addAll(queueManager.loadQueue());
            Log.d("QUEUE_SYNC", "Queue reloaded onResume. Size: " + batchQueue.size());
        }
    }

    private void showStrictDatePicker() {
        long today = MaterialDatePicker.todayInUtcMilliseconds();
        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
        constraintsBuilder.setFirstDayOfWeek(Calendar.MONDAY);
        constraintsBuilder.setStart(today);
        constraintsBuilder.setOpenAt(today);

        constraintsBuilder.setValidator(new CalendarConstraints.DateValidator() {
            @Override
            public boolean isValid(long date) {
                if (date < today) return false;
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                cal.setTimeInMillis(date);
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                return dayOfWeek != Calendar.MONDAY && dayOfWeek != Calendar.TUESDAY;
            }
            @Override
            public int describeContents() { return 0; }
            @Override
            public void writeToParcel(android.os.Parcel dest, int flags) {}
        });

        MaterialDatePicker<Pair<Long, Long>> dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.label_select_leave_dates_title)
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        dateRangePicker.show(getSupportFragmentManager(), "STRICT_DATE_PICKER");

        dateRangePicker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null && selection.first != null && selection.second != null) {
                currentStartMs = selection.first;
                currentEndMs = selection.second;

                long diffMs = currentEndMs - currentStartMs;
                calculatedDays = (int) (diffMs / (1000 * 60 * 60 * 24)) + 1;

                SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

                String startDateStr = formatter.format(new Date(currentStartMs));
                String endDateStr = formatter.format(new Date(currentEndMs));

                if (Objects.equals(startDateStr, endDateStr)) {
                    selectedDateRangeString = startDateStr;
                } else {
                    selectedDateRangeString = startDateStr + " to " + endDateStr;
                }

                etSelectedDates.setText(selectedDateRangeString);
                tvTotalDaysDisplay.setText(getString(R.string.duration_placeholder, calculatedDays));

                evaluateWeekendRestrictions();
            }
        });
    }

    private void evaluateWeekendRestrictions() {
        if (currentStartMs == 0 || currentEndMs == 0 || selectedDateRangeString.isEmpty()) {
            resetLeaveTypeOptions();
            return;
        }

        boolean containsWeekend = false;
        Calendar checkCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        checkCalendar.setTimeInMillis(currentStartMs);

        while (checkCalendar.getTimeInMillis() <= currentEndMs) {
            int dayOfWeek = checkCalendar.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                containsWeekend = true;
                break;
            }
            checkCalendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (containsWeekend) {
            String selectedEmployee = spEmployeeName.getSelectedItem().toString();
            EmployeeBalance balance = balanceMap.get(selectedEmployee);

            leaveTypeList.clear();
            if (balance != null) {
                if (balance.pdoBalance >= calculatedDays) {
                    leaveTypeList.add(getString(R.string.pdo));
                } else if (balance.cutiBalance >= calculatedDays) {
                    leaveTypeList.add(getString(R.string.cuti));
                    Toast.makeText(this, getString(R.string.toast_weekend_detected_cuti), Toast.LENGTH_SHORT).show();
                } else {
                    showNoBalanceAlert(getString(R.string.label_insufficient_balance),
                            getString(R.string.alert_insufficient_balance_weekend_msg,
                                    calculatedDays, balance.cutiBalance, balance.pdoBalance));
                    return;
                }
            } else {
                leaveTypeList.add(getString(R.string.pdo));
            }
            leaveTypeAdapter.notifyDataSetChanged();
            spLeaveType.setSelection(0);
        } else {
            resetLeaveTypeOptions();
        }
    }

    private void showNoBalanceAlert(String title, String message) {
        if (isShowingAlert) return;
        isShowingAlert = true;

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                    isShowingAlert = false;
                    selectedDateRangeString = "";
                    calculatedDays = 0;
                    currentStartMs = 0;
                    currentEndMs = 0;
                    etSelectedDates.setText("");
                    tvTotalDaysDisplay.setText(R.string.duration_zero);
                    resetLeaveTypeOptions();
                })
                .show();
    }

    private void resetLeaveTypeOptions() {
        leaveTypeList.clear();
        String selectedEmployee = spEmployeeName.getSelectedItem() != null ? spEmployeeName.getSelectedItem().toString() : "";

        if (currentStartMs == 0 || currentEndMs == 0 || Objects.equals(selectedEmployee, getString(R.string.prompt_select_employee_name))) {
            leaveTypeList.add(getString(R.string.label_cuti_pdo));
            leaveTypeList.add(getString(R.string.cuti));
            leaveTypeList.add(getString(R.string.pdo));
            leaveTypeAdapter.notifyDataSetChanged();
            spLeaveType.setSelection(0);
            return;
        }

        EmployeeBalance balance = balanceMap.get(selectedEmployee);
        if (balance != null) {
            if (balance.cutiBalance < calculatedDays && balance.pdoBalance < calculatedDays) {
                leaveTypeList.add(getString(R.string.label_insufficient_balance));
                leaveTypeAdapter.notifyDataSetChanged();

                if (!Objects.equals(selectedEmployee, getString(R.string.prompt_select_employee_name))) {
                    showNoBalanceAlert(getString(R.string.label_insufficient_balance),
                            getString(R.string.alert_insufficient_balance_msg,
                                    calculatedDays, balance.cutiBalance, balance.pdoBalance));
                }
                return;
            }

            if (balance.cutiBalance >= calculatedDays) {
                leaveTypeList.add(getString(R.string.cuti));
            }
            if (balance.pdoBalance >= calculatedDays) {
                leaveTypeList.add(getString(R.string.pdo));
            }
        } else {
            leaveTypeList.add(getString(R.string.cuti));
            leaveTypeList.add(getString(R.string.pdo));
        }

        leaveTypeAdapter.notifyDataSetChanged();

        spLeaveType.post(() -> {
            if (leaveTypeList.size() == 2 && Objects.equals(leaveTypeList.get(0), getString(R.string.label_cuti_pdo))) {
                spLeaveType.setSelection(1);
            } else {
                spLeaveType.setSelection(0);
            }
        });
    }
}