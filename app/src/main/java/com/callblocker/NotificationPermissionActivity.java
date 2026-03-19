package com.callblocker;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class NotificationPermissionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_permission);

        Button btnGrant = findViewById(R.id.btnGrantNotifPermission);
        Button btnSkip = findViewById(R.id.btnSkipNotifPermission);
        TextView tvStatus = findViewById(R.id.tvNotifPermStatus);

        updateStatus(tvStatus);

        btnGrant.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        btnSkip.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView tvStatus = findViewById(R.id.tvNotifPermStatus);
        updateStatus(tvStatus);
        if (isNotificationListenerEnabled()) {
            SharedPreferences prefs = getSharedPreferences("CallBlockerPrefs", MODE_PRIVATE);
            prefs.edit().putBoolean("notifListenerGranted", true).apply();
            finish();
        }
    }

    private void updateStatus(TextView tv) {
        if (isNotificationListenerEnabled()) {
            tv.setText("WhatsApp bildirim erisimi verildi");
            tv.setTextColor(getColor(R.color.colorActive));
        } else {
            tv.setText("WhatsApp bildirim erisimi gerekli");
            tv.setTextColor(getColor(R.color.colorInactive));
        }
    }

    public static boolean isNotificationListenerEnabled(android.content.Context context) {
        String flat = Settings.Secure.getString(
            context.getContentResolver(),
            "enabled_notification_listeners"
        );
        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && context.getPackageName().equals(cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        return isNotificationListenerEnabled(this);
    }
}
