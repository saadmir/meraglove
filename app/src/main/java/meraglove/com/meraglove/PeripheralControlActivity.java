package meraglove.com.meraglove;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.WindowManager;
import java.util.*;
import android.util.Log;

public class PeripheralControlActivity extends Activity {

  public static final int VIEW_TYPE_TEXT_VIEW=1;
  public static final int VIEW_TYPE_BUTTON_NOTIFY=2;

	public static final String EXTRA_NAME = "name";
	public static final String EXTRA_ID = "id";
	
  private ArrayList<CharacteristicProperties> characteristic_properties;

	private String mDeviceName;
	private String mDeviceAddress;
	private BleAdapterService mBluetoothLeService;
	private Timer mTimer;

	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,	IBinder service) {
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
		initialiseCharacteristicProperties();	
    this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		setContentView(R.layout.activity_peripheral_control);

		// read intent data
		final Intent intent = getIntent();
		mDeviceName = intent.getStringExtra(EXTRA_NAME);
		mDeviceAddress = intent.getStringExtra(EXTRA_ID);

		// show the device name
		((TextView) this.findViewById(R.id.nameTextView)).setText("Device name: "+mDeviceName);

		// connect to the Bluetooth smart service
		Intent gattServiceIntent = new Intent(this, BleAdapterService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}
	
	private void initialiseCharacteristicProperties() {
	CharacteristicProperties char_props = null;
		characteristic_properties = new ArrayList<CharacteristicProperties>();
        // Only Mandatory or Excluded values are supported explicitly. Other values are interpreted as "not supported"
        // Only read, write, write no response and notify are currently supported
        char_props = new CharacteristicProperties("180F","2A19");	
          

	              
          
					          char_props.setSupports_read(true);
          
                    
                    
                  characteristic_properties.add(char_props);
        char_props = new CharacteristicProperties("181D","2A9E");	
          

	              
          
					          char_props.setSupports_read(true);
          
                    
                    
                  characteristic_properties.add(char_props);
        char_props = new CharacteristicProperties("181D","2A9D");	
          
                    
                    
                    
                  characteristic_properties.add(char_props);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopTimer();
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	public void onConnect(View view) {
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

   public void onRead(View view) {
        String tag = (String) view.getTag();
				showMsg("onRead:"+view.getTag());
        if (mBluetoothLeService != null) {
            String service_uuid = Utility.normaliseUUID(Utility.extractServiceUuidFromTag(tag));
            String characteristic_uuid = Utility.normaliseUUID(Utility.extractCharacteristicUuidFromTag(tag));
            disableGattOpButtons();
            mBluetoothLeService.readCharacteristic(service_uuid,characteristic_uuid);        
        } else {
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
        } else {
        }
    }

    public void onNotify(View view) {
        String tag = (String) view.getTag();
        String service_uuid = Utility.extractServiceUuidFromTag(tag);
        String characteristic_uuid = Utility.extractCharacteristicUuidFromTag(tag);
        Button button_view = (Button) findViewByUUIDs(VIEW_TYPE_BUTTON_NOTIFY, Utility.normaliseUUID(service_uuid), Utility.normaliseUUID(characteristic_uuid));
        if (button_view == null) {
            showMsg("Error:Notifications Button not found");
        }
        String text = button_view.getText().toString();
        Log.d(Constants.TAG, "text=" + text);
        boolean state = text.startsWith("Enable");
        CharacteristicProperties char_props = new CharacteristicProperties(service_uuid, characteristic_uuid);
        int char_props_inx = characteristic_properties.indexOf(char_props);
        if (char_props_inx == -1) {
            showMsg("Error:Could not find characteristic properties");
            return;
        }
        char_props = characteristic_properties.get(char_props_inx);
        if (!char_props.isSupports_notify()) {
            showMsg("Error:Notifications not supported");
            return;
        }

        if (mBluetoothLeService != null) {
            disableGattOpButtons();
            if (mBluetoothLeService.setNotificationsState(Utility.normaliseUUID(service_uuid), Utility.normaliseUUID(characteristic_uuid), state)) {
                if (state) {
                    button_view.setText("Disable Notifications");
                } else {
                    button_view.setText("Enable Notifications");
                }
            }
        } else {
            showMsg("Failed to set notifications state");
        }
    }

	// Service message handler
    private Handler mMessageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            Bundle bundle;
            String service_uuid="";
            String characteristic_uuid="";
            String descriptor_uuid="";
            byte[] b=null;
            TextView value_text=null;

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
                    Log.d(Constants.TAG, "Services discovered");

                    // start off the rssi reading timer
                    PeripheralControlActivity.this.startReadRssiTimer();

                    break;
                case BleAdapterService.GATT_CHARACTERISTIC_READ:
                    Log.d(Constants.TAG, "Handler received characteristic read result");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    Log.d(Constants.TAG, "Handler processing characteristic " + characteristic_uuid + " of " + service_uuid);
                    b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                    Log.d(Constants.TAG, "Value=" + Utility.byteArrayAsHexString(b));
                    value_text = (TextView) findViewByUUIDs(VIEW_TYPE_TEXT_VIEW, service_uuid, characteristic_uuid);
                    if (value_text != null) {
                        Log.d(Constants.TAG, "Handler found TextView for characteristic value");
                        value_text.setText(Utility.byteArrayAsHexString(b));
                    }
                    enableGattOpButtons();
                    break;
                case BleAdapterService.GATT_CHARACTERISTIC_WRITTEN:
                    Log.d(Constants.TAG, "Handler received characteristic written result");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    Log.d(Constants.TAG, "characteristic " + characteristic_uuid + " of " + service_uuid+" written OK");
                    enableGattOpButtons();
                    break;
                case BleAdapterService.GATT_DESCRIPTOR_WRITTEN:
                    Log.d(Constants.TAG, "Handler received descriptor written result");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    descriptor_uuid = bundle.getString(BleAdapterService.PARCEL_DESCRIPTOR_UUID);
                    Log.d(Constants.TAG, "descriptor " + descriptor_uuid + " of " + "characteristic " + characteristic_uuid + " of " + service_uuid+" written OK");
                    enableGattOpButtons();
                    break;
                case BleAdapterService.NOTIFICATION_RECEIVED:
                    Log.d(Constants.TAG, "Handler received notification");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    Log.d(Constants.TAG, "Handler processing characteristic " + characteristic_uuid + " of " + service_uuid);
                    b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                    Log.d(Constants.TAG, "Value=" + Utility.byteArrayAsHexString(b));
                    value_text = (TextView) findViewByUUIDs(VIEW_TYPE_TEXT_VIEW, service_uuid, characteristic_uuid);
                    if (value_text != null) {
                        Log.d(Constants.TAG, "Handler found TextView for characteristic value");
                        value_text.setText(Utility.byteArrayAsHexString(b));
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

	private void startReadRssiTimer() {
		mTimer = new Timer();
		mTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				mBluetoothLeService.readRemoteRssi();
			}
		}, 0, 2000);
	}

	private void stopTimer() {
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}
	}

	private void updateRssi(int rssi) {
    ((TextView) PeripheralControlActivity.this.findViewById(R.id.rssiTextView)).setText("RSSI: "+rssi);
	}

	private void showMsg(String msg) {
    Log.d(Constants.TAG, msg);
    ((TextView) PeripheralControlActivity.this.findViewById(R.id.msgTextView)).setText(msg);
  }

  private ArrayList<Button> getGattOpButtons(ViewGroup root){
    ArrayList<Button> buttons = new ArrayList<Button>();
    final int childCount = root.getChildCount();
    for (int i = 0; i < childCount; i++) {
        final View child = root.getChildAt(i);
        if (child instanceof ViewGroup) {
            buttons.addAll(getGattOpButtons((ViewGroup) child));
        } 

				if (child instanceof Button) {
          final Object tagObj = child.getTag();
          if (tagObj != null && ((String) (tagObj)).startsWith("gatt_op")) {
	            buttons.add((Button)child);
	        }
        }
    }
    return buttons;
  }
 
  private void enableGattOpButtons() {
    LinearLayout root_layout =(LinearLayout) this.findViewById(R.id.deviceView);
    ArrayList<Button> buttons = getGattOpButtons(root_layout);
    for (Button button : buttons) {
  	  button.setEnabled(true);
    }
  } 

  private void disableGattOpButtons() {
	LinearLayout root_layout =(LinearLayout) this.findViewById(R.id.deviceView);
	ArrayList<Button> buttons = getGattOpButtons(root_layout);
	for (Button button : buttons) {
		button.setEnabled(false);
	}
  }

  private ArrayList<EditText> getGattOpEditTexts(ViewGroup root){
    ArrayList<EditText> texts = new ArrayList<EditText>();
    final int childCount = root.getChildCount();
    for (int i = 0; i < childCount; i++) {
        final View child = root.getChildAt(i);
        if (child instanceof ViewGroup) {
            texts.addAll(getGattOpEditTexts((ViewGroup) child));
        } 

        if (child instanceof EditText) {
          final Object tagObj = child.getTag();
          if (tagObj != null && ((String) (tagObj)).startsWith("gatt_op")) {
	            texts.add((EditText)child);
	        }
        }
    }
    return texts;
  }
 
  private void enableGattOpEditTexts() {
    LinearLayout root_layout =(LinearLayout) this.findViewById(R.id.deviceView);
    ArrayList<EditText> texts = getGattOpEditTexts(root_layout);
    for (EditText text : texts) {
  	  text.setEnabled(true);
    }
  } 

    private View findViewByUUIDs(int view_type, String service_uuid, String characteristic_uuid){
        if (view_type == VIEW_TYPE_TEXT_VIEW) {
                if (service_uuid.equalsIgnoreCase(Utility.normaliseUUID("180F")) && characteristic_uuid.equalsIgnoreCase(Utility.normaliseUUID("2A19"))) {
                    return (TextView) this.findViewById(R.id.text_180F_2A19);
                }
                if (service_uuid.equalsIgnoreCase(Utility.normaliseUUID("181D")) && characteristic_uuid.equalsIgnoreCase(Utility.normaliseUUID("2A9E"))) {
                    return (TextView) this.findViewById(R.id.text_181D_2A9E);
                }
                if (service_uuid.equalsIgnoreCase(Utility.normaliseUUID("181D")) && characteristic_uuid.equalsIgnoreCase(Utility.normaliseUUID("2A9D"))) {
//                    return (TextView) this.findViewById(R.id.text_181D_2A9D);
                }
        } else {
                if (view_type == VIEW_TYPE_BUTTON_NOTIFY) {
			          
			          			          
			          			          
			          
	              }
        }
     		return null;
    }
}