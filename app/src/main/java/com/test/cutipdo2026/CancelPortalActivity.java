package com.test.cutipdo2026;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CancelPortalActivity extends AppCompatActivity {

    private RecyclerView rvCancelHistory;
    private ProgressBar pbCancelLoader;
    private FrameLayout progressOverlay;
    private TextView tvProgressMessage, tvClearSelectionCancel;
    private Button btnBatchCancel;
    private SwipeRefreshLayout swipeRefreshCancel;
    private GoogleSheetsApi googleSheetsApi;
    private List<LeaveRequestData> historyList = new ArrayList<>();
    private UniversalRequestAdapter listAdapter;
    private String filterClass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cancel_portal);

        filterClass = getIntent().getStringExtra("FILTER_CLASS");

        rvCancelHistory = findViewById(R.id.rvCancelHistory);
        pbCancelLoader = findViewById(R.id.pbCancelLoader);
        progressOverlay = findViewById(R.id.progressOverlay);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        tvClearSelectionCancel = findViewById(R.id.tvClearSelectionCancel);
        btnBatchCancel = findViewById(R.id.btnBatchCancel);
        swipeRefreshCancel = findViewById(R.id.swipeRefreshCancel);

        swipeRefreshCancel.setOnRefreshListener(this::loadHistoryLogQueue);

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

        rvCancelHistory.setLayoutManager(new LinearLayoutManager(this));

        loadHistoryLogQueue();
    }

    private void loadHistoryLogQueue() {
        if (!swipeRefreshCancel.isRefreshing()) {
            pbCancelLoader.setVisibility(View.VISIBLE);
        }
        rvCancelHistory.setVisibility(View.GONE);

        // Crucial Fix: Use filterClass to only show requests matching the logged-in category
        googleSheetsApi.getAllRequests("all", filterClass).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<LeaveRequestData>> call, @NonNull Response<List<LeaveRequestData>> response) {
                // 🛡️ Safety Switch: Kill loader instantly before handling any data checks
                pbCancelLoader.setVisibility(View.GONE);
                swipeRefreshCancel.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    historyList = response.body();

                    if (historyList.isEmpty()) {
                        Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_no_approved_requests), Toast.LENGTH_LONG).show();
                        // 💡 Automatically go back if list is empty
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> finish(), 1500);
                    } else {
                        rvCancelHistory.setVisibility(View.VISIBLE);
                        listAdapter = new UniversalRequestAdapter(historyList, new UniversalRequestAdapter.OnItemActionListener() {
                            @Override
                            public void onEditSelected(LeaveRequestData request, int position) {
                                Toast.makeText(CancelPortalActivity.this, "Edit feature coming soon!", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onDeleteSelected(int position) {
                                LeaveRequestData item = historyList.get(position);
                                String detail = String.format("Employee: %s\nDates: %s\nDuration: %d Day(s)\n\nThis will delete calendar entries and refund balance. Proceed?", 
                                    item.employeeName, item.getFormattedDate(), item.totalDays);
                                
                                new AlertDialog.Builder(CancelPortalActivity.this)
                                    .setTitle("🚨 CONFIRM INDIVIDUAL REVOKE")
                                    .setMessage(detail)
                                    .setPositiveButton("YES, REVOKE", (dialog, which) -> {
                                        executeCloudCancellation(item.rowNumber, position);
                                    })
                                    .setNegativeButton("NO", null)
                                    .show();
                            }

                            @Override
                            public void onApproveQuick(int position) {
                                // No quick approve in cancel portal
                                listAdapter.notifyItemChanged(position);
                            }
                        });
                        listAdapter.setSwipeLocked(true); // 🔒 Disable the swipe-reveal (Edit/Delete) drawer
                        rvCancelHistory.setAdapter(listAdapter);

                        tvClearSelectionCancel.setOnClickListener(v -> {
                            listAdapter.clearAllMarks();
                            updateClearButtonVisibility();
                        });

                        btnBatchCancel.setOnClickListener(v -> {
                            List<LeaveRequestData> markedItems = new ArrayList<>();
                            for (LeaveRequestData req : historyList) {
                                if (req.isMarked) markedItems.add(req);
                            }
                            if (!markedItems.isEmpty()) {
                                showBatchCancelConfirmation(markedItems);
                            }
                        });

                        listAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                            @Override
                            public void onChanged() {
                                updateClearButtonVisibility();
                            }

                            @Override
                            public void onItemRangeChanged(int positionStart, int itemCount) {
                                updateClearButtonVisibility();
                            }
                        });

                        new ItemTouchHelper(new UniversalSwipeCallback(new UniversalSwipeCallback.OnSwipeListener() {
                            @Override
                            public void onApprove(int position) {
                                listAdapter.onApproveQuick(position);
                            }

                            @Override
                            public boolean canSwipe(int position) {
                                return false; // 🔒 Disable swipe right/left in cancel portal
                            }
                        }, CancelPortalActivity.this, 0)) // 0 means no directions allowed for swipe
                                .attachToRecyclerView(rvCancelHistory);
                    }
                } else {
                    Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_server_error_code, response.code()), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<LeaveRequestData>> call, @NonNull Throwable t) {
                // 🛡️ Safety Switch: Clear loader on absolute hardware or script network crash
                pbCancelLoader.setVisibility(View.GONE);
                swipeRefreshCancel.setRefreshing(false);
                Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_network_failure, t.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateClearButtonVisibility() {
        int markedCount = 0;
        for (LeaveRequestData req : historyList) {
            if (req.isMarked) {
                markedCount++;
            }
        }
        tvClearSelectionCancel.setVisibility(markedCount > 0 ? View.VISIBLE : View.GONE);
        btnBatchCancel.setVisibility(markedCount > 0 ? View.VISIBLE : View.GONE);
        btnBatchCancel.setText("CANCEL SELECTED (" + markedCount + ")");
    }

    private void showBatchCancelConfirmation(final List<LeaveRequestData> items) {
        StringBuilder summary = new StringBuilder();
        summary.append("You are about to cancel the following requests:\n\n");
        for (LeaveRequestData item : items) {
            summary.append("• ").append(item.employeeName).append(" (").append(item.totalDays).append(" Days)\n");
            summary.append("  ").append(item.getFormattedDate()).append("\n\n");
        }
        summary.append("This will delete calendar events and refund sisa balances. Proceed?");

        new AlertDialog.Builder(this)
                .setTitle("🚨 BATCH CANCEL CONFIRMATION")
                .setMessage(summary.toString())
                .setPositiveButton("YES, CANCEL ALL", (dialog, which) -> {
                    processBatchCancellation(items, 0);
                })
                .setNegativeButton("NO", null)
                .show();
    }

    private void processBatchCancellation(final List<LeaveRequestData> items, final int index) {
        if (index >= items.size()) {
            progressOverlay.setVisibility(View.GONE);
            Toast.makeText(this, "Batch cancellation complete!", Toast.LENGTH_SHORT).show();
            loadHistoryLogQueue(); // Refresh
            return;
        }

        LeaveRequestData item = items.get(index);
        tvProgressMessage.setText("Batch Revoking (" + (index + 1) + "/" + items.size() + ")...");
        progressOverlay.setVisibility(View.VISIBLE);

        LeaveRequest cancelPackage = new LeaveRequest("cancel", item.rowNumber);
        googleSheetsApi.sendRequest(cancelPackage).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    processBatchCancellation(items, index + 1);
                }, 500);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                processBatchCancellation(items, index + 1);
            }
        });
    }

    private void executeCloudCancellation(final int rowNumber, final int itemPosition) {
        tvProgressMessage.setText(R.string.msg_revoking_refunding);
        progressOverlay.setVisibility(View.VISIBLE);

        LeaveRequest cancelPackage = new LeaveRequest("cancel", rowNumber);

        googleSheetsApi.sendRequest(cancelPackage).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressOverlay.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_request_revoked), Toast.LENGTH_LONG).show();
                    historyList.remove(itemPosition);
                    listAdapter.notifyItemRemoved(itemPosition);
                    listAdapter.notifyItemRangeChanged(itemPosition, historyList.size());

                    // 💡 Automatically go back if all requests are revoked
                    if (historyList.isEmpty()) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> finish(), 1500);
                    }
                } else {
                    Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_server_rejected_cancellation, response.code()), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                progressOverlay.setVisibility(View.GONE);
                Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }
}