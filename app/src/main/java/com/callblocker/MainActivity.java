package com.callblocker;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private Switch switchMaster;
    private Button btnManageContacts;
    private Button btnSchedule;
    private TextView tvStatus;
    private TextView tvActiveMode;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("CallBlockerPrefs", MODE_PRIVATE);

        switchMaster = findViewById(R.id.switchMaster);
        btnManageContacts = findViewById(R.id.btnManageContacts);
        btnSchedule = findViewById(R.id.btnSchedule);
        tvStatus = findViewById(R.id.tvStatus);
        tvActiveMode = findViewById(R.id.tvActiveMode);

        boolean isActive = prefs.getBoolean("isActive", false);
        switchMaster.setChecked(isActive);
        updateStatusUI(isActive);

        switchMaster.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (checkAndRequestPermissions()) {
                    startBlockerService();
                } else {
                    switchMaster.setChecked(false);
                }
            } else {
                stopBlockerService();
            }
        });

        btnManageContacts.setOnClickListener(v -> {
            Intent intent = new Intent(this, ContactManagerActivity.class);
            startActivity(intent);
        });

        btnSchedule.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScheduleActivity.class);
            startActivity(intent);
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 200);
            }
        }

        Button btnWhatsAppPerm = findViewById(R.id.btnWhatsAppPermission);
        if (btnWhatsAppPerm != null) {
            updateWhatsAppPermButton(btnWhatsAppPerm);
            btnWhatsAppPerm.setOnClickListener(v -> {
                Intent intent = new Intent(this, NotificationPermissionActivity.class);
                startActivity(intent);
            });
        }

        Button btnXiaomi = findViewById(R.id.btnXiaomiSetup);
        if (btnXiaomi != null) {
            btnXiaomi.setOnClickListener(v -> {
                Intent intent = new Intent(this, XiaomiSetupActivity.class);
                startActivity(intent);
            });
        }

        boolean xiaomiSetupDone = prefs.getBoolean("xiaomiSetupDone", false);
        if (!xiaomiSetupDone && isXiaomiDevice()) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Xiaomi Cihazı Tespit Edildi")
                .setMessage("Uygulamanın düzgün çalışması için birkaç Xiaomi ayarı yapman gerekiyor. Şimdi yapalım mı?")
                .setPositiveButton("Evet, Ayarları Yap", (d, w) -> {
                    Intent intent = new Intent(this, XiaomiSetupActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Sonra", null)
                .show();
        }
    }

    private boolean isXiaomiDevice() {
        return android.os.Build.MANUFACTURER.toLowerCase().contains("xiaomi")
            || android.os.Build.MANUFACTURER.toLowerCase().contains("redmi")
            || android.os.Build.BRAND.toLowerCase().contains("xiaomi")
            || android.os.Build.BRAND.toLowerCase().contains("redmi")
            || android.os.Build.BRAND.toLowerCase().contains("poco");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusUI(prefs.getBoolean("isActive", false));
        updateActiveModeText();
        Button btnWhatsAppPerm = findViewById(R.id.btnWhatsAppPermission);
        if (btnWhatsAppPerm != null) updateWhatsAppPermButton(btnWhatsAppPerm);
    }

    private void updateActiveModeText() {
        boolean blockAll = prefs.getBoolean("blockAll", false);
        boolean scheduleEnabled = prefs.getBoolean("scheduleEnabled", false);
        StringBuilder sb = new StringBuilder("Mod: ");
        if (blockAll) {
            sb.append("Tüm aramalar engelleniyor");
        } else {
            int contactCount = prefs.getInt("contactCount", 0);
            sb.append(contactCount).append(" kişi listede");
        }
        if (scheduleEnabled) {
            String startTime = prefs.getString("scheduleStart", "09:00");
            String endTime = prefs.getString("scheduleEnd", "17:00");
            sb.append(" | ").append(startTime).append(" - ").append(endTime);
        }
        tvActiveMode.setText(sb.toString());
    }

    private void updateStatusUI(boolean isActive) {
        if (isActive) {
            tvStatus.setText("Aktif - Aramalar engelleniyor");
            tvStatus.setTextColor(getColor(R.color.colorActive));
        } else {
            tvStatus.setText("Pasif - Aramalar normal");
            tvStatus.setTextColor(getColor(R.color.colorInactive));
        }
    }

    private void updateWhatsAppPermButton(Button btn) {
        boolean granted = NotificationPermissionActivity.isNotificationListenerEnabled(this);
        if (granted) {
            btn.setText("WhatsApp Destegi Aktif");
            btn.setBackgroundResource(R.drawable.btn_success);
        } else {
            btn.setText("WhatsApp Izni Ver");
            btn.setBackgroundResource(R.drawable.btn_warning);
        }
    }

    private void startBlockerService() {
        prefs.edit().putBoolean("isActive", true).apply();
        Intent serviceIntent = new Intent(this, CallBlockerService.class);
        serviceIntent.setAction("START");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        updateStatusUI(true);
        updateActiveModeText();
        Toast.makeText(this, "Cagri engelleyici aktif!", Toast.LENGTH_SHORT).show();
    }

    private void stopBlockerService() {
        prefs.edit().putBoolean("isActive", false).apply();
        Intent serviceIntent = new Intent(this, CallBlockerService.class);
        serviceIntent.setAction("STOP");
        startService(serviceIntent);
        updateStatusUI(false);
        Toast.makeText(this, "Cagri engelleyici durduruldu.", Toast.LENGTH_SHORT).show();
    }

    private boolean checkAndRequestPermissions() {
        List<String> needed = new ArrayList<>();
        String[] required = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        };
        for (String perm : required) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                switchMaster.setChecked(true);
                startBlockerService();
            } else {
                Toast.makeText(this, "Tum izinler gerekli!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            }
        }
    }
}
