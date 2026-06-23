package com.test.cutipdo2026;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SupervisorActivity extends AppCompatActivity {

    private RecyclerView rvPendingRequests;
    private TextView tvNoData, tvClearSelectionSpv, tvSelectAllSpv;
    private ProgressBar pbSupervisorLoader;
    private FrameLayout progressOverlay;
    private View layoutBatchActionsSpv;
    private TextView tvProgressMessage;
    private Button btnBatchApproveSpv, btnBatchDeclineSpv;
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
        tvSelectAllSpv = findViewById(R.id.tvSelectAllSpv);
        pbSupervisorLoader = findViewById(R.id.pbSupervisorLoader);
        progressOverlay = findViewById(R.id.progressOverlay);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        layoutBatchActionsSpv = findViewById(R.id.layoutBatchActionsSpv);
        btnBatchApproveSpv = findViewById(R.id.btnBatchApproveSpv);
        btnBatchDeclineSpv = findViewById(R.id.btnBatchDeclineSpv);
        swipeRefreshSupervisor = findViewById(R.id.swipeRefreshSupervisor);

        swipeRefreshSupervisor.setOnRefreshListener(this::fetchPendingQueue);

        googleSheetsApi = RetrofitClient.getApi(this);

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
                        // 📉 SORTING: Show newest requests at the top for the Supervisor
                        ListSorter.sortNewestFirst(pendingList);

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
                        listAdapter.setSwipeLocked(true); 
                        rvPendingRequests.setAdapter(listAdapter);

                        tvSelectAllSpv.setOnClickListener(v -> {
                            listAdapter.selectAll();
                            updateClearButtonVisibility();
                        });

                        tvClearSelectionSpv.setOnClickListener(v -> {
                            listAdapter.clearAllMarks();
                            updateClearButtonVisibility();
                        });

                        btnBatchApproveSpv.setOnClickListener(v -> {
                            List<LeaveRequestData> markedItems = new ArrayList<>();
                            for (LeaveRequestData req : pendingList) {
                                if (req.isMarked) markedItems.add(req);
                            }

                            if (!markedItems.isEmpty()) {
                                processBatchAction(markedItems, 0, "approve");
                            }
                        });

                        btnBatchDeclineSpv.setOnClickListener(v -> {
                            List<LeaveRequestData> markedItems = new ArrayList<>();
                            for (LeaveRequestData req : pendingList) {
                                if (req.isMarked) markedItems.add(req);
                            }

                            if (!markedItems.isEmpty()) {
                                processBatchAction(markedItems, 0, "decline");
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

                            @Override
                            public void onItemRangeInserted(int positionStart, int itemCount) {
                                updateClearButtonVisibility();
                            }

                            @Override
                            public void onItemRangeRemoved(int positionStart, int itemCount) {
                                updateClearButtonVisibility();
                            }
                        });

                        new ItemTouchHelper(new UniversalSwipeCallback(new UniversalSwipeCallback.OnSwipeListener() {
                            @Override
                            public void onApprove(int position) {
                                listAdapter.onApproveQuick(position);
                            }

                            @Override
                            public void onDecline(int position) {
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
                    updateClearButtonVisibility();
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
        boolean hasItems = !pendingList.isEmpty();
        tvSelectAllSpv.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        tvClearSelectionSpv.setVisibility(markedCount > 0 ? View.VISIBLE : View.GONE);
        layoutBatchActionsSpv.setVisibility(markedCount > 0 ? View.VISIBLE : View.GONE);
        btnBatchApproveSpv.setText(getString(R.string.btn_approve_selected_count, markedCount));
        btnBatchDeclineSpv.setText(getString(R.string.btn_decline_selected_count, markedCount));
    }

    private void processBatchAction(final List<LeaveRequestData> items, final int index, final String action) {
        if (index >= items.size()) {
            progressOverlay.setVisibility(View.GONE);
            swipeRefreshSupervisor.setEnabled(true);
            String toastMsg = action.equals("approve") ? getString(R.string.toast_batch_approve_complete) : getString(R.string.toast_batch_decline_complete);
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
            fetchPendingQueue();
            return;
        }

        LeaveRequestData item = items.get(index);
        String progressMsg = action.equals("approve") ? getString(R.string.msg_batch_approving, (index + 1), items.size()) : getString(R.string.msg_batch_declining, (index + 1), items.size());
        tvProgressMessage.setText(progressMsg);
        progressOverlay.setVisibility(View.VISIBLE);
        swipeRefreshSupervisor.setEnabled(false);

        LeaveRequest decisionPackage = new LeaveRequest(action, item.rowNumber);
        googleSheetsApi.sendRequest(decisionPackage).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> processBatchAction(items, index + 1, action), 500);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                processBatchAction(items, index + 1, action);
            }
        });
    }

    private void executeCloudAction(final int rowNumber, final int itemPosition, final String action) {
        String processingMessage = Objects.equals(action, "approve") ? getString(R.string.msg_approving_request) : getString(R.string.msg_declining_request);
        tvProgressMessage.setText(processingMessage);
        progressOverlay.setVisibility(View.VISIBLE);
        swipeRefreshSupervisor.setEnabled(false);

        LeaveRequest decisionPackage = new LeaveRequest(action, rowNumber);

        googleSheetsApi.sendRequest(decisionPackage).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressOverlay.setVisibility(View.GONE);
                swipeRefreshSupervisor.setEnabled(true);
                if (response.isSuccessful()) {
                    String successMessage = Objects.equals(action, "approve")
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
                swipeRefreshSupervisor.setEnabled(true);
                Toast.makeText(SupervisorActivity.this, getString(R.string.toast_sync_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }
}