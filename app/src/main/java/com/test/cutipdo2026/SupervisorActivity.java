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

public class SupervisorActivity extends AppCompatActivity {

    private RecyclerView rvPendingRequests;
    private TextView tvNoData, tvClearSelectionSpv;
    private ProgressBar pbSupervisorLoader;
    private FrameLayout progressOverlay;
    private TextView tvProgressMessage;
    private Button btnBatchActionSpv;
    private SwipeRefreshLayout swipeRefreshSupervisor;
    private GoogleSheetsApi googleSheetsApi;
    private List<LeaveRequestData> pendingList = new ArrayList<>();
    private UniversalRequestAdapter listAdapter;
    private String filterClass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supervisor);

        filterClass = getIntent().getStringExtra("FILTER_CLASS");

        rvPendingRequests = findViewById(R.id.rvPendingRequests);
        tvNoData = findViewById(R.id.tvNoData);
        tvClearSelectionSpv = findViewById(R.id.tvClearSelectionSpv);
        pbSupervisorLoader = findViewById(R.id.pbSupervisorLoader);
        progressOverlay = findViewById(R.id.progressOverlay);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        btnBatchActionSpv = findViewById(R.id.btnBatchActionSpv);
        swipeRefreshSupervisor = findViewById(R.id.swipeRefreshSupervisor);

        swipeRefreshSupervisor.setOnRefreshListener(this::fetchPendingQueue);

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

        rvPendingRequests.setLayoutManager(new LinearLayoutManager(this));

        fetchPendingQueue();
    }

    private void fetchPendingQueue() {
        if (!swipeRefreshSupervisor.isRefreshing()) {
            pbSupervisorLoader.setVisibility(View.VISIBLE);
        }
        tvNoData.setVisibility(View.GONE);
        rvPendingRequests.setVisibility(View.GONE);

        googleSheetsApi.getPendingRequests("pending", filterClass).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<LeaveRequestData>> call, @NonNull Response<List<LeaveRequestData>> response) {
                pbSupervisorLoader.setVisibility(View.GONE);
                swipeRefreshSupervisor.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    pendingList = response.body();

                    if (pendingList.isEmpty()) {
                        tvNoData.setVisibility(View.VISIBLE);
                        rvPendingRequests.setVisibility(View.GONE);
                    } else {
                        tvNoData.setVisibility(View.GONE);
                        rvPendingRequests.setVisibility(View.VISIBLE);

                        listAdapter = new UniversalRequestAdapter(pendingList, new UniversalRequestAdapter.OnItemActionListener() {
                            @Override
                            public void onEditSelected(LeaveRequestData request, int position) {
                                Toast.makeText(SupervisorActivity.this, getString(R.string.toast_edit_coming_soon), Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onDeleteSelected(int position) {
                                executeCloudAction(pendingList.get(position).rowNumber, position, "decline");
                            }

                            @Override
                            public void onApproveQuick(int position) {
                                executeCloudAction(pendingList.get(position).rowNumber, position, "approve");
                            }
                        });
                        listAdapter.setSwipeLocked(true); // 🔒 Use quick-swipe (full row) instead of reveal drawer
                        rvPendingRequests.setAdapter(listAdapter);

                        tvClearSelectionSpv.setOnClickListener(v -> {
                            listAdapter.clearAllMarks();
                            updateClearButtonVisibility();
                        });

                        btnBatchActionSpv.setOnClickListener(v -> {
                            List<LeaveRequestData> markedItems = new ArrayList<>();
                            for (LeaveRequestData req : pendingList) {
                                if (req.isMarked) markedItems.add(req);
                            }

                            if (!markedItems.isEmpty()) {
                                processBatchApproval(markedItems, 0);
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
                                // Direct Approval
                                listAdapter.onApproveQuick(position);
                            }

                            @Override
                            public void onDecline(int position) {
                                // Direct Decline
                                if (position >= 0 && position < pendingList.size()) {
                                    executeCloudAction(pendingList.get(position).rowNumber, position, "decline");
                                }
                            }

                            @Override
                            public boolean canSwipe(int position) {
                                if (position >= 0 && position < pendingList.size()) {
                                    return !pendingList.get(position).isMarked;
                                }
                                return true;
                            }
                        }, SupervisorActivity.this, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT)).attachToRecyclerView(rvPendingRequests);
                    }
                } else {
                    Toast.makeText(SupervisorActivity.this, getString(R.string.toast_server_error_code_supervisor, response.code()), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<LeaveRequestData>> call, @NonNull Throwable t) {
                pbSupervisorLoader.setVisibility(View.GONE);
                swipeRefreshSupervisor.setRefreshing(false);
                tvNoData.setVisibility(View.VISIBLE);
                Toast.makeText(SupervisorActivity.this, getString(R.string.toast_load_list_failed, t.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateClearButtonVisibility() {
        int markedCount = 0;
        for (LeaveRequestData req : pendingList) {
            if (req.isMarked) {
                markedCount++;
            }
        }
        tvClearSelectionSpv.setVisibility(markedCount > 0 ? View.VISIBLE : View.GONE);
        btnBatchActionSpv.setVisibility(markedCount > 0 ? View.VISIBLE : View.GONE);
        btnBatchActionSpv.setText(getString(R.string.btn_approve_selected_count, markedCount));
    }

    private void processBatchApproval(final List<LeaveRequestData> items, final int index) {
        if (index >= items.size()) {
            progressOverlay.setVisibility(View.GONE);
            swipeRefreshSupervisor.setEnabled(true); // 🔓 Re-enable refresh
            Toast.makeText(this, getString(R.string.toast_batch_approve_complete), Toast.LENGTH_SHORT).show();
            fetchPendingQueue(); // Refresh list
            return;
        }

        LeaveRequestData item = items.get(index);
        tvProgressMessage.setText(getString(R.string.msg_batch_approving, (index + 1), items.size()));
        progressOverlay.setVisibility(View.VISIBLE);
        swipeRefreshSupervisor.setEnabled(false); // 🔒 Disable refresh while processing

        // 💡 FIX: use "approve" action which triggers approveLeaveRequest() in script for calendar logging
        LeaveRequest decisionPackage = new LeaveRequest("approve", item.rowNumber);
        googleSheetsApi.sendRequest(decisionPackage).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                // Continue to next regardless of row success to prevent hang
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    processBatchApproval(items, index + 1);
                }, 500);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                processBatchApproval(items, index + 1);
            }
        });
    }

    private void executeCloudAction(final int rowNumber, final int itemPosition, final String action) {
        String processingMessage = action.equals("approve") ? getString(R.string.msg_approving_request) : getString(R.string.msg_declining_request);
        tvProgressMessage.setText(processingMessage);
        progressOverlay.setVisibility(View.VISIBLE);
        swipeRefreshSupervisor.setEnabled(false); // 🔒 Lock UI interaction

        LeaveRequest decisionPackage = new LeaveRequest(action, rowNumber);

        googleSheetsApi.sendRequest(decisionPackage).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressOverlay.setVisibility(View.GONE);
                swipeRefreshSupervisor.setEnabled(true); // 🔓 Unlock
                if (response.isSuccessful()) {
                    String successMessage = action.equals("approve")
                            ? getString(R.string.toast_approve_success, rowNumber)
                            : getString(R.string.toast_decline_success, rowNumber);

                    Toast.makeText(SupervisorActivity.this, successMessage, Toast.LENGTH_LONG).show();

                    pendingList.remove(itemPosition);
                    listAdapter.notifyItemRemoved(itemPosition);
                    listAdapter.notifyItemRangeChanged(itemPosition, pendingList.size());

                    if (pendingList.isEmpty()) {
                        tvNoData.setVisibility(View.VISIBLE);
                        rvPendingRequests.setVisibility(View.GONE);
                    }
                } else {
                    Toast.makeText(SupervisorActivity.this, getString(R.string.toast_server_rejected_action, response.code()), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                progressOverlay.setVisibility(View.GONE);
                swipeRefreshSupervisor.setEnabled(true); // 🔓 Unlock
                Toast.makeText(SupervisorActivity.this, getString(R.string.toast_sync_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }
}