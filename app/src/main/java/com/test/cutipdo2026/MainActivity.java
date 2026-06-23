package com.test.cutipdo2026;

import android.text.Editable;
import android.text.TextWatcher;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.google.android.material.button.MaterialButton;
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

public class MainActivity extends AppCompatActivity {

    private EditText etSelectedDates, etLeaveDescription;
    private Button btnAddToBatch, btnSubmitToSpv;
    private TextView tvTotalDaysDisplay, tvClearSelection, tvSelectAll, tvMainProgressMessage;
    private Spinner spEmployeeName, spLeaveType;
    private RadioGroup rgCutiCategory;
    private RecyclerView rvBatchQueue;
    private View layoutQueueHeader, mainProgressOverlay;

    private final List<String> employeeList = new ArrayList<>();
    private final Map<String, EmployeeBalance> balanceMap = new HashMap<>();

    private final List<String> leaveTypeList = new ArrayList<>();
    private ArrayAdapter<String> leaveTypeAdapter;

    private ArrayList<QueuedRequest> batchQueue = new ArrayList<>();
    private ReviewQueueAdapter queueAdapter;
    private GoogleSheetsApi googleSheetsApi;
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
        rgCutiCategory = findViewById(R.id.rgCutiCategory);
        etSelectedDates = findViewById(R.id.etSelectedDates);
        etLeaveDescription = findViewById(R.id.etLeaveDescription);
        tvTotalDaysDisplay = findViewById(R.id.tvTotalDaysDisplay);
        tvClearSelection = findViewById(R.id.tvClearSelection);
        tvSelectAll = findViewById(R.id.tvSelectAll);
        layoutQueueHeader = findViewById(R.id.layoutQueueHeader);
        btnAddToBatch = findViewById(R.id.btnAddToBatch);
        btnSubmitToSpv = findViewById(R.id.btnSubmitToSpv);
        rvBatchQueue = findViewById(R.id.rvBatchQueue);
        mainProgressOverlay = findViewById(R.id.mainProgressOverlay);
        tvMainProgressMessage = findViewById(R.id.tvMainProgressMessage);

        googleSheetsApi = RetrofitClient.getApi(this);

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

