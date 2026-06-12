package com.test.cutipdo2026;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SupervisorActivity extends AppCompatActivity {

    private ListView lvPendingRequests;
    private TextView tvNoData;
    private ProgressBar pbSupervisorLoader;
    private FrameLayout progressOverlay;
    private TextView tvProgressMessage;
    private GoogleSheetsApi googleSheetsApi;
    private List<LeaveRequestData> pendingList = new ArrayList<>();
    private SupervisorAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supervisor);

        lvPendingRequests = findViewById(R.id.lvPendingRequests);
        tvNoData = findViewById(R.id.tvNoData);
        pbSupervisorLoader = findViewById(R.id.pbSupervisorLoader);
        progressOverlay = findViewById(R.id.progressOverlay);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);

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

        fetchPendingQueue();

        lvPendingRequests.setOnItemClickListener((parent, view, position, id) -> {
            LeaveRequestData selectedItem = pendingList.get(position);
            showDecisionDialog(selectedItem, position);
        });
    }

    private void fetchPendingQueue() {
        pbSupervisorLoader.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);
        lvPendingRequests.setVisibility(View.GONE);

        googleSheetsApi.getPendingRequests("pending").enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<LeaveRequestData>> call, @NonNull Response<List<LeaveRequestData>> response) {
                pbSupervisorLoader.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    pendingList = response.body();

                    if (pendingList.isEmpty()) {
                        tvNoData.setVisibility(View.VISIBLE);
                        lvPendingRequests.setVisibility(View.GONE);
                    } else {
                        tvNoData.setVisibility(View.GONE);
                        lvPendingRequests.setVisibility(View.VISIBLE);

                        listAdapter = new SupervisorAdapter(SupervisorActivity.this, pendingList);
                        lvPendingRequests.setAdapter(listAdapter);
                    }
                } else {
                    Toast.makeText(SupervisorActivity.this, getString(R.string.toast_server_error_code_supervisor, response.code()), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<LeaveRequestData>> call, @NonNull Throwable t) {
                pbSupervisorLoader.setVisibility(View.GONE);
                tvNoData.setVisibility(View.VISIBLE);
                Toast.makeText(SupervisorActivity.this, getString(R.string.toast_load_list_failed, t.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showDecisionDialog(final LeaveRequestData request, final int itemPosition) {
        new AlertDialog.Builder(SupervisorActivity.this)
                .setTitle(R.string.dialog_review_leave_title)
                .setMessage(getString(R.string.dialog_review_leave_msg_format,
                        request.employeeName,
                        request.leaveType,
                        request.totalDays,
                        request.getFormattedDate(),
                        (request.description != null ? request.description : "-")))
                .setPositiveButton(R.string.btn_approve, (dialog, which) -> executeCloudAction(request.rowNumber, itemPosition, "approve"))
                .setNegativeButton(R.string.btn_decline, (dialog, which) -> executeCloudAction(request.rowNumber, itemPosition, "decline"))
                .setNeutralButton(R.string.btn_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void executeCloudAction(final int rowNumber, final int itemPosition, final String action) {
        String processingMessage = action.equals("approve") ? getString(R.string.msg_approving_request) : getString(R.string.msg_declining_request);
        tvProgressMessage.setText(processingMessage);
        progressOverlay.setVisibility(View.VISIBLE);

        LeaveRequest decisionPackage = new LeaveRequest(action, rowNumber);

        googleSheetsApi.sendRequest(decisionPackage).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressOverlay.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    String successMessage = action.equals("approve")
                            ? getString(R.string.toast_approve_success, rowNumber)
                            : getString(R.string.toast_decline_success, rowNumber);

                    Toast.makeText(SupervisorActivity.this, successMessage, Toast.LENGTH_LONG).show();

                    pendingList.remove(itemPosition);
                    listAdapter.notifyDataSetChanged();

                    if (pendingList.isEmpty()) {
                        tvNoData.setVisibility(View.VISIBLE);
                        lvPendingRequests.setVisibility(View.GONE);
                    }
                } else {
                    Toast.makeText(SupervisorActivity.this, getString(R.string.toast_server_rejected_action, response.code()), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                progressOverlay.setVisibility(View.GONE);
                Toast.makeText(SupervisorActivity.this, getString(R.string.toast_sync_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class SupervisorAdapter extends ArrayAdapter<LeaveRequestData> {
        public SupervisorAdapter(android.content.Context context, List<LeaveRequestData> items) {
            super(context, 0, items);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View v = (convertView != null) ? convertView :
                    LayoutInflater.from(getContext()).inflate(R.layout.item_supervisor_request, parent, false);

            LeaveRequestData item = getItem(position);
            if (item != null) {
                TextView tvTitle = v.findViewById(R.id.tvSupervisorRowTitle);
                TextView tvSubtitle = v.findViewById(R.id.tvSupervisorRowSubtitle);

                tvTitle.setText(getContext().getString(R.string.item_title_format, item.employeeName, item.leaveType));
                tvSubtitle.setText(getContext().getString(R.string.item_subtitle_format, item.getFormattedDate(), item.totalDays, (item.description != null ? item.description : "-")));
            }
            return v;
        }
    }
}