package com.test.cutipdo2026;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class DeveloperOptionsActivity extends AppCompatActivity {

    private CheckBox cbUseLocalNotif;
    private TextInputLayout tilLocalNumber;
    private TextInputEditText etLocalNumber;
    private Button btnSaveDevOpts;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer_options);

        cbUseLocalNotif = findViewById(R.id.cbUseLocalNotif);
        tilLocalNumber = findViewById(R.id.tilLocalNumber);
        etLocalNumber = findViewById(R.id.etLocalNumber);
        btnSaveDevOpts = findViewById(R.id.btnSaveDevOpts);

        prefs = getSharedPreferences("DEV_OPTS", MODE_PRIVATE);

        // Load current values
        boolean useLocal = prefs.getBoolean("USE_LOCAL_NOTIF", false);
        String localNum = prefs.getString("LOCAL_NOTIF_NUMBER", "");

        cbUseLocalNotif.setChecked(useLocal);
        etLocalNumber.setText(localNum);
        tilLocalNumber.setEnabled(useLocal);

        cbUseLocalNotif.setOnCheckedChangeListener((buttonView, isChecked) -> tilLocalNumber.setEnabled(isChecked));

        btnSaveDevOpts.setOnClickListener(v -> {
            String num = etLocalNumber.getText().toString().trim();
            prefs.edit()
                    .putBoolean("USE_LOCAL_NOTIF", cbUseLocalNotif.isChecked())
                    .putString("LOCAL_NOTIF_NUMBER", num)
                    .apply();
            Toast.makeText(this, R.string.msg_settings_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
