package com.test.cutipdo2026;

import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Button;

public class MaintenanceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maintenance);

        TextView tvTitle = findViewById(R.id.tvMaintenanceTitle);
        TextView tvMessage = findViewById(R.id.tvMaintenanceMessage);
        Button btnExit = findViewById(R.id.btnExitMaintenance);

        String title = getIntent().getStringExtra("MAINTENANCE_TITLE");
        String message = getIntent().getStringExtra("MAINTENANCE_MESSAGE");

        if (title != null && !title.isEmpty()) {
            tvTitle.setText(title);
        }

        if (message != null && !message.isEmpty()) {
            tvMessage.setText(message);
        } else {
            tvMessage.setText(R.string.msg_default_maintenance);
        }

        btnExit.setOnClickListener(v -> {
            finishAffinity(); // Close all activities and exit app
            System.exit(0);
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });
    }
}
