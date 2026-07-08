package com.test.cutipdo2026;

import android.graphics.Color;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
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
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SpvRequestActivity extends AppCompatActivity {

    private EditText etSelectedDatesSpv, etLeaveDescriptionSpv;
    private Button btnSubmitDirect;
    private TextView tvTotalDaysDisplaySpv, tvCutiBalanceSpv, tvPdoBalanceSpv;
    private Spinner spEmployeeNameSpv;
    private LinearLayout btnTypeCuti, btnTypePdo;
    private RadioGroup rgCutiCategorySpv;
    private View ivShowRulesSpv;

    private final List<String> employeeList = new ArrayList<>();
    private final Map<String, EmployeeBalance> balanceMap = new HashMap<>();

    private GoogleSheetsApi googleSheetsApi;


    private String selectedDateRangeString = "";
    private int calculatedDays = 0;
    private String selectedLeaveType = "";

    private long currentStartMs = 0;
    private long currentEndMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spv_request);

        etSelectedDatesSpv = findViewById(R.id.etSelectedDatesSpv);
        etLeaveDescriptionSpv = findViewById(R.id.etLeaveDescriptionSpv);
        btnSubmitDirect = findViewById(R.id.btnSubmitDirect);
        tvTotalDaysDisplaySpv = findViewById(R.id.tvTotalDaysDisplaySpv);
        tvCutiBalanceSpv = findViewById(R.id.tvCutiBalanceSpv);
        tvPdoBalanceSpv = findViewById(R.id.tvPdoBalanceSpv);
        spEmployeeNameSpv = findViewById(R.id.spEmployeeNameSpv);
        btnTypeCuti = findViewById(R.id.btnTypeCuti);
        btnTypePdo = findViewById(R.id.btnTypePdo);
        rgCutiCategorySpv = findViewById(R.id.rgCutiCategorySpv);
        ivShowRulesSpv = findViewById(R.id.ivShowRulesSpv);

        ivShowRulesSpv.setOnClickListener(v -> showRulesDialog());

        // API Setup
        googleSheetsApi = RetrofitClient.getApi(this);

        // Data setup
        @SuppressWarnings("unchecked")
        ArrayList<EmployeeBalance> preFetchedBalances = (ArrayList<EmployeeBalance>) getIntent().getSerializableExtra("PRE_FETCHED_BALANCES");
        @SuppressWarnings("unchecked")
        ArrayList<String> preFetchedNames = (ArrayList<String>) getIntent().getSerializableExtra("PRE_FETCHED_NAMES");

        if (preFetchedBalances != null) {
            for (EmployeeBalance b : preFetchedBalances) balanceMap.put(b.name, b);
        }

        employeeList.clear();
        if (preFetchedNames != null && !preFetchedNames.isEmpty()) {
            if (preFetchedNames.size() == 1) {
                // If only one SPV, add just that one and it will be selected by default
                employeeList.addAll(preFetchedNames);
            } else {
                // If more than one, add the prompt and all names
                employeeList.add(getString(R.string.prompt_select_employee_name));
                employeeList.addAll(preFetchedNames);
            }
        } else {
            employeeList.add(getString(R.string.prompt_select_employee_name));
            Toast.makeText(this, getString(R.string.toast_roster_empty_relogin), Toast.LENGTH_LONG).show();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, employeeList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEmployeeNameSpv.setAdapter(adapter);

        // If only one actual employee name, select it and disable the spinner
        if (preFetchedNames != null && preFetchedNames.size() == 1) {
            spEmployeeNameSpv.setSelection(0);
            spEmployeeNameSpv.setEnabled(false);
            // Manually trigger balance update
            spEmployeeNameSpv.post(this::updateBalanceDisplay);
        }

        spEmployeeNameSpv.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateBalanceDisplay();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        etSelectedDatesSpv.setOnClickListener(v -> showDatePicker());

        btnTypeCuti.setOnClickListener(v -> selectLeaveType(getString(R.string.cuti)));
        btnTypePdo.setOnClickListener(v -> selectLeaveType(getString(R.string.pdo)));

        btnSubmitDirect.setOnClickListener(v -> executeDirectSubmission());

        // 💡 CATEGORY LOGIC: Same as MainActivity
        rgCutiCategorySpv.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbKhususSpv) {
                String current = etLeaveDescriptionSpv.getText().toString();
                if (!current.startsWith("[Khusus] ")) {
                    etLeaveDescriptionSpv.setText("[Khusus] " + current.replace("[Bersurat] ", ""));
                    etLeaveDescriptionSpv.setSelection(etLeaveDescriptionSpv.getText().length());
                }
            } else if (checkedId == R.id.rbBersuratSpv) {
                String current = etLeaveDescriptionSpv.getText().toString();
                if (!current.startsWith("[Bersurat] ")) {
                    etLeaveDescriptionSpv.setText("[Bersurat] " + current.replace("[Khusus] ", ""));
                    etLeaveDescriptionSpv.setSelection(etLeaveDescriptionSpv.getText().length());
                }
            } else {
                String text = etLeaveDescriptionSpv.getText().toString();
                etLeaveDescriptionSpv.setText(text.replace("[Khusus] ", "").replace("[Bersurat] ", ""));
            }
        });

        // 💡 SAKIT DYNAMIC BYPASS: Re-evaluate rules when typing "sakit"
        etLeaveDescriptionSpv.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                evaluateRules();
                
                // Prefix enforcement
                String prefix = "";
                int checkedId = rgCutiCategorySpv.getCheckedRadioButtonId();
                if (checkedId == R.id.rbKhususSpv) prefix = "[Khusus] ";
                else if (checkedId == R.id.rbBersuratSpv) prefix = "[Bersurat] ";

                if (!prefix.isEmpty() && !s.toString().startsWith(prefix)) {
                    rgCutiCategorySpv.clearCheck();
                }
            }
        });
    }

    private void showRulesDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.label_rules_info)
                .setMessage(android.text.Html.fromHtml(getString(R.string.msg_rules_content)))
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    private void updateBalanceDisplay() {
        String name = spEmployeeNameSpv.getSelectedItem().toString();
        EmployeeBalance balance = balanceMap.get(name);
        if (balance != null) {
            int displayCuti = balance.cutiBalance;
            int displayPdo = balance.pdoBalance;

            boolean isSpecial = rgCutiCategorySpv.getCheckedRadioButtonId() != -1;

            // 💡 Apply "Virtual Deduction" if a leave type is currently selected
            if (Objects.equals(selectedLeaveType, getString(R.string.cuti))) {
                if (!isSpecial) {
                    displayCuti -= calculatedDays;
                }
            } else if (Objects.equals(selectedLeaveType, getString(R.string.pdo))) {
                displayPdo -= calculatedDays;
            }

            tvCutiBalanceSpv.setText(String.valueOf(Math.max(0, displayCuti)));
            tvPdoBalanceSpv.setText(String.valueOf(Math.max(0, displayPdo)));
        } else {
            tvCutiBalanceSpv.setText(R.string.zero);
            tvPdoBalanceSpv.setText(R.string.zero);
        }
        evaluateRules();
    }

    private void showDatePicker() {
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
            @Override public int describeContents() { return 0; }
            @Override public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {}
        });

        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.label_select_leave_dates_title)
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        picker.show(getSupportFragmentManager(), "SPV_DATE_PICKER");
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null && selection.first != null && selection.second != null) {
                currentStartMs = selection.first;
                currentEndMs = selection.second;
                calculatedDays = (int) ((currentEndMs - currentStartMs) / (1000 * 60 * 60 * 24)) + 1;

                SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                String start = formatter.format(new Date(currentStartMs));
                String end = formatter.format(new Date(currentEndMs));
                selectedDateRangeString = Objects.equals(start, end) ? start : start + " to " + end;

                etSelectedDatesSpv.setText(selectedDateRangeString);
                tvTotalDaysDisplaySpv.setText(getString(R.string.duration_placeholder, calculatedDays));

                String name = spEmployeeNameSpv.getSelectedItem().toString();
                EmployeeBalance balance = balanceMap.get(name);
                if (balance != null) {
                    // 💡 Rule 2: Force PDO if Cuti is 0 but PDO is available
                    if (balance.cutiBalance < calculatedDays && balance.pdoBalance >= calculatedDays) {
                        selectedLeaveType = getString(R.string.pdo);
                        updateSelectionVisuals();
                    }
                }

                updateBalanceDisplay(); // 💡 Refresh balance and rules with new date count
            }
        });
    }

    private void evaluateRules() {
        if (currentStartMs != 0 && currentEndMs != 0) {
            // Logic for weekend detection if needed in the future
        }

        // 💡 GENERAL DENDA LOGIC: Always allow both Cuti and PDO buttons.
        // We handle the (denda) marking during submission if balance is insufficient.
        btnTypeCuti.setEnabled(calculatedDays > 0);
        btnTypeCuti.setAlpha(calculatedDays > 0 ? 1.0f : 0.4f);
        btnTypePdo.setEnabled(calculatedDays > 0);
        btnTypePdo.setAlpha(calculatedDays > 0 ? 1.0f : 0.4f);

        updateSelectionVisuals();
    }

    private void selectLeaveType(String type) {
        if (calculatedDays <= 0) {
            Toast.makeText(this, "Pilih tanggal terlebih dahulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        String empName = spEmployeeNameSpv.getSelectedItem().toString();
        EmployeeBalance balance = balanceMap.get(empName);

        // 💡 Rule: Warning if Cuti balance is 0
        if (Objects.equals(type, getString(R.string.cuti)) && balance != null && balance.cutiBalance <= 0) {
            Toast.makeText(this, "Saldo Cuti 0. Gunakan PDO atau pilih kategori Khusus/Bersurat.", Toast.LENGTH_LONG).show();
        }

        // If tapping the same type twice, deselect it (optional but good for UX)
        if (Objects.equals(selectedLeaveType, type)) {
            selectedLeaveType = "";
        } else {
            selectedLeaveType = type;
        }

        if (Objects.equals(selectedLeaveType, getString(R.string.cuti))) {
            rgCutiCategorySpv.setVisibility(View.VISIBLE);
        } else {
            rgCutiCategorySpv.setVisibility(View.GONE);
            rgCutiCategorySpv.clearCheck();
        }

        updateSelectionVisuals();
        updateBalanceDisplay(); // Refresh balance numbers with virtual deduction
    }

    private void updateSelectionVisuals() {
        btnTypeCuti.setSelected(Objects.equals(selectedLeaveType, getString(R.string.cuti)));
        btnTypePdo.setSelected(Objects.equals(selectedLeaveType, getString(R.string.pdo)));
    }

    private void executeDirectSubmission() {
        String name = spEmployeeNameSpv.getSelectedItem().toString();
        if (Objects.equals(name, getString(R.string.prompt_select_employee_name)) || selectedDateRangeString.isEmpty() || selectedLeaveType.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_complete_all_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        // 💡 Rule: Block Cuti on weekend if no special category
        boolean containsWeekend = isWeekendInRange(currentStartMs, currentEndMs);
        boolean isSpecialCategory = rgCutiCategorySpv.getCheckedRadioButtonId() != -1;
        if (containsWeekend && Objects.equals(selectedLeaveType, getString(R.string.cuti)) && !isSpecialCategory) {
            Toast.makeText(this, "⚠️ Weekend terdeteksi! Gunakan PDO atau pilih kategori Khusus/Bersurat untuk Cuti.", Toast.LENGTH_LONG).show();
            return;
        }

        EmployeeBalance balance = balanceMap.get(name);
        String filterClass = getIntent().getStringExtra("FILTER_CLASS");
        boolean isRestrictedDivision = filterClass != null && (filterClass.equalsIgnoreCase("Teknis") || filterClass.equalsIgnoreCase("Guide") || filterClass.equalsIgnoreCase("H.K."));
        String description = etLeaveDescriptionSpv.getText().toString().trim();
        boolean isSakit = description.toLowerCase().contains("sakit");
        // 💡 isSpecialCategory is already defined above

        // 💡 GENERAL DENDA LOGIC: Allow any request with insufficient balance but mark as (denda)
        int selectedBalanceValue = Objects.equals(selectedLeaveType, getString(R.string.cuti)) ? balance.cutiBalance : balance.pdoBalance;
        if (!isSpecialCategory && selectedBalanceValue < calculatedDays) {
            // 💡 PENALTY WARNING DIALOG
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_insufficient_balance_title)
                    .setMessage(getString(R.string.dialog_insufficient_balance_message, selectedLeaveType, selectedBalanceValue))
                    .setPositiveButton(R.string.btn_process_denda, (dialog, which) -> {
                        String finalDesc = etLeaveDescriptionSpv.getText().toString().trim();
                        if (!finalDesc.toLowerCase().contains("(denda)")) {
                            finalDesc = (finalDesc.isEmpty() ? "(denda)" : finalDesc + " (denda)");
                        }
                        finalizeDirectSubmission(name, finalDesc, balance, isRestrictedDivision);
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
            return;
        }

        finalizeDirectSubmission(name, description, balance, isRestrictedDivision);
    }

    private void finalizeDirectSubmission(String name, String description, EmployeeBalance balance, boolean isRestrictedDivision) {
        boolean isSakit = description.toLowerCase().contains("sakit");
        boolean hasDW = description.toUpperCase().matches(".*\\b(DW|DAILY WORKER)\\b.*");
        boolean isSpecialCategory = description.contains("[Khusus]") || description.contains("[Bersurat]");

        // 💡 DIVISION QUOTA CHECK
        if (isRestrictedDivision && !isSpecialCategory && !isSakit && !hasDW) {
            @SuppressWarnings("unchecked")
            ArrayList<LeaveRequestData> preFetchedApproved = (ArrayList<LeaveRequestData>) getIntent().getSerializableExtra("PRE_FETCHED_APPROVED");
            if (preFetchedApproved != null) {
                String startNew = selectedDateRangeString.split(" to ")[0];
                String endNew = selectedDateRangeString.contains(" to ") ? selectedDateRangeString.split(" to ")[1] : startNew;

                for (LeaveRequestData old : preFetchedApproved) {
                    if (Objects.equals(old.employeeName, name)) continue;

                    // 💡 BUG FIX: Skip checks for Declined or Cancelled requests
                    String oldStatus = (old.status != null) ? old.status.toUpperCase() : "PENDING";
                    if (oldStatus.equals("DECLINED") || oldStatus.equals("CANCELLED")) continue;
                    
                    // 💡 IMPORTANT: Only block if they are in the SAME DIVISION
                    EmployeeBalance otherEmp = balanceMap.get(old.employeeName);
                    if (otherEmp != null && balance != null && Objects.equals(otherEmp.empClass, balance.empClass)) {
                        if (isDateOverlap(startNew, endNew, old.getFormattedDate())) {
                            Toast.makeText(this, "⚠️ " + old.employeeName + " (" + otherEmp.empClass + ") sudah ambil tanggal ini!", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                }
            }
        }

        // 💡 7 WORKING DAYS VALIDATION
        if (!isSpecialCategory && balance != null && balance.lastLeaveDate != null && !balance.lastLeaveDate.isEmpty() && !description.toLowerCase().contains("sakit")) {
            int gap = countWorkDaysBetween(balance.lastLeaveDate, selectedDateRangeString.split(" to ")[0]);
            if (gap < 7) {
                Toast.makeText(this, "⚠️ Belum 7 hari kerja sejak izin terakhir!", Toast.LENGTH_LONG).show();
                return;
            }
        }

        btnSubmitDirect.setEnabled(false);
        btnSubmitDirect.setText(R.string.msg_processing);

        SharedPreferences prefs = getSharedPreferences("DEV_OPTS", MODE_PRIVATE);
        String customRecipient = prefs.getBoolean("USE_LOCAL_NOTIF", false) ? prefs.getString("LOCAL_NOTIF_NUMBER", "") : null;

        // Straight to approve!
        LeaveRequest payload = new LeaveRequest("approve_direct", name, selectedDateRangeString, calculatedDays, selectedLeaveType, description);
        if (customRecipient != null) payload.setCustomRecipient(customRecipient);

        googleSheetsApi.sendRequest(payload).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(SpvRequestActivity.this, getString(R.string.toast_request_approved_logged), Toast.LENGTH_LONG).show();
                    
                    finish();
                } else {
                    Toast.makeText(SpvRequestActivity.this, getString(R.string.toast_server_rejected_action, response.code()), Toast.LENGTH_SHORT).show();
                    btnSubmitDirect.setEnabled(true);
                    btnSubmitDirect.setText(R.string.btn_submit);
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                handleNetworkError(t);
                btnSubmitDirect.setEnabled(true);
                btnSubmitDirect.setText(R.string.btn_submit);
            }
        });
    }

    private void handleNetworkError(Throwable t) {
        String empName = spEmployeeNameSpv.getSelectedItem() != null ? spEmployeeNameSpv.getSelectedItem().toString() : "Unknown";
        String errorType = (t instanceof java.net.SocketTimeoutException) ? "Timeout" : "Network Error";
        ErrorReporter.report(this, empName, "SpvRequestActivity", t.getMessage(), errorType);

        if (t instanceof java.net.SocketTimeoutException) {
            Toast.makeText(this, R.string.toast_timeout_error, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.toast_network_error, t.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isWeekendInRange(long start, long end) {
        Calendar checkCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        checkCalendar.setTimeInMillis(start);
        while (checkCalendar.getTimeInMillis() <= end) {
            int dayOfWeek = checkCalendar.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                return true;
            }
            checkCalendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return false;
    }

    private boolean isDateOverlap(String startA, String endA, String rangeB) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date sA = sdf.parse(startA);
            Date eA = sdf.parse(endA);
            String[] partsB = rangeB.contains(" to ") ? rangeB.split(" to ") : new String[]{rangeB, rangeB};
            Date sB = sdf.parse(partsB[0]);
            Date eB = sdf.parse(partsB[1]);
            return (sA != null && eB != null && sB != null && eA != null) &&
                    (sA.before(eB) || sA.equals(eB)) && (eA.after(sB) || eA.equals(sB));
        } catch (Exception e) { return false; }
    }

    private int countWorkDaysBetween(String startStr, String endStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            cal.setTime(Objects.requireNonNull(sdf.parse(startStr)));
            Date endDate = sdf.parse(endStr);
            int workDays = 0;
            cal.add(Calendar.DAY_OF_MONTH, 1);
            while (cal.getTime().before(endDate)) {
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                if (dayOfWeek != Calendar.MONDAY && dayOfWeek != Calendar.TUESDAY) workDays++;
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            return workDays;
        } catch (Exception e) { return 99; }
    }
}