        tvSelectAll.setOnClickListener(v -> {
            queueAdapter.selectAll();
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
                // 💡 BLOCK SELECTION IF DATE NOT SET
                if (currentStartMs == 0 || currentEndMs == 0) return false;

                if (position >= leaveTypeList.size()) return true;
                return !Objects.equals(leaveTypeList.get(position), getString(R.string.label_cuti_pdo));
            }

            @Override
            @NonNull
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                
                // 💡 Visual feedback: Gray out if date not set or if it's the prompt
                boolean isPrompt = position < leaveTypeList.size() && Objects.equals(leaveTypeList.get(position), getString(R.string.label_cuti_pdo));
                if (currentStartMs == 0 || currentEndMs == 0 || isPrompt) {
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
                
                boolean isPrompt = position < leaveTypeList.size() && Objects.equals(leaveTypeList.get(position), getString(R.string.label_cuti_pdo));
                if (currentStartMs == 0 || currentEndMs == 0 || isPrompt) {
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

        // Radio Button Category Logic for "Cuti"
        spLeaveType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedType = spLeaveType.getSelectedItem().toString();
                if (selectedType.equalsIgnoreCase(getString(R.string.cuti))) {
                    rgCutiCategory.setVisibility(View.VISIBLE);
                } else {
                    rgCutiCategory.setVisibility(View.GONE);
                    rgCutiCategory.clearCheck();
                    
                    // 💡 Clean up: Remove special tags if switching back to PDO
                    String currentDesc = etLeaveDescription.getText().toString();
                    if (currentDesc.equals("[Khusus] ") || currentDesc.equals("[Bersurat] ")) {
                        etLeaveDescription.setText("");
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 💡 Custom behavior: Allow deselecting the radio button
        for (int i = 0; i < rgCutiCategory.getChildCount(); i++) {
            View rb = rgCutiCategory.getChildAt(i);
            if (rb instanceof RadioButton) {
                rb.setOnClickListener(v -> {
                    RadioButton clickedRb = (RadioButton) v;
                    // If clicking the ALREADY selected button, uncheck it
                    if (clickedRb.getTag() != null && (boolean) clickedRb.getTag()) {
                        rgCutiCategory.clearCheck();
                        clickedRb.setTag(false);
                        
                        // Clear description if it only contained the tag
                        String desc = etLeaveDescription.getText().toString();
                        if (desc.equals("[Khusus] ") || desc.equals("[Bersurat] ")) {
                            etLeaveDescription.setText("");
                        }
                    } else {
                        // Mark this as selected, unmark others
                        for (int j = 0; j < rgCutiCategory.getChildCount(); j++) {
                            rgCutiCategory.getChildAt(j).setTag(false);
                        }
                        clickedRb.setTag(true);
                    }
                });
            }
        }

        rgCutiCategory.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbKhusus) {
                String current = etLeaveDescription.getText().toString();
                // Avoid re-setting if already there to prevent cursor jumping
                if (!current.startsWith("[Khusus] ")) {
                    etLeaveDescription.setText("[Khusus] ");
                    etLeaveDescription.setSelection(etLeaveDescription.getText().length());
                }
            } else if (checkedId == R.id.rbBersurat) {
                String current = etLeaveDescription.getText().toString();
                if (!current.startsWith("[Bersurat] ")) {
                    etLeaveDescription.setText("[Bersurat] ");
                    etLeaveDescription.setSelection(etLeaveDescription.getText().length());
                }
            } else {
                // 💡 CLEANUP: If nothing is selected, strip the tag but keep the rest of the text
                String text = etLeaveDescription.getText().toString();
                if (text.startsWith("[Khusus] ")) {
                    etLeaveDescription.setText(text.replace("[Khusus] ", ""));
                } else if (text.startsWith("[Bersurat] ")) {
                    etLeaveDescription.setText(text.replace("[Bersurat] ", ""));
                }
                // Unmark internal selection tags
                for (int i = 0; i < rgCutiCategory.getChildCount(); i++) {
                    rgCutiCategory.getChildAt(i).setTag(false);
                }
            }
        });

        // 🛡️ ENFORCE TAG PREFIX: Prevent typing before the tag. If tag is deleted, deselect category.
        etLeaveDescription.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String prefix = "";
                int checkedId = rgCutiCategory.getCheckedRadioButtonId();
                if (checkedId == R.id.rbKhusus) prefix = "[Khusus] ";
                else if (checkedId == R.id.rbBersurat) prefix = "[Bersurat] ";

                if (!prefix.isEmpty()) {
                    if (!s.toString().startsWith(prefix)) {
                        // 💡 BEST PRACTICE: If user deletes/modifies the tag, deselect the category
                        rgCutiCategory.clearCheck();
                        // Note: clearCheck() will trigger its listener, but won't re-enter this if block
                    }
                }

                // 💡 DYNAMIC REFRESH: If "sakit" is typed, refresh the leave type options to allow (denda) path
                resetLeaveTypeOptions();
            }
        });

        // 💡 CURSOR PROTECTION & DYNAMIC TAG MANAGEMENT
        etLeaveDescription.setOnClickListener(v -> {
            String prefix = "";
            int checkedId = rgCutiCategory.getCheckedRadioButtonId();
            if (checkedId == R.id.rbKhusus) prefix = "[Khusus] ";
            else if (checkedId == R.id.rbBersurat) prefix = "[Bersurat] ";
            
            if (!prefix.isEmpty() && etLeaveDescription.getSelectionStart() < prefix.length()) {
                etLeaveDescription.setSelection(prefix.length());
            }
        });

        // =========================================================================
        // 💡 4. EXTRACT PRE-FETCHED DATABASE DATA BUNDLES INSTANTLY
        // =========================================================================
        @SuppressWarnings("unchecked")
        ArrayList<EmployeeBalance> preFetchedBalances = (ArrayList<EmployeeBalance>) getIntent().getSerializableExtra("PRE_FETCHED_BALANCES");
        @SuppressWarnings("unchecked")
        ArrayList<String> preFetchedNames = (ArrayList<String>) getIntent().getSerializableExtra("PRE_FETCHED_NAMES");
        @SuppressWarnings("unchecked")
        ArrayList<LeaveRequestData> preFetchedApproved = (ArrayList<LeaveRequestData>) getIntent().getSerializableExtra("PRE_FETCHED_APPROVED");

        employeeList.clear();
        employeeList.add(getString(R.string.prompt_select_employee_name));

        if (preFetchedBalances != null) {
            String filterClass = getIntent().getStringExtra("FILTER_CLASS");
            for (EmployeeBalance b : preFetchedBalances) {
                // 💡 MASTER PASSCODE FIX: If we are in "Testing" mode, don't filter out the test user
                if (Objects.equals(filterClass, "Testing")) {
                    balanceMap.put(b.name, b);
                } else {
                    balanceMap.put(b.name, b);
                }
                Log.d("BALANCE_CHECK", "Loaded -> Emp: " + b.name + " Cuti: " + b.cutiBalance + " PDO: " + b.pdoBalance);
            }
        }

        if (preFetchedNames != null) {
            employeeList.addAll(preFetchedNames);
        }

        employeeAdapter.notifyDataSetChanged();

        // 💡 AUTO-SELECT: If there's only 1 employee (size=2 because of prompt), select it automatically
        if (employeeList.size() == 2) {
            spEmployeeName.setSelection(1);
        }

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
                String filterClass = getIntent().getStringExtra("FILTER_CLASS");
                boolean isRestrictedDivision = filterClass != null && (filterClass.equalsIgnoreCase("Teknis") || filterClass.equalsIgnoreCase("Guide"));
                boolean isSpecialCategory = rgCutiCategory.getCheckedRadioButtonId() != -1;
                boolean isSakit = description.toLowerCase().contains("sakit");

                // 💡 DIVISION QUOTA CHECK: Only 1 person per division (H.K, Teknis, or Guide)
                // We bypass this for Sakit or Special categories (Khusus/Bersurat)
                if (isRestrictedDivision && !isSpecialCategory && !isSakit) {
                    String startNew = selectedDateRangeString.split(" to ")[0];
                    String endNew = selectedDateRangeString.contains(" to ") ? selectedDateRangeString.split(" to ")[1] : startNew;

                    // 1. Check against Approved Database
                    if (preFetchedApproved != null) {
                        for (LeaveRequestData old : preFetchedApproved) {
                            if (old.employeeName.equals(empName)) continue; // Skip self
                            
                            // 💡 IMPORTANT: Only block if they are in the SAME DIVISION
                            EmployeeBalance otherEmp = balanceMap.get(old.employeeName);
                            if (otherEmp != null && Objects.equals(otherEmp.empClass, balance.empClass)) {
                                if (isDateOverlap(startNew, endNew, old.getFormattedDate())) {
                                    Toast.makeText(this, "⚠️ " + old.employeeName + " (" + otherEmp.empClass + ") sudah ambil tanggal ini!", Toast.LENGTH_LONG).show();
                                    return;
                                }
                            }
                        }
                    }

                    // 2. Check against Local Batch Queue
                    for (QueuedRequest req : batchQueue) {
                        if (req.getEmployeeName().equals(empName)) continue; // Skip self
                        
                        EmployeeBalance otherEmp = balanceMap.get(req.getEmployeeName());
                        if (otherEmp != null && Objects.equals(otherEmp.empClass, balance.empClass)) {
                            if (isDateOverlap(startNew, endNew, req.getTargetDate())) {
                                Toast.makeText(this, "⚠️ " + req.getEmployeeName() + " (" + otherEmp.empClass + ") ada di daftar batch!", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                    }
                }

                // 💡 7 WORKING DAYS VALIDATION (Bypassed by Special Category or Sakit)
                if (!isSpecialCategory && !isSakit) {
                    String newStartStr = selectedDateRangeString.split(" to ")[0];
                    String newEndStr = selectedDateRangeString.contains(" to ") ? selectedDateRangeString.split(" to ")[1] : selectedDateRangeString;

                    // Collect all existing dates for this employee (DB + Queue)
                    List<Pair<String, String>> existingRanges = new ArrayList<>();
                    if (balance.lastLeaveDate != null && !balance.lastLeaveDate.isEmpty()) {
                        // Assuming DB date is a single date or we treat it as an end-point
                        existingRanges.add(new Pair<>(balance.lastLeaveDate, balance.lastLeaveDate));
                    }
                    for (QueuedRequest req : batchQueue) {
                        if (req.getEmployeeName().equals(empName)) {
                            String start = req.getTargetDate().split(" to ")[0];
                            String end = req.getTargetDate().contains(" to ") ? req.getTargetDate().split(" to ")[1] : start;
                            existingRanges.add(new Pair<>(start, end));
                        }
                    }

                    for (Pair<String, String> range : existingRanges) {
                        // 1. Check if new date is AFTER an existing leave
                        if (isDateAfter(newStartStr, range.second)) {
                            int gap = countWorkDaysBetween(range.second, newStartStr);
                            if (gap < 7) {
                                Toast.makeText(this, "⚠️ Terlalu dekat dengan izin tanggal " + range.second + " (Cuma " + gap + " hari kerja)", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        // 2. Check if new date is BEFORE an existing leave
                        else if (isDateAfter(range.first, newEndStr)) {
                            int gap = countWorkDaysBetween(newEndStr, range.first);
                            if (gap < 7) {
                                Toast.makeText(this, "⚠️ Terlalu dekat dengan izin tanggal " + range.first + " (Cuma " + gap + " hari kerja)", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        // 3. Overlap check
                        else {
                            Toast.makeText(this, "⚠️ Tanggal ini bentrok dengan izin lain di daftar!", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                }

                if (!isSpecialCategory) {
                    int selectedBalance = Objects.equals(leaveType, getString(R.string.cuti)) ? balance.cutiBalance : balance.pdoBalance;

                    if (selectedBalance < calculatedDays) {
                        // 💡 PENALTY WARNING DIALOG
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.dialog_insufficient_balance_title)
                                .setMessage(getString(R.string.dialog_insufficient_balance_message, leaveType, selectedBalance))
                                .setPositiveButton(R.string.btn_process_denda, (dialog, which) -> {
                                    String finalDesc = etLeaveDescription.getText().toString().trim();
                                    if (!finalDesc.toLowerCase().contains("(denda)")) {
                                        finalDesc = (finalDesc.isEmpty() ? "(denda)" : finalDesc + " (denda)");
                                    }
                                    addToBatch(empName, selectedDateRangeString, calculatedDays, leaveType, finalDesc);
                                })
                                .setNegativeButton(R.string.btn_cancel, null)
                                .show();
                        return; // Exit the main listener, let the dialog handle it
                    } else {
                        // 💡 Deduct normally
                        if (Objects.equals(leaveType, getString(R.string.cuti))) balance.cutiBalance -= calculatedDays;
                        else balance.pdoBalance -= calculatedDays;
                    }
                }
            }

            // Normal path (sufficient balance or special category)
            addToBatch(empName, selectedDateRangeString, calculatedDays, leaveType, description);
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
            
            mainProgressOverlay.setVisibility(View.VISIBLE);
            tvMainProgressMessage.setText(getString(R.string.msg_uploading));

            successUploadCount = 0;
            sendSelectedItemsSequentially(itemsToSend, 0);
        });
    }

    private void addToBatch(String empName, String dateRange, int days, String type, String desc) {
        QueuedRequest newRequest = new QueuedRequest(empName, dateRange, days, type, desc);
        batchQueue.add(newRequest);
        queueManager.saveQueue(batchQueue);
        queueAdapter.notifyItemInserted(batchQueue.size() - 1);
        updateQueueUi();

        Toast.makeText(MainActivity.this, getString(R.string.toast_added_to_batch, batchQueue.size()), Toast.LENGTH_SHORT).show();

        // Reset UI
        selectedDateRangeString = "";
        calculatedDays = 0;
        currentStartMs = 0;
        currentEndMs = 0;
        etSelectedDates.setText("");
        etLeaveDescription.setText("");
        rgCutiCategory.clearCheck();
        tvTotalDaysDisplay.setText(R.string.duration_zero);
        resetLeaveTypeOptions();
    }

    // 💡 Helper: Compares two dd/MM/yyyy strings
    private boolean isDateAfter(String dateA, String dateB) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return Objects.requireNonNull(sdf.parse(dateA)).after(sdf.parse(dateB));
        } catch (Exception e) { return false; }
    }

    // 💡 Helper: Checks if two date ranges overlap
    private boolean isDateOverlap(String startA, String endA, String rangeB) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date sA = sdf.parse(startA);
            Date eA = sdf.parse(endA);

            String[] partsB = rangeB.contains(" to ") ? rangeB.split(" to ") : new String[]{rangeB, rangeB};
            Date sB = sdf.parse(partsB[0]);
            Date eB = sdf.parse(partsB[1]);

            // Two ranges overlap if (StartA <= EndB) AND (EndA >= StartB)
            return (sA != null && eB != null && sB != null && eA != null) &&
                    (sA.before(eB) || sA.equals(eB)) && (eA.after(sB) || eA.equals(sB));
        } catch (Exception e) { return false; }
    }

    // 💡 Helper: Counts Wed-Sun days (skipping Mon/Tue)
    private int countWorkDaysBetween(String startStr, String endStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            cal.setTime(Objects.requireNonNull(sdf.parse(startStr)));
            Date endDate = sdf.parse(endStr);

            int workDays = 0;
            cal.add(Calendar.DAY_OF_MONTH, 1); // Start counting from the day after

            while (cal.getTime().before(endDate)) {
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                if (dayOfWeek != Calendar.MONDAY && dayOfWeek != Calendar.TUESDAY) {
                    workDays++;
                }
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            return workDays;
        } catch (Exception e) { return 99; }
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
        int markedCount = 0;
        for (QueuedRequest req : batchQueue) {
            if (req.isMarked) {
                markedCount++;
            }
        }
        boolean hasItems = !batchQueue.isEmpty();
        tvSelectAll.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        tvClearSelection.setVisibility(markedCount > 0 ? View.VISIBLE : View.GONE);
    }

    private void openInlineDatePicker(QueuedRequest request, final int position) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final TextView tvDateLabel = new TextView(this);
        tvDateLabel.setText(R.string.label_select_leave_dates);
        tvDateLabel.setPadding(0, 0, 0, 10);
        layout.addView(tvDateLabel);

        final Button btnChangeDate = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnChangeDate.setText(request.getTargetDate());
        layout.addView(btnChangeDate);

        final TextView tvDescLabel = new TextView(this);
        tvDescLabel.setText(R.string.label_reason_of_leaving);
        tvDescLabel.setPadding(0, 30, 0, 10);
        layout.addView(tvDescLabel);

        final EditText etEditDesc = new EditText(this);
        etEditDesc.setHint(R.string.hint_enter_reason);
        etEditDesc.setText(request.getDescription());
        layout.addView(etEditDesc);

        final String[] tempDate = {request.getTargetDate()};
        final int[] tempDays = {request.getTotalDays()};

        btnChangeDate.setOnClickListener(v -> {
            MaterialDatePicker<Pair<Long, Long>> rangePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText(R.string.label_modify_allocation_dates_title)
                    .setCalendarConstraints(getStrictConstraints())
                    .build();

            rangePicker.show(getSupportFragmentManager(), "EDIT_DATE_PICKER");
            rangePicker.addOnPositiveButtonClickListener(selection -> {
                if (selection != null && selection.first != null && selection.second != null) {
                    long diffMs = selection.second - selection.first;
                    tempDays[0] = (int) (diffMs / (1000 * 60 * 60 * 24)) + 1;

                    SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    format.setTimeZone(TimeZone.getTimeZone("UTC"));

                    String startStr = format.format(new Date(selection.first));
                    String endStr = format.format(new Date(selection.second));

                    tempDate[0] = Objects.equals(startStr, endStr) ? startStr : startStr + " to " + endStr;
                    btnChangeDate.setText(tempDate[0]);

                    Calendar checkCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    checkCalendar.setTimeInMillis(selection.first);
                    while (checkCalendar.getTimeInMillis() <= selection.second) {
                        int day = checkCalendar.get(Calendar.DAY_OF_WEEK);
                        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) {
                            request.setLeaveType(getString(R.string.pdo));
                            Toast.makeText(this, getString(R.string.toast_weekend_detected_pdo), Toast.LENGTH_SHORT).show();
                            break;
                        }
                        checkCalendar.add(Calendar.DAY_OF_MONTH, 1);
                    }
                }
            });
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.label_edit_request)
                .setView(layout)
                .setPositiveButton(R.string.btn_save_changes, (dialog, which) -> {
                    request.setTargetDate(tempDate[0]);
                    request.setTotalDays(tempDays[0]);
                    request.setDescription(etEditDesc.getText().toString().trim());

                    queueManager.saveQueue(batchQueue);
                    queueAdapter.notifyItemChanged(position);
                    updateQueueUi();
                    Toast.makeText(this, R.string.msg_changes_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private CalendarConstraints getStrictConstraints() {
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
        return constraintsBuilder.build();
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
            // 💡 ALL ITEMS FINISHED: Trigger ONE batch notification for the SPV
            googleSheetsApi.sendRequest(new LeaveRequest("notify", "", "", 0, "", "")).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    mainProgressOverlay.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, getString(R.string.toast_requests_submitted, successUploadCount), Toast.LENGTH_LONG).show();

                    // Clean up: Remove only the items that were successfully submitted
                    for (QueuedRequest submittedItem : items) {
                        batchQueue.remove(submittedItem);
                    }

                    queueManager.saveQueue(batchQueue);
                    queueAdapter.notifyDataSetChanged();
                    updateQueueUi();

                    btnSubmitToSpv.setEnabled(true);
                    setUiEnabled(true); // 🔓 Unlock everything
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    // Even if notification fails, the data is already saved
                    mainProgressOverlay.setVisibility(View.GONE);
                    btnSubmitToSpv.setEnabled(true);
                    setUiEnabled(true);
                }
            });
            return;
        }

        QueuedRequest item = items.get(index);
        tvMainProgressMessage.setText(getString(R.string.msg_batch_approving, (index + 1), items.size()));
        
        LeaveRequest networkPayload = new LeaveRequest("submit", item.getEmployeeName(), item.getTargetDate(), item.getTotalDays(), item.getLeaveType(), item.getDescription());

        googleSheetsApi.sendRequest(networkPayload).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    successUploadCount++;
                    sendSelectedItemsSequentially(items, index + 1);
                } else {
                    mainProgressOverlay.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, getString(R.string.toast_upload_failed_pos, index), Toast.LENGTH_SHORT).show();
                    btnSubmitToSpv.setEnabled(true);
                    setUiEnabled(true); // 🔓 Unlock on error
                    updateQueueUi();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                mainProgressOverlay.setVisibility(View.GONE);
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
        MaterialDatePicker<Pair<Long, Long>> dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.label_select_leave_dates_title)
                .setCalendarConstraints(getStrictConstraints())
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

        // 💡 FULL RESET: Clear category and text whenever dates change to prevent stale data
        rgCutiCategory.clearCheck();
        etLeaveDescription.setText("");

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
            // Always allow both PDO and Cuti when a weekend is involved.
            // PDO is standard for weekends, but Cuti is allowed for Special/Bersurat categories.
            leaveTypeList.add(getString(R.string.pdo));
            leaveTypeList.add(getString(R.string.cuti));

            leaveTypeAdapter.notifyDataSetChanged();
            
            // Default to PDO as it's the standard for weekends
            spLeaveType.setSelection(0);

            Toast.makeText(this, "ℹ️ Akhir pekan terdeteksi! Gunakan 'Cuti' hanya jika ini kategori Khusus/Bersurat.", Toast.LENGTH_LONG).show();
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
            
            // 💡 Ensure category is hidden if data is reset
            rgCutiCategory.setVisibility(View.GONE);
            return;
        }

        // 💡 Logic Update: Always allow both Cuti and PDO so "Denda" path is always accessible
        leaveTypeList.add(getString(R.string.cuti));
        leaveTypeList.add(getString(R.string.pdo));

        leaveTypeAdapter.notifyDataSetChanged();

        EmployeeBalance balance = balanceMap.get(selectedEmployee);
        if (balance != null) {
            if (balance.cutiBalance < calculatedDays && balance.pdoBalance >= calculatedDays) {
                spLeaveType.setSelection(leaveTypeList.indexOf(getString(R.string.pdo)));
            } else {
                spLeaveType.setSelection(0);
            }
        }
        
        // 💡 Trigger UI update for Cuti Category if Cuti is already selected
        String selectedType = spLeaveType.getSelectedItem().toString();
        if (selectedType.equalsIgnoreCase(getString(R.string.cuti))) {
            rgCutiCategory.setVisibility(View.VISIBLE);
        } else {
            rgCutiCategory.setVisibility(View.GONE);
        }
    }
}
