package com.meraglove.meraglove;

import java.io.IOException;
import java.nio.ByteBuffer;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.view.WindowManager;
import java.util.*;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import static android.R.attr.value;
import static com.meraglove.meraglove.BleAdapterService.DUMBLE_WEIGHT_UUID;
import static com.meraglove.meraglove.BleAdapterService.GLOVE_ORIENTATION_UUID;
import static com.meraglove.meraglove.BleAdapterService.MERA_GLOVE_SERVICE_UUID;
import static com.meraglove.meraglove.BleAdapterService.REP_UUID;
import static com.meraglove.meraglove.Utility.byteArrayAsHexString;

public class PeripheralControlActivity extends Activity {

    public FirebaseDatabase database = FirebaseDatabase.getInstance();

    public static final int VIEW_TYPE_TEXT_VIEW = 1;
    public static final int VIEW_TYPE_BUTTON_NOTIFY = 2;

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ID = "id";
    public static final String USER_ID = "";
    public static final String GOOGLE_ID = "";

    private ArrayList<CharacteristicProperties> characteristic_properties;

    private String userId;
    private String googleId;
    private String mDeviceName;
    private String mDeviceAddress;
    private BleAdapterService mBluetoothLeService;
    private Timer mTimer;
    TextView forceView = null;
    TextView repView = null;
    LinearLayout glove = null;
    LinearLayout peripheralView = null;
    Button connectButton = null;

    boolean updateUI_thread = false;

    public String weight = "0";
    public String newWeight = "0";

    public String reps = "0";
    public String newReps = "0";

    public String orientation = "U";
    public String newOrientation = "U";

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BleAdapterService.LocalBinder) service).getService();
            mBluetoothLeService.setActivityHandler(mMessageHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showMsg("> > > onCreate");
        initialiseCharacteristicProperties();
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.meraglove);

        // read intent data
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRA_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRA_ID);
        userId = intent.getStringExtra(USER_ID);
        googleId = intent.getStringExtra(GOOGLE_ID);

        showMsg(userId);
        ((TextView) findViewById(R.id.status)).setText(getString(R.string.signed_in_fmt, userId));

        forceView = ((TextView) findViewById(R.id.forceView));
        repView = ((TextView) findViewById(R.id.repView));

        glove = (LinearLayout) findViewById(R.id.Glove);
        connectButton = (Button) findViewById(R.id.button_connect);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBluetoothLeService != null) {
                    if (mBluetoothLeService.connect(mDeviceAddress)) {
                        connectButton.setEnabled(false);
                        connectButton.setText("Disconnect");
                    } else {
                        showMsg("onConnect: failed to connect");
                    }
                } else {
                    showMsg("onConnect: mBluetoothLeService=null");
                }
            }
        });

        // connect to the Bluetooth smart service
        Intent gattServiceIntent = new Intent(this, BleAdapterService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void initialiseCharacteristicProperties() {
        CharacteristicProperties char_props = null;
        characteristic_properties = new ArrayList<CharacteristicProperties>();

        char_props = new CharacteristicProperties(MERA_GLOVE_SERVICE_UUID, DUMBLE_WEIGHT_UUID);
        char_props.setSupports_read(true);
        char_props.setSupports_notify(true);
        characteristic_properties.add(char_props);

        char_props = new CharacteristicProperties(MERA_GLOVE_SERVICE_UUID, GLOVE_ORIENTATION_UUID);
        char_props.setSupports_read(true);
        char_props.setSupports_notify(true);

        char_props = new CharacteristicProperties(MERA_GLOVE_SERVICE_UUID, REP_UUID);
        char_props.setSupports_read(true);
        char_props.setSupports_notify(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    public void onConnectButtonClick(View view) {
        showMsg("> > > onConnect");

        if (mBluetoothLeService != null) {
            if (mBluetoothLeService.connect(mDeviceAddress)) {
                ((Button) PeripheralControlActivity.this.findViewById(R.id.button_connect)).setEnabled(false);
            } else {
                showMsg("onConnect: failed to connect");
            }
        } else {
            showMsg("onConnect: mBluetoothLeService=null");
        }
    }

    public void onRead(String service, String characteristic) {
        if (mBluetoothLeService != null) {
            String service_uuid = Utility.normaliseUUID(Utility.extractServiceUuidFromTag(service));
            String characteristic_uuid = Utility.normaliseUUID(Utility.extractCharacteristicUuidFromTag(characteristic));
            mBluetoothLeService.readCharacteristic("19B10000-E8F2-537E-4F6C-D105768A1214", "19B10001-E8F2-537E-4F6C-D105768A1214");
        }
    }

    public void onWrite(View view) {
        String tag = (String) view.getTag();
        String service_uuid = Utility.extractServiceUuidFromTag(tag);
        String characteristic_uuid = Utility.extractCharacteristicUuidFromTag(tag);
        EditText text_view = (EditText) findViewByUUIDs(VIEW_TYPE_TEXT_VIEW, Utility.normaliseUUID(service_uuid), Utility.normaliseUUID(characteristic_uuid));
        String text = text_view.getText().toString();
        CharacteristicProperties char_props = new CharacteristicProperties(service_uuid, characteristic_uuid);
        int char_props_inx = characteristic_properties.indexOf(char_props);
        if (char_props_inx == -1) {
            showMsg("Error:Could not find characteristic properties");
            return;
        }
        char_props = characteristic_properties.get(char_props_inx);
        if (!char_props.isSupports_write() && !char_props.isSupports_write_without_response()) {
            showMsg("Error:Writing to characteristic not allowed");
            return;
        }

        if (!Utility.isValidHex(text)) {
            showMsg("Input is not a valid hex number");
            return;
        }
        if (mBluetoothLeService != null) {
            byte[] value = Utility.getByteArrayFromHexString(text);
            boolean require_response = !char_props.isSupports_write_without_response();
            if (require_response) {
                disableGattOpButtons();
            }
            mBluetoothLeService.writeCharacteristic(Utility.normaliseUUID(service_uuid), Utility.normaliseUUID(characteristic_uuid), value, require_response);
        }
    }

    public void onNotify(String service, String characteristic) {
        showMsg("onNotify");
        if (mBluetoothLeService != null) {
            disableGattOpButtons();
            if (mBluetoothLeService.setNotificationsState(service, characteristic, true)) {}
        } else {
            showMsg("Failed to set notifications state");
        }
    }

    // Service message handler
    private Handler mMessageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            Bundle bundle;
            String service_uuid = "";
            String characteristic_uuid = "";
            String descriptor_uuid = "";
            byte[] b = null;
            TextView value_text = null;

            switch (msg.what) {
                case BleAdapterService.GATT_CONNECTED:
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.button_connect)).setEnabled(false);
                    // we're connected
                    enableGattOpButtons();
                    enableGattOpEditTexts();
                    break;
                case BleAdapterService.GATT_DISCONNECT:
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.button_connect)).setEnabled(true);
                    PeripheralControlActivity.this.stopTimer();
                    disableGattOpButtons();
                    break;
                case BleAdapterService.GATT_SERVICES_DISCOVERED:
