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
    private TextView tvProgressMessage, tvClearSelectionCancel, tvNoHistory;
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
        tvNoHistory = findViewById(R.id.tvNoHistory);
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
        tvNoHistory.setVisibility(View.GONE);

        // 💡 Cache Buster: Ensures we get the freshest data from Google Sheets
        String cb = String.valueOf(System.currentTimeMillis());

        googleSheetsApi.getAllRequests("all", filterClass, cb).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<LeaveRequestData>> call, @NonNull Response<List<LeaveRequestData>> response) {
                pbCancelLoader.setVisibility(View.GONE);
                swipeRefreshCancel.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    List<LeaveRequestData> fullList = response.body();
                    historyList.clear();

                    // 🛡️ FILTER: Only show "Approved" requests for revocation
                    // This prevents "Pending" or "Cancelled" items from appearing in the Revoke Portal
                    for (LeaveRequestData data : fullList) {
                        if (data.status != null && data.status.equalsIgnoreCase("Approved")) {
                            historyList.add(data);
                        }
                    }

                    if (historyList.isEmpty()) {
                        tvNoHistory.setVisibility(View.VISIBLE);
                        rvCancelHistory.setVisibility(View.GONE);
                        updateClearButtonVisibility();
                    } else {
                        tvNoHistory.setVisibility(View.GONE);
                        rvCancelHistory.setVisibility(View.VISIBLE);
                        listAdapter = new UniversalRequestAdapter(historyList, new UniversalRequestAdapter.OnItemActionListener() {
                            @Override
                            public void onEditSelected(LeaveRequestData request, int position) {
                                Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_edit_coming_soon), Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onDeleteSelected(int position) {
                                LeaveRequestData item = historyList.get(position);
                                String detail = getString(R.string.dialog_confirm_revoke_message, 
                                    item.employeeName, item.getFormattedDate(), item.totalDays);
                                
                                new AlertDialog.Builder(CancelPortalActivity.this)
                                    .setTitle(R.string.dialog_confirm_revoke_title)
                                    .setMessage(detail)
                                    .setPositiveButton(R.string.btn_yes_revoke, (dialog, which) -> {
                                        executeCloudCancellation(item.rowNumber, position);
                                    })
                                    .setNegativeButton(R.string.btn_no, null)
                                    .show();
                            }

                            @Override
                            public void onApproveQuick(int position) {
                                listAdapter.notifyItemChanged(position);
                            }
                        });
                        listAdapter.setSwipeLocked(true);
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
                                return false;
                            }
                        }, CancelPortalActivity.this, 0)).attachToRecyclerView(rvCancelHistory);
                    }
                } else {
                    Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_server_error_code, response.code()), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<LeaveRequestData>> call, @NonNull Throwable t) {
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
        btnBatchCancel.setText(getString(R.string.btn_cancel_selected_count, markedCount));
    }

    private void showBatchCancelConfirmation(final List<LeaveRequestData> items) {
        StringBuilder summary = new StringBuilder();
        for (LeaveRequestData item : items) {
            summary.append("• ").append(item.employeeName).append(" (").append(item.totalDays).append(" Hari)\n");
            summary.append("  ").append(item.getFormattedDate()).append("\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_batch_cancel_title)
                .setMessage(getString(R.string.dialog_batch_cancel_msg, summary.toString()))
                .setPositiveButton(R.string.btn_yes_cancel_all, (dialog, which) -> {
                    processBatchCancellation(items, 0);
                })
                .setNegativeButton(R.string.btn_no, null)
                .show();
    }

    private void processBatchCancellation(final List<LeaveRequestData> items, final int index) {
        if (index >= items.size()) {
            progressOverlay.setVisibility(View.GONE);
            swipeRefreshCancel.setEnabled(true);
            Toast.makeText(this, getString(R.string.toast_batch_cancel_complete), Toast.LENGTH_SHORT).show();
            loadHistoryLogQueue();
            return;
        }

        LeaveRequestData item = items.get(index);
        tvProgressMessage.setText(getString(R.string.msg_batch_revoking, (index + 1), items.size()));
        progressOverlay.setVisibility(View.VISIBLE);
        swipeRefreshCancel.setEnabled(false);

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
        swipeRefreshCancel.setEnabled(false);

        LeaveRequest cancelPackage = new LeaveRequest("cancel", rowNumber);

        googleSheetsApi.sendRequest(cancelPackage).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressOverlay.setVisibility(View.GONE);
                swipeRefreshCancel.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_request_revoked), Toast.LENGTH_LONG).show();
                    historyList.remove(itemPosition);
                    listAdapter.notifyItemRemoved(itemPosition);
                    listAdapter.notifyItemRangeChanged(itemPosition, historyList.size());

                    if (historyList.isEmpty()) {
                        tvNoHistory.setVisibility(View.VISIBLE);
                        rvCancelHistory.setVisibility(View.GONE);
                    }
                    updateClearButtonVisibility();
                } else {
                    Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_server_rejected_cancellation, response.code()), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                progressOverlay.setVisibility(View.GONE);
                swipeRefreshCancel.setEnabled(true);
                Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
