package com.callblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CallReceiver extends BroadcastReceiver {

    private static final String TAG = "CallBlocker";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (!TelephonyManager.EXTRA_STATE_RINGING.equals(state)) return;

        SharedPreferences prefs = context.getSharedPreferences("CallBlockerPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("isActive", false)) return;

        if (prefs.getBoolean("scheduleEnabled", false)) {
            if (!isWithinSchedule(prefs)) return;
        }

        String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        if (incomingNumber == null) return;

        boolean blockAll = prefs.getBoolean("blockAll", false);
        boolean shouldBlock = false;
        String customMessage = prefs.getString("defaultMessage", "Su an mesgulum, sonra arayacagim.");

        if (blockAll) {
            shouldBlock = true;
        } else {
            try {
                String contactsJson = prefs.getString("contactList", "[]");
                JSONArray contacts = new JSONArray(contactsJson);
                for (int i = 0; i < contacts.length(); i++) {
                    JSONObject contact = contacts.getJSONObject(i);
                    String savedNumber = normalizeNumber(contact.getString("number"));
                    String callerNumber = normalizeNumber(incomingNumber);
                    if (savedNumber.equals(callerNumber) || callerNumber.endsWith(savedNumber) || savedNumber.endsWith(callerNumber)) {
                        shouldBlock = true;
                        if (contact.has("customMessage") && !contact.getString("customMessage").isEmpty()) {
                            customMessage = contact.getString("customMessage");
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Kisi listesi hatasi: " + e.getMessage());
            }
        }

        if (shouldBlock) {
            rejectCall(context);
            sendAutoSms(context, incomingNumber, customMessage);
            logBlockedCall(context, incomingNumber, prefs);
        }
    }

    private void rejectCall(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (tm != null) {
                    tm.endCall();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Arama reddetme hatasi: " + e.getMessage());
        }
    }

    private void sendAutoSms(Context context, String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        } catch (Exception e) {
            Log.e(TAG, "SMS hatasi: " + e.getMessage());
        }
    }

    private boolean isWithinSchedule(SharedPreferences prefs) {
        try {
            String startStr = prefs.getString("scheduleStart", "09:00");
            String endStr = prefs.getString("scheduleEnd", "17:00");
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date now = sdf.parse(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            Date start = sdf.parse(startStr);
            Date end = sdf.parse(endStr);
            if (start == null || end == null || now == null) return true;
            return !now.before(start) && !now.after(end);
        } catch (Exception e) {
            return true;
        }
    }

    private void logBlockedCall(Context context, String number, SharedPreferences prefs) {
        try {
            String logsJson = prefs.getString("callLogs", "[]");
            JSONArray logs = new JSONArray(logsJson);
            JSONObject log = new JSONObject();
            log.put("number", number);
            log.put("name", getContactName(context, number));
            log.put("time", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()));
            logs.put(log);
            while (logs.length() > 50) logs.remove(0);
            prefs.edit().putString("callLogs", logs.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Log hatasi: " + e.getMessage());
        }
    }

    private String getContactName(Context context, String phoneNumber) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                cursor.close();
                return name;
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            // ignore
        }
        return phoneNumber;
    }

    private String normalizeNumber(String number) {
        return number.replaceAll("[^0-9]", "");
    }
}
