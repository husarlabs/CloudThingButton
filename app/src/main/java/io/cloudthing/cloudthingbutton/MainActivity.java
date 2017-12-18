package io.cloudthing.cloudthingbutton;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import io.cloudthing.sdk.device.connectivity.http.EventRequestFactory;
import io.cloudthing.sdk.device.connectivity.http.HttpRequestQueue;
import io.cloudthing.sdk.device.utils.CredentialCache;

public class MainActivity extends AppCompatActivity {

    private String tenant;
    private String deviceId;
    private String token;

    private HttpRequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setCredentials();

        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggleButton);
        toggleButton.setOnClickListener(new ToggleListener());

        requestQueue = HttpRequestQueue.getInstance();
    }

    @Override
    protected void onResume() {
        setCredentials();
        super.onResume();
    }

    private void setCredentials() {
        tenant = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("tenant", null);
        deviceId = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("deviceId", null);
        token = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("token", null);
        if (isCredentialsValid()) {
            Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivity(intent);
        }
        Log.d("DEBUG", "Tenant: " + tenant);
        Log.d("DEBUG", "Device ID: " + deviceId);
        Log.d("DEBUG", "Token: " + token);
        CredentialCache.getInstance().setCredentials(tenant, deviceId, token);
    }

    private boolean isCredentialsValid() {
        return tenant == null || "".equals(tenant)
                || deviceId == null || "".equals(deviceId)
                || token == null || "".equals(token);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.action_relay:
                Intent relayIntent = new Intent(getApplicationContext(), BluetoothActivity.class);
                startActivity(relayIntent);
                break;
            default:
                break;
        }

        return true;
    }

    private class ToggleListener implements View.OnClickListener {

        private EventRequestFactory eventRequestFactory;

        public ToggleListener() {
            eventRequestFactory = new EventRequestFactory(deviceId, token, tenant);
        }

        @Override
        public void onClick(View v) {
            if (isCredentialsValid()) {
                Toast.makeText(getApplicationContext(), "Please set device credentials!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intent);
                return;
            }
            ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggleButton);
            Log.d("DEBUG", "Button was toggled to: " + toggleButton.isChecked());

            sendEvent(toggleButton.isChecked());
        }

        private void sendEvent(boolean toggle) {
            eventRequestFactory.setPayload(String.valueOf(toggle));
            eventRequestFactory.setEventId(toggle ? "toggleOn" : "toggleOff");

            requestQueue.addToRequestQueue(eventRequestFactory.getRequest(), eventRequestFactory.getListener());
        }

    }

}
