package com.callblocker;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ScheduleActivity extends AppCompatActivity {

    private Switch switchSchedule;
    private Button btnStartTime, btnEndTime;
    private TextView tvScheduleInfo;
    private SharedPreferences prefs;
    private String startTime, endTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        prefs = getSharedPreferences("CallBlockerPrefs", MODE_PRIVATE);

        switchSchedule = findViewById(R.id.switchSchedule);
        btnStartTime = findViewById(R.id.btnStartTime);
        btnEndTime = findViewById(R.id.btnEndTime);
        tvScheduleInfo = findViewById(R.id.tvScheduleInfo);

        startTime = prefs.getString("scheduleStart", "09:00");
        endTime = prefs.getString("scheduleEnd", "18:00");
        boolean enabled = prefs.getBoolean("scheduleEnabled", false);

        switchSchedule.setChecked(enabled);
        updateButtons();
        updateInfo();

        switchSchedule.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean("scheduleEnabled", checked).apply();
            updateInfo();
        });

        btnStartTime.setOnClickListener(v -> {
            int hour = Integer.parseInt(startTime.split(":")[0]);
            int min = Integer.parseInt(startTime.split(":")[1]);
            new TimePickerDialog(this, (view, h, m) -> {
                startTime = String.format("%02d:%02d", h, m);
                prefs.edit().putString("scheduleStart", startTime).apply();
                updateButtons();
                updateInfo();
            }, hour, min, true).show();
        });

        btnEndTime.setOnClickListener(v -> {
            int hour = Integer.parseInt(endTime.split(":")[0]);
            int min = Integer.parseInt(endTime.split(":")[1]);
            new TimePickerDialog(this, (view, h, m) -> {
                endTime = String.format("%02d:%02d", h, m);
                prefs.edit().putString("scheduleEnd", endTime).apply();
                updateButtons();
                updateInfo();
            }, hour, min, true).show();
        });
    }

    private void updateButtons() {
        btnStartTime.setText("Baslangic: " + startTime);
        btnEndTime.setText("Bitis: " + endTime);
    }

    private void updateInfo() {
        boolean enabled = switchSchedule.isChecked();
        if (enabled) {
            tvScheduleInfo.setText("Aramalar sadece " + startTime + " - " + endTime + " arasinda engellenecek");
        } else {
            tvScheduleInfo.setText("Saat kisitlamasi yok - her zaman aktif");
        }
    }
}
