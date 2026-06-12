package com.test.cutipdo2026;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.datepicker.MaterialDatePicker;
import androidx.core.util.Pair;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ReviewQueueActivity extends AppCompatActivity {

    private RecyclerView rvReviewQueue;
    private Button btnFinalSubmitAll;
    private ArrayList<QueuedRequest> batchList;
    private ReviewQueueAdapter queueAdapter;
    private GoogleSheetsApi googleSheetsApi;
    private CallMeBotApi callMeBotApi;
    private QueueManager queueManager;
    private int successUploadCount = 0;

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_queue);

        rvReviewQueue = findViewById(R.id.rvReviewQueue);
        btnFinalSubmitAll = findViewById(R.id.btnFinalSubmitAll);
        queueManager = new QueueManager(this);

        batchList = (ArrayList<QueuedRequest>) getIntent().getSerializableExtra("batchDataList");

        rvReviewQueue.setLayoutManager(new LinearLayoutManager(this));

        // 🛠️ THE UPGRADE: Pass the clean row-action click listeners straight into the adapter setup
        queueAdapter = new ReviewQueueAdapter(batchList, new ReviewQueueAdapter.OnItemActionListener() {
            @Override
            public void onEditSelected(QueuedRequest request, int position) {
                openInlineDatePicker(request, position);
            }

            @Override
            public void onDeleteSelected(int position) {
                batchList.remove(position);
                queueManager.saveQueue(batchList);
                queueAdapter.notifyItemRemoved(position);
                updateSubmitButtonText();
                Toast.makeText(ReviewQueueActivity.this, getString(R.string.msg_item_removed), Toast.LENGTH_SHORT).show();

                if (batchList.isEmpty()) finish();
            }
        });

        rvReviewQueue.setAdapter(queueAdapter);
        updateSubmitButtonText();

        // Configure network connection client pipeline
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

        btnFinalSubmitAll.setOnClickListener(v -> {
            btnFinalSubmitAll.setEnabled(false);
            btnFinalSubmitAll.setText(getString(R.string.msg_uploading));
            // Prevent interaction with the list while uploading
            rvReviewQueue.setAlpha(0.5f);
            rvReviewQueue.setClickable(false);
            rvReviewQueue.setEnabled(false);

            successUploadCount = 0;
            sendBatchItemsSequentially(0);
        });

        Retrofit callMeBotRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.callmebot.com/") // CallMeBot's central gateway address
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        callMeBotApi = callMeBotRetrofit.create(CallMeBotApi.class);
    }

    private void openInlineDatePicker(QueuedRequest request, final int position) {
        long today = MaterialDatePicker.todayInUtcMilliseconds();
        // 1. Build the identical Monday-start and Monday/Tuesday blocking rule constraints
        com.google.android.material.datepicker.CalendarConstraints.Builder constraintsBuilder =
                new com.google.android.material.datepicker.CalendarConstraints.Builder();

        constraintsBuilder.setFirstDayOfWeek(Calendar.MONDAY); // Force layout grid row to start on Monday
        constraintsBuilder.setStart(today);
        constraintsBuilder.setOpenAt(today);

        constraintsBuilder.setValidator(new com.google.android.material.datepicker.CalendarConstraints.DateValidator() {
            @Override
            public boolean isValid(long date) {
                if (date < today) return false;
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                cal.setTimeInMillis(date);
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

                // Gray out and block Monday and Tuesday clicks entirely
                return dayOfWeek != Calendar.MONDAY && dayOfWeek != Calendar.TUESDAY;
            }
            @Override
            public int describeContents() { return 0; }
            @Override
            public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {}
        });

        // 2. Parse the existing saved text string back into milliseconds to pre-select dates
        Pair<Long, Long> existingSelectionMs = null;
        try {
            String rawDateText = request.getTargetDate(); // e.g., "24/06/2026 to 25/06/2026" or "24/06/2026"
            SimpleDateFormat parser = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));

            if (rawDateText.contains(" to ")) {
                String[] splitDates = rawDateText.split(" to ");
                Date start = parser.parse(splitDates[0]);
                Date end = parser.parse(splitDates[1]);
                if (start != null && end != null) {
                    existingSelectionMs = new Pair<>(start.getTime(), end.getTime());
                }
            } else {
                Date single = parser.parse(rawDateText);
                if (single != null) {
                    existingSelectionMs = new Pair<>(single.getTime(), single.getTime());
                }
            }
        } catch (Exception e) {
            Log.e("ReviewQueue", "Error parsing date", e);
        }

        // 3. Initialize the Material Range Picker with the constraints AND pre-selection data
        com.google.android.material.datepicker.MaterialDatePicker.Builder<Pair<Long, Long>> pickerBuilder =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText("Modify Allocation Dates Timeline")
                        .setCalendarConstraints(constraintsBuilder.build());

        if (existingSelectionMs != null) {
            pickerBuilder.setSelection(existingSelectionMs); // 💡 Pre-select the previous dates on the calendar grid
        }

        MaterialDatePicker<Pair<Long, Long>> rangePicker = pickerBuilder.build();
        rangePicker.show(getSupportFragmentManager(), "INLINE_RE_PICKER");

        // 4. Listen for the positive confirmation click button change
        rangePicker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null && selection.first != null && selection.second != null) {
                long diffMs = selection.second - selection.first;
                int updatedDaysCount = (int) (diffMs / (1000 * 60 * 60 * 24)) + 1;

                SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                format.setTimeZone(TimeZone.getTimeZone("UTC"));

                String startString = format.format(new Date(selection.first));
                String endString = format.format(new Date(selection.second));

                // Assign updated date strings and count to your item model instance
                request.setTargetDate(Objects.equals(startString, endString) ? startString : startString + " to " + endString);
                request.setTotalDays(updatedDaysCount);

                // 💡 NEW WORKFLOW: Scan the edited range selection for weekend days
                boolean containsWeekend = false;
                Calendar checkCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                checkCalendar.setTimeInMillis(selection.first);

                while (checkCalendar.getTimeInMillis() <= selection.second) {
                    int dayOfWeek = checkCalendar.get(Calendar.DAY_OF_WEEK);
                    if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                        containsWeekend = true;
                        break;
                    }
                    checkCalendar.add(Calendar.DAY_OF_MONTH, 1);
                }

                // 💡 FORCE MUTATION: If a weekend exists, automatically overwrite type to PDO
                if (containsWeekend) {
                    request.setLeaveType("PDO");
                    Toast.makeText(this, "ℹ️ Weekend detected! Leave type forced to PDO.", Toast.LENGTH_SHORT).show();
                }

                // Tell your layout adapter engine to instantly repaint this modified row entry frame
                queueManager.saveQueue(batchList);
                queueAdapter.notifyItemChanged(position);
                updateSubmitButtonText();
            } else {
                queueAdapter.notifyItemChanged(position);
            }
        });

        // Reset the row design position safely if they close or cancel out of the calendar selection menu
        rangePicker.addOnCancelListener(dialog -> queueAdapter.notifyItemChanged(position));
    }

    private void sendBatchItemsSequentially(final int index) {
        if (index >= batchList.size()) {
            Toast.makeText(ReviewQueueActivity.this, "🎉 All " + successUploadCount + " requests submitted to Supervisor!", Toast.LENGTH_LONG).show();
            batchList.clear(); // Clear the local list
            queueManager.clearQueue(); // Clear the saved persistence
            finish();
            return;
        }

        QueuedRequest item = batchList.get(index);
        LeaveRequest networkPayload = new LeaveRequest("submit", item.getEmployeeName(), item.getTargetDate(), item.getTotalDays(), item.getLeaveType(), item.getDescription());

        googleSheetsApi.sendRequest(networkPayload).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    successUploadCount++;

                    // 💡 NEW WORKFLOW: Fire a CallMeBot notification for this successful item
                    String messageContent = "🔔 *New Leave Request Submitted!*\n\n" +
                            "👤 *Employee:* " + item.getEmployeeName() + "\n" +
                            "📅 *Dates:* " + item.getTargetDate() + "\n" +
                            "⏱️ *Duration:* " + item.getTotalDays() + " Day(s)\n" +
                            "📝 *Type:* " + item.getLeaveType();

                    // Replace with your registered international phone number and your exact CallMeBot API key
                    callMeBotApi.sendWhatsAppMessage("+628998366182", messageContent, "YOUR_API_KEY_HERE")
                            .enqueue(new Callback<>() {
                                @Override
                                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> res) {
                                    // Message sent successfully to the gateway!
                                }
                                @Override
                                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                                    // Silently fail or log if the messaging network dips
                                }
                            });

                    // Continue the sequential loop to the next batch item card
                    sendBatchItemsSequentially(index + 1);
                } else {
                    Toast.makeText(ReviewQueueActivity.this, "Failed uploading row position: " + index, Toast.LENGTH_SHORT).show();
                    btnFinalSubmitAll.setEnabled(true);
                    updateSubmitButtonText();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(ReviewQueueActivity.this, "Upload loop failure: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                btnFinalSubmitAll.setEnabled(true);
                updateSubmitButtonText();
            }
        });
    }

    private void updateSubmitButtonText() {
        btnFinalSubmitAll.setText(getString(R.string.btn_submit_all_count, batchList.size()));
    }
}