//                    Log.d(Constants.TAG, "Services discovered");

                    // start off the rssi reading timer
                    PeripheralControlActivity.this.startReadRssiTimer();

                    break;
                case BleAdapterService.GATT_CHARACTERISTIC_READ:
//                    Log.d(Constants.TAG, "Handler received characteristic read result");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
//                    Log.d(Constants.TAG, "Handler processing characteristic " + characteristic_uuid + " of " + service_uuid);
                    b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
//                    Log.d(Constants.TAG, "Value=" + byteArrayAsHexString(b));
                    enableGattOpButtons();
                    break;
                case BleAdapterService.GATT_CHARACTERISTIC_WRITTEN:
//                    Log.d(Constants.TAG, "Handler received characteristic written result");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
//                    Log.d(Constants.TAG, "characteristic " + characteristic_uuid + " of " + service_uuid + " written OK");
                    enableGattOpButtons();
                    break;
                case BleAdapterService.GATT_DESCRIPTOR_WRITTEN:
//                    Log.d(Constants.TAG, "Handler received descriptor written result");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    descriptor_uuid = bundle.getString(BleAdapterService.PARCEL_DESCRIPTOR_UUID);
//                    Log.d(Constants.TAG, "descriptor " + descriptor_uuid + " of " + "characteristic " + characteristic_uuid + " of " + service_uuid + " written OK");
                    enableGattOpButtons();
                    break;
                case BleAdapterService.NOTIFICATION_RECEIVED:
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);

                    if (characteristic_uuid.equals("00002a40-0000-1000-8000-00805f9b34fb")) {
                        newOrientation = byteArrayAsHexString(b).toString().equals("01") ? "D" : "U";
                    }

                    if (characteristic_uuid.equals("00002a19-0000-1000-8000-00805f9b34fb")) {
                        newWeight = String.valueOf(Integer.parseInt(Utility.byteArrayAsHexString(b).toString(), 16));
                    }

                    if (characteristic_uuid.equals("00002a80-0000-1000-8000-00805f9b34fb")) {
                        newReps = String.valueOf(Integer.parseInt(Utility.byteArrayAsHexString(b).toString(), 16));
                    }

                    break;
                case BleAdapterService.GATT_REMOTE_RSSI:
                    bundle = msg.getData();
                    int rssi = bundle.getInt(BleAdapterService.PARCEL_RSSI);
                    PeripheralControlActivity.this.updateRssi(rssi);
                    break;
                case BleAdapterService.MESSAGE:
                    bundle = msg.getData();
                    String text = bundle.getString(BleAdapterService.PARCEL_TEXT);
                    showMsg(text);
                    break;
                case BleAdapterService.ERROR:
                    bundle = msg.getData();
                    String error = bundle.getString(BleAdapterService.PARCEL_ERROR);
                    showMsg(error);
                    enableGattOpButtons();
            }
        }
    };

    // rssi read timer functions //////////
    int counter = 1;

    private void startReadRssiTimer() {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {

                switch (counter) {
                    case 0:
                        mBluetoothLeService.readRemoteRssi();
                        counter++;
                        return;
                    case 1:
                        onNotify("0000180F-0000-1000-8000-00805f9b34fb", "00002A19-0000-1000-8000-00805f9b34fb");
                        counter++;
                        return;
                    case 2:
                        onNotify("0000180F-0000-1000-8000-00805f9b34fb", "00002A40-0000-1000-8000-00805f9b34fb");
                        counter++;
                        return;

                    case 3:
                        onNotify("0000180F-0000-1000-8000-00805f9b34fb", "00002A80-0000-1000-8000-00805f9b34fb");
                        counter++;
                        updateUI_thread = true;
                        return;
                    case 4:
                        new Thread(new Task()).start();
                        counter++;
                        break;
                    default:
                        mBluetoothLeService.readRemoteRssi();
                        counter++;
                        return;

                }

            }
        }, 0, 600);
    }

    private void stopTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    private void updateRssi(int rssi) {}
    private void showMsg(String msg) {
        Log.d(Constants.TAG, msg);
    }
    private ArrayList<Button> getGattOpButtons(ViewGroup root) { return null; }
    private void enableGattOpButtons() {}
    private void disableGattOpButtons() {}
    private ArrayList<EditText> getGattOpEditTexts(ViewGroup root) { return null; }
    private void enableGattOpEditTexts() {}
    private View findViewByUUIDs(int view_type, String service_uuid, String characteristic_uuid) { return null; }


    class Task implements Runnable {
        @Override
        public void run() {

            while (updateUI_thread) {
                try {
                    Thread.sleep(160);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                runOnUiThread(new Runnable() {
                    public void run() {
                        Boolean updateDatabase = false;
                        if (!newWeight.equalsIgnoreCase(weight)) {
                            weight = newWeight;
                            forceView.setText(weight);
                            updateDatabase = true;
                        }

                        if (!newReps.equalsIgnoreCase(reps)) {
                            reps = newReps;
                            repView.setText(reps);
                            updateDatabase = true;
                        }

                        if (!newOrientation.equalsIgnoreCase(orientation)) {
                            orientation = newOrientation;
                            updateDatabase = true;
                        }

                        if (updateDatabase) {
//                            database.getReference(userId).setValue(String.valueOf(Calendar.getInstance().getTimeInMillis()) + " " + weight + " " + orientation + " " + reps);

                            DatabaseReference databaseReference = database.getReference("gloves");

                            String key = databaseReference.child(googleId).push().getKey();

                            HashMap<String, Object> row = new HashMap<>();
                            row.put("timestamp", String.valueOf(Calendar.getInstance().getTimeInMillis()));
                            row.put("userId", userId);
                            row.put("googleId", googleId);
                            row.put("weight", weight);
                            row.put("orientation", orientation);
                            row.put("reps", reps);

                            Map<String, Object> databaseUpdate = new HashMap<>();
                            databaseUpdate.put(googleId + "/" + key, row);
                            databaseReference.updateChildren(databaseUpdate);
                            glove.setBackgroundResource(orientation.equalsIgnoreCase(("D")) ? R.drawable.arrow_down : R.drawable.arrow_up);
                        }
                    }
                });
            }
        }
    }

}
