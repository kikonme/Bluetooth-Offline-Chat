
package com.example.bluetoothchat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket chatSocket;
    private TextView status, messages;
    private EditText messageInput;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final UUID APP_UUID = UUID.fromString(
            "fa87c0d0-afac-11de-8a39-0800200c9a66"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BluetoothManager manager =
                (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

        bluetoothAdapter = manager.getAdapter();

        createChatScreen();

        if (bluetoothAdapter == null) {
            status.setText("Bluetooth is not supported");
            return;
        }

        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT
                    }, 100
            );
        }
    }

    private void createChatScreen() {

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(20, 25, 20, 20);

        main.setBackgroundColor(0xFFF2F4F8);

        TextView title = new TextView(this);
        title.setText("Bluetooth Messenger");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setPadding(10, 15, 10, 15);

        status = new TextView(this);
        status.setText("Status: Not connected");
        status.setTextSize(15);
        status.setPadding(10, 15, 10, 15);

        Button listenButton = new Button(this);
        listenButton.setText("Start Listening");
        listenButton.setOnClickListener(v -> startListening());

        Button connectButton = new Button(this);
        connectButton.setText("Connect to Paired Phone");
        connectButton.setOnClickListener(v -> showPairedDevices());

        messages = new TextView(this);
        messages.setTextSize(16);
        messages.setPadding(12, 12, 12, 12);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(messages);

        messageInput = new EditText(this);
        messageInput.setHint("Type your message...");

        Button sendButton = new Button(this);
        sendButton.setText("Send");
        sendButton.setOnClickListener(v -> sendMessage());

        main.addView(title);
        main.addView(status);
        main.addView(listenButton);
        main.addView(connectButton);

        main.addView(scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                ));

        main.addView(messageInput);
        main.addView(sendButton);

        setContentView(main);
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < 31 ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private boolean bluetoothReady() {

        if (!hasBluetoothPermission()) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT
                    }, 100
            );
            return false;
        }

        try {
            if (!bluetoothAdapter.isEnabled()) {
                status.setText("Please enable Bluetooth");
                startActivity(
                        new android.content.Intent(
                                BluetoothAdapter.ACTION_REQUEST_ENABLE
                        )
                );
                return false;
            }
        } catch (SecurityException e) {
            status.setText("Bluetooth permission required");
            return false;
        }

        return true;
    }

    private void startListening() {

        if (!bluetoothReady()) return;

        status.setText("Waiting for another phone...");

        new Thread(() -> {

            try {
                BluetoothServerSocket server =
                        bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                                "BluetoothChat", APP_UUID
                        );

                BluetoothSocket accepted = server.accept();

                server.close();

                connected(accepted);

            } catch (IOException | SecurityException e) {
                updateStatus("Connection failed: " + e.getMessage());
            }

        }).start();
    }

    private void showPairedDevices() {

        if (!bluetoothReady()) return;

        try {
            Set<BluetoothDevice> devices =
                    bluetoothAdapter.getBondedDevices();

            ArrayList<BluetoothDevice> deviceList =
                    new ArrayList<>(devices);

            if (deviceList.isEmpty()) {
                Toast.makeText(this,
                        "Pair phones in Bluetooth settings first",
                        Toast.LENGTH_LONG).show();
                return;
            }

            String[] names = new String[deviceList.size()];

            for (int i = 0; i < deviceList.size(); i++) {
                names[i] = deviceList.get(i).getName();
            }

            new AlertDialog.Builder(this)
                    .setTitle("Choose a paired phone")
                    .setItems(names, (dialog, which) ->
                            connectToDevice(deviceList.get(which)))
                    .show();

        } catch (SecurityException e) {
            updateStatus("Bluetooth permission required");
        }
    }

    private void connectToDevice(BluetoothDevice device) {

        status.setText("Connecting...");

        new Thread(() -> {

            try {
                BluetoothSocket newSocket =
                        device.createRfcommSocketToServiceRecord(APP_UUID);

                newSocket.connect();

                connected(newSocket);

            } catch (IOException | SecurityException e) {
                updateStatus("Could not connect");
            }

        }).start();
    }

    private void connected(BluetoothSocket socket) {

        chatSocket = socket;

        updateStatus("Connected successfully!");

        appendMessage("Connected to Bluetooth phone.");

        new Thread(() -> {

            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                chatSocket.getInputStream()
                        )
                );

                String line;

                while ((line = reader.readLine()) != null) {
                    appendMessage("Friend: " + line);
                }

            } catch (IOException e) {
                updateStatus("Connection disconnected");
            }

        }).start();
    }

    private void sendMessage() {

        String message = messageInput.getText().toString().trim();

        if (message.isEmpty()) return;

        if (chatSocket == null || !chatSocket.isConnected()) {
            Toast.makeText(this,
                    "Connect to a phone first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {

            try {
                OutputStream output = chatSocket.getOutputStream();

                output.write((message + "\n").getBytes("UTF-8"));
                output.flush();

                appendMessage("You: " + message);

            } catch (IOException e) {
                updateStatus("Message could not be sent");
            }

        }).start();

        messageInput.setText("");
    }

    private void appendMessage(String message) {

        handler.post(() ->
                messages.append(message + "\n\n")
        );
    }

    private void updateStatus(String text) {

        handler.post(() -> status.setText(text));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            if (chatSocket != null) {
                chatSocket.close();
            }
        } catch (IOException e) {
            // Connection already closed
        }
    }
}
