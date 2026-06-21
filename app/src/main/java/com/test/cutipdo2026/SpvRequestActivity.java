package com.test.cutipdo2026;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
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
    }

    private void updateBalanceDisplay() {
        String name = spEmployeeNameSpv.getSelectedItem().toString();
        EmployeeBalance balance = balanceMap.get(name);
        if (balance != null) {
            int displayCuti = balance.cutiBalance;
            int displayPdo = balance.pdoBalance;

            // 💡 Apply "Virtual Deduction" if a leave type is currently selected
            if (Objects.equals(selectedLeaveType, getString(R.string.cuti))) {
                displayCuti -= calculatedDays;
            } else if (Objects.equals(selectedLeaveType, getString(R.string.pdo))) {
                displayPdo -= calculatedDays;
            }

            tvCutiBalanceSpv.setText(String.valueOf(displayCuti));
            tvPdoBalanceSpv.setText(String.valueOf(displayPdo));
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
                updateBalanceDisplay(); // 💡 Refresh balance and rules with new date count
            }
        });
    }

    private void evaluateRules() {
        String name = spEmployeeNameSpv.getSelectedItem().toString();
        EmployeeBalance balance = balanceMap.get(name);

        boolean containsWeekend = false;
        if (currentStartMs != 0 && currentEndMs != 0) {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(currentStartMs);
            while (cal.getTimeInMillis() <= currentEndMs) {
                int day = cal.get(Calendar.DAY_OF_WEEK);
                if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) {
                    containsWeekend = true;
                    break;
                }
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

        boolean canCuti = balance != null && balance.cutiBalance >= calculatedDays;
        boolean canPdo = balance != null && balance.pdoBalance >= calculatedDays;

        if (containsWeekend && balance != null && balance.pdoBalance > 0) {
            canCuti = false;
        }

        btnTypeCuti.setEnabled(canCuti && calculatedDays > 0);
        btnTypeCuti.setAlpha(canCuti && calculatedDays > 0 ? 1.0f : 0.4f);
        btnTypePdo.setEnabled(canPdo && calculatedDays > 0);
        btnTypePdo.setAlpha(canPdo && calculatedDays > 0 ? 1.0f : 0.4f);

        if (Objects.equals(selectedLeaveType, getString(R.string.cuti)) && !canCuti) selectLeaveType("");
        if (Objects.equals(selectedLeaveType, getString(R.string.pdo)) && !canPdo) selectLeaveType("");

        updateSelectionVisuals();
    }

    private void selectLeaveType(String type) {
        // If tapping the same type twice, deselect it (optional but good for UX)
        if (Objects.equals(selectedLeaveType, type)) {
            selectedLeaveType = "";
        } else {
            selectedLeaveType = type;
        }
        updateSelectionVisuals();
        updateBalanceDisplay(); // Refresh balance numbers with virtual deduction
    }

    private void updateSelectionVisuals() {
        btnTypeCuti.setBackgroundColor(Objects.equals(selectedLeaveType, getString(R.string.cuti)) ? Color.LTGRAY : Color.TRANSPARENT);
        btnTypePdo.setBackgroundColor(Objects.equals(selectedLeaveType, getString(R.string.pdo)) ? Color.LTGRAY : Color.TRANSPARENT);
    }

    private void executeDirectSubmission() {
        String name = spEmployeeNameSpv.getSelectedItem().toString();
        if (Objects.equals(name, getString(R.string.prompt_select_employee_name)) || selectedDateRangeString.isEmpty() || selectedLeaveType.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_complete_all_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmitDirect.setEnabled(false);
        btnSubmitDirect.setText(R.string.msg_processing);

        // Straight to approve!
        LeaveRequest payload = new LeaveRequest("approve_direct", name, selectedDateRangeString, calculatedDays, selectedLeaveType, etLeaveDescriptionSpv.getText().toString().trim());
        
        googleSheetsApi.sendRequest(payload).enqueue(new Callback<>() {
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
                Toast.makeText(SpvRequestActivity.this, getString(R.string.toast_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
                btnSubmitDirect.setEnabled(true);
                btnSubmitDirect.setText(R.string.btn_submit);
            }
        });
    }
}
