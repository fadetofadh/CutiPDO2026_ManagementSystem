package com.test.cutipdo2026;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class SpvPortalActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spv_portal);

        Button btnAddRequestSpv = findViewById(R.id.btnAddRequestSpv);
        Button btnCancelRequestSpv = findViewById(R.id.btnCancelRequestSpv);
        Button btnApproveRequest = findViewById(R.id.btnApproveRequest);
        Button btnBackToLoginSpv = findViewById(R.id.btnBackToLoginSpv);

        @SuppressWarnings("unchecked")
        ArrayList<EmployeeBalance> balanceList = (ArrayList<EmployeeBalance>) getIntent().getSerializableExtra("PRE_FETCHED_BALANCES");
        @SuppressWarnings("unchecked")
        ArrayList<String> nameList = (ArrayList<String>) getIntent().getSerializableExtra("PRE_FETCHED_NAMES");
        @SuppressWarnings("unchecked")
        ArrayList<LeaveRequestData> approvedList = (ArrayList<LeaveRequestData>) getIntent().getSerializableExtra("PRE_FETCHED_APPROVED");
        String filterClass = getIntent().getStringExtra("FILTER_CLASS");

        btnAddRequestSpv.setOnClickListener(v -> {
            Intent intent = new Intent(this, SpvRequestActivity.class);
            intent.putExtra("PRE_FETCHED_BALANCES", balanceList);
            intent.putExtra("PRE_FETCHED_NAMES", nameList);
            intent.putExtra("PRE_FETCHED_APPROVED", approvedList);
            intent.putExtra("FILTER_CLASS", filterClass);
            startActivity(intent);
        });

        btnCancelRequestSpv.setOnClickListener(v -> {
            Intent intent = new Intent(this, CancelPortalActivity.class);
            intent.putExtra("FILTER_CLASS", filterClass); // filterClass is "all" for SPV
            startActivity(intent);
        });

        btnApproveRequest.setOnClickListener(v -> {
            Intent intent = new Intent(this, SupervisorActivity.class);
            intent.putExtra("FILTER_CLASS", filterClass);
            startActivity(intent);
        });

        btnBackToLoginSpv.setOnClickListener(v -> finish());
    }
}
