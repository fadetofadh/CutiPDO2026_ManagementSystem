package com.test.cutipdo2026;

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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private EditText etSelectedDates, etLeaveDescription;
    private Button btnAddToBatch, btnSubmitToSpv;
    private TextView tvTotalDaysDisplay, tvClearSelection;
    private Spinner spEmployeeName, spLeaveType;
    private RecyclerView rvBatchQueue;
    private View layoutQueueHeader;

    private final List<String> employeeList = new ArrayList<>();
    private final Map<String, EmployeeBalance> balanceMap = new HashMap<>();

    private final List<String> leaveTypeList = new ArrayList<>();
    private ArrayAdapter<String> leaveTypeAdapter;

    private ArrayList<QueuedRequest> batchQueue = new ArrayList<>();
    private ReviewQueueAdapter queueAdapter;
    private GoogleSheetsApi googleSheetsApi;
    private CallMeBotApi callMeBotApi;
    private QueueManager queueManager;
    private String selectedDateRangeString = "";
    private int calculatedDays = 0;
    private int successUploadCount = 0;

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
        tvClearSelection = findViewById(R.id.tvClearSelection);
        layoutQueueHeader = findViewById(R.id.layoutQueueHeader);
        btnAddToBatch = findViewById(R.id.btnAddToBatch);
        btnSubmitToSpv = findViewById(R.id.btnSubmitToSpv);
        rvBatchQueue = findViewById(R.id.rvBatchQueue);

        // Configure APIs
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

        Retrofit callMeBotRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.callmebot.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        callMeBotApi = callMeBotRetrofit.create(CallMeBotApi.class);

        // Setup RecyclerView
        rvBatchQueue.setLayoutManager(new LinearLayoutManager(this));
        queueAdapter = new ReviewQueueAdapter(batchQueue, new ReviewQueueAdapter.OnItemActionListener() {
            @Override
            public void onEditSelected(QueuedRequest request, int position) {
                openInlineDatePicker(request, position);
            }

            @Override
            public void onDeleteSelected(int position) {
                if (position >= 0 && position < batchQueue.size()) {
                    batchQueue.remove(position);
                    queueManager.saveQueue(batchQueue);
                    queueAdapter.notifyItemRemoved(position);
                    queueAdapter.notifyItemRangeChanged(position, batchQueue.size());
                    updateQueueUi();
                    Toast.makeText(MainActivity.this, getString(R.string.msg_item_removed), Toast.LENGTH_SHORT).show();
                }
            }
        });
        rvBatchQueue.setAdapter(queueAdapter);

        tvClearSelection.setOnClickListener(v -> {
            queueAdapter.clearAllMarks();
            updateQueueUi();
        });

        queueAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updateQueueUi();
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                updateQueueUi();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                updateQueueUi();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                updateQueueUi();
            }
        });

        updateQueueUi();

        // 2. Configure Employee Dropdown
        ArrayAdapter<String> employeeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, employeeList);
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
        leaveTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, leaveTypeList) {
            @Override
            public boolean isEnabled(int position) {
                if (position >= leaveTypeList.size()) return true;
                return !Objects.equals(leaveTypeList.get(position), getString(R.string.label_cuti_pdo));
            }

            @Override
            @NonNull
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                if (position < leaveTypeList.size() && Objects.equals(leaveTypeList.get(position), getString(R.string.label_cuti_pdo))) {
                    text.setTextColor(android.graphics.Color.GRAY);
                } else {
                    text.setTextColor(android.graphics.Color.BLACK);
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                if (position < leaveTypeList.size() && Objects.equals(leaveTypeList.get(position), getString(R.string.label_cuti_pdo))) {
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

        // 🔄 Partitioned Access: Filter the local batch queue to only show items for the current department
        syncAndFilterQueue();

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
            queueAdapter.notifyItemInserted(batchQueue.size() - 1);
            updateQueueUi();

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

        btnSubmitToSpv.setOnClickListener(v -> {
            // Determine which items to send
            ArrayList<QueuedRequest> itemsToSend = new ArrayList<>();
            for (QueuedRequest req : batchQueue) {
                if (req.isMarked) itemsToSend.add(req);
            }

            // If nothing is marked, send everything (default behavior)
            if (itemsToSend.isEmpty()) {
                itemsToSend.addAll(batchQueue);
            }

            btnSubmitToSpv.setEnabled(false);
            btnSubmitToSpv.setText(getString(R.string.msg_uploading));
            setUiEnabled(false); // 🔒 Lock the entire form and list

            successUploadCount = 0;
            sendSelectedItemsSequentially(itemsToSend, 0);
        });
    }

    private void updateQueueUi() {
        if (batchQueue.isEmpty()) {
            layoutQueueHeader.setVisibility(View.GONE);
            rvBatchQueue.setVisibility(View.GONE);
            btnSubmitToSpv.setVisibility(View.GONE);
        } else {
            layoutQueueHeader.setVisibility(View.VISIBLE);
            rvBatchQueue.setVisibility(View.VISIBLE);
            btnSubmitToSpv.setVisibility(View.VISIBLE);

            // 💡 Dynamic Label: Use marked count if selection mode is active, otherwise use total size
            int markedCount = 0;
            for (QueuedRequest req : batchQueue) {
                if (req.isMarked) markedCount++;
            }

            int displayCount = (markedCount > 0) ? markedCount : batchQueue.size();
            btnSubmitToSpv.setText(getString(R.string.btn_submit_all_count, displayCount));

            updateClearButtonVisibility();
        }
    }

    private void updateClearButtonVisibility() {
        boolean hasMarks = false;
        for (QueuedRequest req : batchQueue) {
            if (req.isMarked) {
                hasMarks = true;
                break;
            }
        }
        tvClearSelection.setVisibility(hasMarks ? View.VISIBLE : View.GONE);
    }

    private void openInlineDatePicker(QueuedRequest request, final int position) {
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
            public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {}
        });

        MaterialDatePicker<Pair<Long, Long>> rangePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.label_modify_allocation_dates_title)
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        rangePicker.show(getSupportFragmentManager(), "INLINE_RE_PICKER");

        rangePicker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null && selection.first != null && selection.second != null) {
                long diffMs = selection.second - selection.first;
                int updatedDaysCount = (int) (diffMs / (1000 * 60 * 60 * 24)) + 1;

                SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                format.setTimeZone(TimeZone.getTimeZone("UTC"));

                String startString = format.format(new Date(selection.first));
                String endString = format.format(new Date(selection.second));

                request.setTargetDate(Objects.equals(startString, endString) ? startString : startString + " to " + endString);
                request.setTotalDays(updatedDaysCount);

                boolean containsWeekend = false;
                Calendar checkCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                checkCalendar.setTimeInMillis(selection.first);
                while (checkCalendar.getTimeInMillis() <= selection.second) {
                    if (checkCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || checkCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                        containsWeekend = true;
                        break;
                    }
                    checkCalendar.add(Calendar.DAY_OF_MONTH, 1);
                }
                if (containsWeekend) {
                    request.setLeaveType(getString(R.string.pdo));
                    Toast.makeText(this, getString(R.string.toast_weekend_detected_pdo), Toast.LENGTH_SHORT).show();
                }

                queueManager.saveQueue(batchQueue);
                queueAdapter.notifyItemChanged(position);
                updateQueueUi();
            }
        });
    }

    private void setUiEnabled(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.5f;
        spEmployeeName.setEnabled(enabled);
        etSelectedDates.setEnabled(enabled);
        etLeaveDescription.setEnabled(enabled);
        spLeaveType.setEnabled(enabled);
        btnAddToBatch.setEnabled(enabled);
        rvBatchQueue.setEnabled(enabled);
        rvBatchQueue.setAlpha(alpha);
        
        // Form containers/labels alpha for visual feedback
        findViewById(R.id.spEmployeeName).setAlpha(alpha);
        findViewById(R.id.etSelectedDates).setAlpha(alpha);
        findViewById(R.id.etLeaveDescription).setAlpha(alpha);
        findViewById(R.id.spLeaveType).setAlpha(alpha);
    }

    private void sendSelectedItemsSequentially(final ArrayList<QueuedRequest> items, final int index) {
        if (index >= items.size()) {
            Toast.makeText(this, getString(R.string.toast_requests_submitted, successUploadCount), Toast.LENGTH_LONG).show();

            // 💡 Clean up: Remove only the items that were successfully submitted
            for (QueuedRequest submittedItem : items) {
                batchQueue.remove(submittedItem);
            }

            queueManager.saveQueue(batchQueue);
            queueAdapter.notifyDataSetChanged();
            updateQueueUi();

            btnSubmitToSpv.setEnabled(true);
            setUiEnabled(true); // 🔓 Unlock everything
            return;
        }

        QueuedRequest item = items.get(index);
        LeaveRequest networkPayload = new LeaveRequest("submit", item.getEmployeeName(), item.getTargetDate(), item.getTotalDays(), item.getLeaveType(), item.getDescription());

        googleSheetsApi.sendRequest(networkPayload).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    successUploadCount++;
                    String messageContent = getString(R.string.whatsapp_message_format,
                            item.getEmployeeName(), item.getTargetDate(), item.getTotalDays(), item.getLeaveType());

                    callMeBotApi.sendWhatsAppMessage("+628998366182", messageContent, "9378602")
                            .enqueue(new Callback<ResponseBody>() {
                                @Override
                                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                                    // WhatsApp notification triggered successfully
                                }
                                @Override
                                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                                    // Log failure or ignore
                                }
                            });

                    sendSelectedItemsSequentially(items, index + 1);
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_upload_failed_pos, index), Toast.LENGTH_SHORT).show();
                    btnSubmitToSpv.setEnabled(true);
                    setUiEnabled(true); // 🔓 Unlock on error
                    updateQueueUi();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, getString(R.string.toast_upload_loop_failure, t.getMessage()), Toast.LENGTH_SHORT).show();
                btnSubmitToSpv.setEnabled(true);
                setUiEnabled(true); // 🔓 Unlock on failure
                updateQueueUi();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncAndFilterQueue();
    }

    private void syncAndFilterQueue() {
        if (queueManager != null && !balanceMap.isEmpty()) {
            ArrayList<QueuedRequest> rawQueue = queueManager.loadQueue();
            ArrayList<QueuedRequest> filtered = new ArrayList<>();
            for (QueuedRequest req : rawQueue) {
                if (balanceMap.containsKey(req.getEmployeeName())) {
                    filtered.add(req);
                }
            }
            batchQueue.clear();
            batchQueue.addAll(filtered);
            if (queueAdapter != null) {
                queueAdapter.notifyDataSetChanged();
            }
            updateQueueUi();
            Log.d("QUEUE_SYNC", "Queue filtered onResume. Visible size: " + batchQueue.size());
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
