package com.bunnyhop.app;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class AdminActivity extends AppCompatActivity {

    private AlertDatabaseHelper dbHelper;
    private ArrayAdapter<Alert> adapter;
    private final List<Alert> alerts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        dbHelper = new AlertDatabaseHelper(this);

        ListView alertList = findViewById(R.id.alertListView);
        TextView emptyView = findViewById(R.id.emptyView);
        alertList.setEmptyView(emptyView);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, alerts);
        alertList.setAdapter(adapter);

        Button addButton = findViewById(R.id.addAlertButton);
        addButton.setOnClickListener(v -> showAlertForm(null));

        alertList.setOnItemClickListener((parent, view, position, id) -> {
            showAlertForm(alerts.get(position));
        });

        alertList.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(alerts.get(position));
            return true;
        });

        refreshAlerts();
    }

    @Override
    protected void onDestroy() {
        dbHelper.close();
        super.onDestroy();
    }

    private void refreshAlerts() {
        alerts.clear();
        alerts.addAll(dbHelper.getAlerts());
        adapter.notifyDataSetChanged();
    }

    private void showAlertForm(@androidx.annotation.Nullable Alert alert) {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_alert_form, null);
        EditText deviceNameInput = form.findViewById(R.id.deviceNameInput);
        EditText messageInput = form.findViewById(R.id.alertMessageInput);
        EditText channelsInput = form.findViewById(R.id.alertChannelsInput);
        CheckBox activeInput = form.findViewById(R.id.alertActiveCheckbox);

        if (alert != null) {
            deviceNameInput.setText(alert.getDeviceName());
            messageInput.setText(alert.getMessage());
            channelsInput.setText(alert.getChannels());
            activeInput.setChecked(alert.isActive());
        }

        new AlertDialog.Builder(this)
                .setTitle(alert == null ? R.string.add_alert_title : R.string.edit_alert_title)
                .setView(form)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String deviceName = deviceNameInput.getText().toString().trim();
                    String message = messageInput.getText().toString().trim();
                    String channels = channelsInput.getText().toString().trim();
                    boolean active = activeInput.isChecked();
                    if (deviceName.isEmpty() || message.isEmpty()) {
                        Toast.makeText(AdminActivity.this, R.string.alert_fields_required, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Alert saved = new Alert(alert == null ? 0L : alert.getId(), deviceName, message, active, channels);
                    dbHelper.saveAlert(saved);
                    refreshAlerts();
                    Toast.makeText(AdminActivity.this, R.string.alert_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(Alert alert) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_alert_title)
                .setMessage(getString(R.string.delete_alert_message, alert.getZoneLabel()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    dbHelper.deleteAlert(alert.getId());
                    refreshAlerts();
                    Toast.makeText(AdminActivity.this, R.string.alert_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
