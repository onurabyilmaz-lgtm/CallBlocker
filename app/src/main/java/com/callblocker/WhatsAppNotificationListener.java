package com.callblocker;

import android.app.Notification;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.SmsManager;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WhatsAppNotificationListener extends NotificationListenerService {

    private static final String TAG = "WA_Blocker";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String packageName = sbn.getPackageName();
        if (!WHATSAPP_PACKAGE.equals(packageName) && !WHATSAPP_BUSINESS_PACKAGE.equals(packageName)) return;

        SharedPreferences prefs = getSharedPreferences("CallBlockerPrefs", MODE_PRIVATE);
        if (!prefs.getBoolean("isActive", false)) return;

        if (prefs.getBoolean("scheduleEnabled", false)) {
            if (!isWithinSchedule(prefs)) return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        String title = "";
        String text = "";
        if (notification.extras != null) {
            CharSequence titleSeq = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence textSeq = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (titleSeq != null) title = titleSeq.toString();
            if (textSeq != null) text = textSeq.toString();
        }

        boolean isCallNotification = "call".equals(notification.category);
        if (!isCallNotification && !isCallRelatedText(title, text)) return;

        String callerIdentity = extractCallerFromNotification(title, text);
        if (callerIdentity == null || callerIdentity.isEmpty()) return;

        boolean blockAll = prefs.getBoolean("blockAll", false);
        String customMessage = prefs.getString("defaultMessage", "Su an mesgulum, sonra arayacagim.");
        boolean shouldBlock = false;

        if (blockAll) {
            shouldBlock = true;
        } else {
            String[] result = checkContactList(prefs, callerIdentity);
            if (result != null) {
                shouldBlock = true;
                if (result[1] != null && !result[1].isEmpty()) {
                    customMessage = result[1];
                }
            }
        }

        if (!shouldBlock) return;

        rejectWhatsAppCall(notification, sbn);

        String phoneNumber = resolvePhoneNumber(callerIdentity);
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            sendSms(phoneNumber, customMessage);
            logBlockedCall(prefs, callerIdentity, phoneNumber);
        }
    }

    private boolean isCallRelatedText(String title, String text) {
        String[] callKeywords = {
            "ariyor", "sizi ariyor", "calling", "is calling",
            "incoming call", "gelen arama", "sesli arama", "voice call",
            "video call", "goruntulu arama"
        };
        String combined = (title + " " + text).toLowerCase(Locale.getDefault());
        for (String keyword : callKeywords) {
            if (combined.contains(keyword)) return true;
        }
        return false;
    }

    private String extractCallerFromNotification(String title, String text) {
        String numberFromTitle = extractPhoneNumber(title);
        if (numberFromTitle != null) return numberFromTitle;
        String numberFromText = extractPhoneNumber(text);
        if (numberFromText != null) return numberFromText;

        String[] patterns = {
            "(.+?)\\s+sizi ariyor",
            "(.+?)\\s+ariyor",
            "(.+?)\\s+is calling",
            "(.+?)\\s+calling"
        };
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(title);
            if (m.find()) return m.group(1).trim();
            m = p.matcher(text);
            if (m.find()) return m.group(1).trim();
        }
        if (!title.isEmpty()) return title;
        return null;
    }

    private String extractPhoneNumber(String text) {
        if (text == null) return null;
        Pattern pattern = Pattern.compile("(\\+?\\d[\\d\\s\\-().]{7,}\\d)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("[\\s\\-().]", "");
        }
        return null;
    }

    private String[] checkContactList(SharedPreferences prefs, String callerIdentity) {
        try {
            String contactsJson = prefs.getString("contactList", "[]");
            JSONArray contacts = new JSONArray(contactsJson);
            String normalizedCaller = normalizeNumber(callerIdentity);
            for (int i = 0; i < contacts.length(); i++) {
                JSONObject contact = contacts.getJSONObject(i);
                String savedNumber = normalizeNumber(contact.getString("number"));
                String savedName = contact.optString("name", "").toLowerCase(Locale.getDefault());
                if (!normalizedCaller.isEmpty() && !savedNumber.isEmpty()) {
                    if (normalizedCaller.endsWith(savedNumber) || savedNumber.endsWith(normalizedCaller)) {
                        return new String[]{"match", contact.optString("customMessage", "")};
                    }
                }
                if (!savedName.isEmpty() && callerIdentity.toLowerCase(Locale.getDefault()).contains(savedName)) {
                    return new String[]{"match", contact.optString("customMessage", "")};
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Kisi listesi hatasi: " + e.getMessage());
        }
        return null;
    }

    private void rejectWhatsAppCall(Notification notification, StatusBarNotification sbn) {
        try {
            if (notification.actions == null) return;
            String[] rejectKeywords = {"reddet", "reject", "decline", "kapat", "dismiss"};
            for (Notification.Action action : notification.actions) {
                if (action.title == null) continue;
                String actionTitle = action.title.toString().toLowerCase(Locale.getDefault());
                for (String keyword : rejectKeywords) {
                    if (actionTitle.contains(keyword)) {
                        action.actionIntent.send();
                        return;
                    }
                }
            }
            cancelNotification(sbn.getKey());
        } catch (Exception e) {
            Log.e(TAG, "WA arama reddetme hatasi: " + e.getMessage());
        }
    }

    private String resolvePhoneNumber(String callerIdentity) {
        String extracted = extractPhoneNumber(callerIdentity);
        if (extracted != null) return extracted;
        return lookupNumberByName(callerIdentity);
    }

    private String lookupNumberByName(String name) {
        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            };
            String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
            String[] args = {"%" + name + "%"};
            Cursor cursor = getContentResolver().query(uri, projection, selection, args, null);
            if (cursor != null && cursor.moveToFirst()) {
                String number = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                );
                cursor.close();
                return number.replaceAll("[\\s\\-().]", "");
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Rehber arama hatasi: " + e.getMessage());
        }
        return null;
    }

    private void sendSms(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        } catch (Exception e) {
            Log.e(TAG, "SMS hatasi: " + e.getMessage());
        }
    }

    private void logBlockedCall(SharedPreferences prefs, String identity, String number) {
        try {
            String logsJson = prefs.getString("callLogs", "[]");
            JSONArray logs = new JSONArray(logsJson);
            JSONObject log = new JSONObject();
            log.put("number", number);
            log.put("name", identity);
            log.put("type", "whatsapp");
            log.put("time", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()));
            logs.put(log);
            while (logs.length() > 50) logs.remove(0);
            prefs.edit().putString("callLogs", logs.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Log hatasi: " + e.getMessage());
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

    private String normalizeNumber(String input) {
        if (input == null) return "";
        return input.replaceAll("[^0-9]", "");
    }
}
