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

        lvPendingRequests.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                LeaveRequestData selectedItem = pendingList.get(position);
                showDecisionDialog(selectedItem, position);
            }
        });
    }

    private void fetchPendingQueue() {
        pbSupervisorLoader.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);
        lvPendingRequests.setVisibility(View.GONE);

        googleSheetsApi.getPendingRequests("pending").enqueue(new Callback<List<LeaveRequestData>>() {
            @Override
            public void onResponse(Call<List<LeaveRequestData>> call, Response<List<LeaveRequestData>> response) {
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
                    Toast.makeText(SupervisorActivity.this, "Server error code: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<LeaveRequestData>> call, Throwable t) {
                pbSupervisorLoader.setVisibility(View.GONE);
                tvNoData.setVisibility(View.VISIBLE);
                Toast.makeText(SupervisorActivity.this, "Failed to load list queue: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showDecisionDialog(final LeaveRequestData request, final int itemPosition) {
        new AlertDialog.Builder(SupervisorActivity.this)
                .setTitle("Review Leave Request")
                .setMessage("Employee: " + request.employeeName +
                        "\nType: " + request.leaveType +
                        "\nDuration: " + request.totalDays + " Day(s)" +
                        "\nDates: " + request.getFormattedDate())
                .setPositiveButton("APPROVE ✅", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        executeCloudAction(request.rowNumber, itemPosition, "approve");
                    }
                })
                .setNegativeButton("DECLINE ❌", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        executeCloudAction(request.rowNumber, itemPosition, "decline");
                    }
                })
                .setNeutralButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void executeCloudAction(final int rowNumber, final int itemPosition, final String action) {
        String processingMessage = action.equals("approve") ? "Approving request..." : "Declining request...";
        tvProgressMessage.setText(processingMessage);
        progressOverlay.setVisibility(View.VISIBLE);

        LeaveRequest decisionPackage = new LeaveRequest(action, rowNumber);

        googleSheetsApi.sendRequest(decisionPackage).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                progressOverlay.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    String successMessage = action.equals("approve")
                            ? "✅ Row " + rowNumber + " Approved & Calendar Logged!"
                            : "❌ Row " + rowNumber + " Successfully Declined.";

                    Toast.makeText(SupervisorActivity.this, successMessage, Toast.LENGTH_LONG).show();

                    pendingList.remove(itemPosition);
                    listAdapter.notifyDataSetChanged();

                    if (pendingList.isEmpty()) {
                        tvNoData.setVisibility(View.VISIBLE);
                        lvPendingRequests.setVisibility(View.GONE);
                    }
                } else {
                    Toast.makeText(SupervisorActivity.this, "Server rejected action: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                progressOverlay.setVisibility(View.GONE);
                Toast.makeText(SupervisorActivity.this, "Sync Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class SupervisorAdapter extends ArrayAdapter<LeaveRequestData> {
        public SupervisorAdapter(android.content.Context context, List<LeaveRequestData> items) {
            super(context, 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_supervisor_request, parent, false);
            }

            LeaveRequestData item = getItem(position);
            if (item != null) {
                TextView tvTitle = convertView.findViewById(R.id.tvSupervisorRowTitle);
                TextView tvSubtitle = convertView.findViewById(R.id.tvSupervisorRowSubtitle);

                tvTitle.setText(item.employeeName + " - " + item.leaveType);
                tvSubtitle.setText("Dates: " + item.getFormattedDate() + " (" + item.totalDays + " Days)");
            }
            return convertView;
        }
    }
}