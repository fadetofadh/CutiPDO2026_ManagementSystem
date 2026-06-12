package com.test.cutipdo2026;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
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

public class CancelPortalActivity extends AppCompatActivity {

    private ListView lvCancelHistory;
    private ProgressBar pbCancelLoader;
    private FrameLayout progressOverlay;
    private TextView tvProgressMessage;
    private GoogleSheetsApi googleSheetsApi;
    private List<LeaveRequestData> historyList = new ArrayList<>();
    private CancelAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cancel_portal);

        lvCancelHistory = findViewById(R.id.lvCancelHistory);
        pbCancelLoader = findViewById(R.id.pbCancelLoader);
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

        loadHistoryLogQueue();

        lvCancelHistory.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                LeaveRequestData selectedItem = historyList.get(position);
                showCancelConfirmationDialog(selectedItem, position);
                return true;
            }
        });
    }

    private void loadHistoryLogQueue() {
        // Force spinner visible on fresh boot request kickoff
        pbCancelLoader.setVisibility(View.VISIBLE);
        lvCancelHistory.setVisibility(View.GONE);

        // Crucial Fix: We use .getAllRequests("all") to match your custom Retrofit interface mapping!
        googleSheetsApi.getAllRequests("all").enqueue(new Callback<List<LeaveRequestData>>() {
            @Override
            public void onResponse(Call<List<LeaveRequestData>> call, Response<List<LeaveRequestData>> response) {
                // 🛡️ Safety Switch: Kill loader instantly before handling any data checks
                pbCancelLoader.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    historyList = response.body();

                    if (historyList.isEmpty()) {
                        Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_no_approved_requests), Toast.LENGTH_LONG).show();
                    } else {
                        lvCancelHistory.setVisibility(View.VISIBLE);
                        listAdapter = new CancelAdapter(CancelPortalActivity.this, historyList);
                        lvCancelHistory.setAdapter(listAdapter);
                    }
                } else {
                    Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_server_error_code, response.code()), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<LeaveRequestData>> call, Throwable t) {
                // 🛡️ Safety Switch: Clear loader on absolute hardware or script network crash
                pbCancelLoader.setVisibility(View.GONE);
                Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_network_failure, t.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showCancelConfirmationDialog(final LeaveRequestData request, final int itemPosition) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_revoke_title)
                .setMessage(getString(R.string.dialog_revoke_message, request.employeeName, request.totalDays))
                .setPositiveButton(R.string.btn_yes_revoke_refund, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        executeCloudCancellation(request.rowNumber, itemPosition);
                    }
                })
                .setNegativeButton(R.string.btn_no, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void executeCloudCancellation(final int rowNumber, final int itemPosition) {
        tvProgressMessage.setText(R.string.msg_revoking_refunding);
        progressOverlay.setVisibility(View.VISIBLE);

        LeaveRequest cancelPackage = new LeaveRequest("cancel", rowNumber);

        googleSheetsApi.sendRequest(cancelPackage).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                progressOverlay.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_request_revoked), Toast.LENGTH_LONG).show();
                    historyList.remove(itemPosition);
                    listAdapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_server_rejected_cancellation, response.code()), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                progressOverlay.setVisibility(View.GONE);
                Toast.makeText(CancelPortalActivity.this, getString(R.string.toast_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class CancelAdapter extends ArrayAdapter<LeaveRequestData> {
        public CancelAdapter(android.content.Context context, List<LeaveRequestData> items) {
            super(context, 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_cancel_request, parent, false);
            }

            LeaveRequestData item = getItem(position);
            if (item != null) {
                TextView tvTitle = convertView.findViewById(R.id.tvCancelRowTitle);
                TextView tvSubtitle = convertView.findViewById(R.id.tvCancelRowSubtitle);

                tvTitle.setText(item.employeeName + " - " + item.leaveType);
                tvSubtitle.setText("Dates: " + item.targetDate + " (" + item.totalDays + " Days)");
            }
            return convertView;
        }
    }
}