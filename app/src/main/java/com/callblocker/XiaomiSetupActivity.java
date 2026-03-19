package com.callblocker;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class XiaomiSetupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xiaomi_setup);

        Button btnAutoStart = findViewById(R.id.btnAutoStart);
        Button btnBattery = findViewById(R.id.btnBattery);
        Button btnDone = findViewById(R.id.btnSetupDone);

        btnAutoStart.setOnClickListener(v -> openAutoStart());
        btnBattery.setOnClickListener(v -> openBatterySettings());

        btnDone.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("CallBlockerPrefs", MODE_PRIVATE);
            prefs.edit().putBoolean("xiaomiSetupDone", true).apply();
            Toast.makeText(this, "Ayarlar tamamlandi!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void openAutoStart() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ));
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent();
                intent.setAction("miui.intent.action.OP_AUTO_START");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                startActivity(intent);
            } catch (Exception e2) {
                openAppSettings();
                Toast.makeText(this, "Ayarlar - Uygulamalar - Cagri Engelleyici - Otomatik Baslat", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HideAppsContainerManagementActivity"
            ));
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            } catch (Exception e2) {
                openAppSettings();
                Toast.makeText(this, "Ayarlar - Pil - Uygulama Pil Yonetimi - Kisitlama Yok", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }
}
