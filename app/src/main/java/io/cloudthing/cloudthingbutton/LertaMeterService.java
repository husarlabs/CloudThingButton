package io.cloudthing.cloudthingbutton;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import io.cloudthing.sdk.device.connectivity.http.DataRequestFactory;
import io.cloudthing.sdk.device.connectivity.http.HttpRequestQueue;
import io.cloudthing.sdk.device.data.GenericDataPayload;
import io.cloudthing.sdk.device.data.convert.JsonPayloadConverter;
import io.cloudthing.sdk.device.utils.CredentialCache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Created by patryk on 15.12.17.
 */

public class LertaMeterService extends Service {

    private ResultReceiver resultReceiver;
    private BluetoothAdapter adapter;
    private Timer timer = new Timer();
    private MyTimerTask timerTask;
    private BufferedReader reader;
    private InputStream inputStream;
    private Intent intent;
    private HttpRequestQueue requestQueue = HttpRequestQueue.getInstance();
    private int[] preamble = new int[2];
    private String frame;
    private BluetoothSocket socket;
    private DataRequestFactory client;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        adapter = BluetoothAdapter.getDefaultAdapter();
        this.intent = intent;

        if (adapter == null) {
            System.out.println("Device doesn't support Bluetooth :(");
            return START_NOT_STICKY;
        }
        resultReceiver = intent.getParcelableExtra("receiver");
        doRelay();
        timerTask = new MyTimerTask();
        timer.scheduleAtFixedRate(timerTask, 0, 500);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        timer.cancel();
        Bundle bundle = new Bundle();
        bundle.putString("end", "Timer Stopped....\n");
        resultReceiver.send(200, bundle);
    }

    private class MyTimerTask extends TimerTask {
        MyTimerTask() {
            doLog("Timer Started....\n", "start", 100);
        }

        @Override
        public void run() {
            if (!socket.isConnected()) {
                doRelay();
                doLog("Reconnecting...", "restart", 100);
            }
            try {
                preamble[0] = inputStream.read();
                preamble[1] = inputStream.read();
                final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                if ((frame = reader.readLine()) != null) {
                    logFrame();
                    sendData();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        private void logFrame() {
            doLog(frame, "log", 10);
            System.out.println(frame);
        }


    }

    private void doRelay() {
        try {
            final Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices.iterator().hasNext()) {
                final BluetoothDevice device = bondedDevices.iterator().next();
                if ("98:D3:32:70:67:1F".equals(device.getAddress())) {
                    handleLerta(device);
                } else {
                    System.out.println("NOT LERTA METER :(");
                }
            }
        } catch (IOException | MqttException e) {
            e.printStackTrace();
        }
    }

    private void handleLerta(BluetoothDevice device) throws IOException, MqttException {
        final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
        socket.connect();
        inputStream = socket.getInputStream();
    }


    private void sendData() throws Exception {
        if (isFrameValid()) {
            final GenericDataPayload payload = new GenericDataPayload();
            payload.setConverter(new JsonPayloadConverter());
            final int type = frame.charAt(1);
            payload.add("type", String.valueOf(type));
            for (String record : frame.substring(2).split(";")) {
                final String[] recordSplit = record.split("=");
                final String key = recordSplit[0].replace(".", "_");
                final String[] valueSplit = recordSplit[1].split("\\*");
                final double value = Double.parseDouble(valueSplit[0]);
                final String unit = valueSplit[1];
                final Date time = new Date();
                payload.add(key, String.valueOf(value), time);
                payload.add(key + ".unit", unit, time);
            }

            final DataRequestFactory factory = getDataRequestFactory();
            requestQueue.addToRequestQueue(factory.getRequest(payload), factory.getListener());
        } else {
            System.out.println("!!INVALID FRAME!!: " + frame);
            doLog("!!INVALID FRAME!!: " + frame, "log", 10);
        }
    }

    private boolean isFrameValid() {
        final char[] frameChars = frame.toCharArray();
        return preamble[0] == 0xAA && preamble[1] == 0xAA &&
                frame.length() > 2 &&
                (int) frameChars[0] == preamble.length + frame.length() + 2;
    }

    private DataRequestFactory getDataRequestFactory() throws MqttException {
        final String tenant = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("tenant", null);
        final String deviceId = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("deviceId", null);
        final String token = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("token", null);
        if (isCredentialsValid(tenant, deviceId, token)) {
            Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivity(intent);
        }
        Log.d("DEBUG", "Tenant: " + tenant);
        Log.d("DEBUG", "Device ID: " + deviceId);
        Log.d("DEBUG", "Token: " + token);
        CredentialCache.getInstance().setCredentials(tenant, deviceId, token);

            return DataRequestFactory.builder()
                    .setDeviceId(deviceId)
                    .setTenant(tenant)
                    .setToken(token)
                    .setListener(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            final String logMessage = "Failed to send data to IoT :(";
                            doLog(logMessage, "log", 10);
                            e.printStackTrace();
//                            if (e instanceof SocketTimeoutException) {
//                                try {
//                                    e.printStackTrace();
//                                    doLog(e.getClass().getName(), "log", 10);
//                                    sendData();
//                                } catch (Exception e1) {
//                                    e1.printStackTrace();
//                                }
//                            }
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            System.out.println(response);
                            doLog("Sent data to IoT with status " + response.code(), "log", 10);
                        }
                    })
                    .build();
    }

    private void doLog(String line, String log, int resultCode) {
        Bundle bundle = new Bundle();
        bundle.putString(log, new Date() + line);
        resultReceiver.send(resultCode, bundle);
    }

    private boolean isCredentialsValid(String tenant, String deviceId, String token) {
        return tenant == null || "".equals(tenant)
                || deviceId == null || "".equals(deviceId)
                || token == null || "".equals(token);
    }

}
