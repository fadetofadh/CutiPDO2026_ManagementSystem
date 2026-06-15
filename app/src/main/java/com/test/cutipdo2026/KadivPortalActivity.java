package com.test.cutipdo2026;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class KadivPortalActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kadiv_portal);

        Button btnAddRequest = findViewById(R.id.btnAddRequest);
        Button btnCancelRequest = findViewById(R.id.btnCancelRequest);
        Button btnBackToLogin = findViewById(R.id.btnBackToLogin);

        @SuppressWarnings("unchecked")
        ArrayList<EmployeeBalance> balanceList = (ArrayList<EmployeeBalance>) getIntent().getSerializableExtra("PRE_FETCHED_BALANCES");
        @SuppressWarnings("unchecked")
        ArrayList<String> nameList = (ArrayList<String>) getIntent().getSerializableExtra("PRE_FETCHED_NAMES");
        String filterClass = getIntent().getStringExtra("FILTER_CLASS");

        btnAddRequest.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("PRE_FETCHED_BALANCES", balanceList);
            intent.putExtra("PRE_FETCHED_NAMES", nameList);
            startActivity(intent);
        });

        btnCancelRequest.setOnClickListener(v -> {
            Intent intent = new Intent(this, CancelPortalActivity.class);
            intent.putExtra("FILTER_CLASS", filterClass);
            startActivity(intent);
        });

        btnBackToLogin.setOnClickListener(v -> finish());
    }
}
