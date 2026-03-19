package com.callblocker;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ContactManagerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ContactAdapter adapter;
    private List<ContactItem> contactList = new ArrayList<>();
    private SharedPreferences prefs;
    private Switch switchBlockAll;
    private EditText etDefaultMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_manager);

        prefs = getSharedPreferences("CallBlockerPrefs", MODE_PRIVATE);

        switchBlockAll = findViewById(R.id.switchBlockAll);
        etDefaultMessage = findViewById(R.id.etDefaultMessage);
        recyclerView = findViewById(R.id.recyclerContacts);
        Button btnAddContact = findViewById(R.id.btnAddContact);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContactAdapter(contactList, this::onDeleteContact, this::onEditContact);
        recyclerView.setAdapter(adapter);

        String defaultMsg = prefs.getString("defaultMessage", "Su an mesgulum, sonra arayacagim.");
        etDefaultMessage.setText(defaultMsg);

        etDefaultMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                prefs.edit().putString("defaultMessage", etDefaultMessage.getText().toString()).apply();
            }
        });

        boolean blockAll = prefs.getBoolean("blockAll", false);
        switchBlockAll.setChecked(blockAll);
        updateContactListVisibility(!blockAll);

        switchBlockAll.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean("blockAll", checked).apply();
            updateContactListVisibility(!checked);
        });

        btnAddContact.setOnClickListener(v -> showAddContactDialog(null));

        loadContacts();
    }

    private void updateContactListVisibility(boolean show) {
        recyclerView.setVisibility(show ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnAddContact).setVisibility(show ? View.VISIBLE : View.GONE);
        TextView tvHint = findViewById(R.id.tvContactHint);
        if (show) {
            tvHint.setText("Sadece listedeki kisilerin aramalari engellenecek");
        } else {
            tvHint.setText("Tum gelen aramalar engellenecek");
        }
    }

    private void showAddContactDialog(ContactItem existingContact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_contact, null);
        builder.setView(dialogView);
        builder.setTitle(existingContact == null ? "Kisi Ekle" : "Kisi Duzenle");

        EditText etName = dialogView.findViewById(R.id.etContactName);
        EditText etNumber = dialogView.findViewById(R.id.etContactNumber);
        EditText etMessage = dialogView.findViewById(R.id.etContactMessage);

        if (existingContact != null) {
            etName.setText(existingContact.name);
            etNumber.setText(existingContact.number);
            etMessage.setText(existingContact.customMessage);
        }

        builder.setPositiveButton("Kaydet", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String number = etNumber.getText().toString().trim();
            String message = etMessage.getText().toString().trim();

            if (number.isEmpty()) {
                Toast.makeText(this, "Numara zorunlu!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (existingContact != null) {
                existingContact.name = name;
                existingContact.number = number;
                existingContact.customMessage = message;
            } else {
                contactList.add(new ContactItem(name, number, message));
            }
            adapter.notifyDataSetChanged();
            saveContacts();
        });

        builder.setNegativeButton("Iptal", null);
        builder.show();
    }

    private void onDeleteContact(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Kisiyi Sil")
                .setMessage("Bu kisiyi listeden kaldirmak istediginize emin misiniz?")
                .setPositiveButton("Sil", (d, w) -> {
                    contactList.remove(position);
                    adapter.notifyDataSetChanged();
                    saveContacts();
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void onEditContact(int position) {
        showAddContactDialog(contactList.get(position));
    }

    private void loadContacts() {
        try {
            String json = prefs.getString("contactList", "[]");
            JSONArray arr = new JSONArray(json);
            contactList.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                contactList.add(new ContactItem(
                        obj.optString("name", ""),
                        obj.getString("number"),
                        obj.optString("customMessage", "")
                ));
            }
            adapter.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveContacts() {
        try {
            JSONArray arr = new JSONArray();
            for (ContactItem c : contactList) {
                JSONObject obj = new JSONObject();
                obj.put("name", c.name);
                obj.put("number", c.number);
                obj.put("customMessage", c.customMessage);
                arr.put(obj);
            }
            prefs.edit()
                .putString("contactList", arr.toString())
                .putInt("contactCount", contactList.size())
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit().putString("defaultMessage", etDefaultMessage.getText().toString()).apply();
    }

    static class ContactItem {
        String name, number, customMessage;
        ContactItem(String name, String number, String customMessage) {
            this.name = name; this.number = number; this.customMessage = customMessage;
        }
    }

    static class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.VH> {
        interface OnAction { void run(int pos); }
        List<ContactItem> list;
        OnAction onDelete, onEdit;

        ContactAdapter(List<ContactItem> list, OnAction onDelete, OnAction onEdit) {
            this.list = list; this.onDelete = onDelete; this.onEdit = onEdit;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            ContactItem item = list.get(position);
            holder.tvName.setText(item.name.isEmpty() ? item.number : item.name);
            holder.tvNumber.setText(item.number);
            holder.tvMessage.setText(item.customMessage.isEmpty() ? "Varsayilan mesaj" : item.customMessage);
            holder.btnDelete.setOnClickListener(v -> onDelete.run(holder.getAdapterPosition()));
            holder.btnEdit.setOnClickListener(v -> onEdit.run(holder.getAdapterPosition()));
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvNumber, tvMessage;
            Button btnDelete, btnEdit;
            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvContactName);
                tvNumber = v.findViewById(R.id.tvContactNumber);
                tvMessage = v.findViewById(R.id.tvContactMessage);
                btnDelete = v.findViewById(R.id.btnDeleteContact);
                btnEdit = v.findViewById(R.id.btnEditContact);
            }
        }
    }
}
