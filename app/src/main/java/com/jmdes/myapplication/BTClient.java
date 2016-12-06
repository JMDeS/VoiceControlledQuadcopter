/* Project: App Controller for copter 4(including Crazepony)
 * Author: 	Huang Yong xiang 
 * Brief:	This is an open source project under the terms of the GNU General Public License v3.0
 * TOBE FIXED:  1. disconnect and connect fail with Bluetooth due to running thread 
 * 				2. Stick controller should be drawn in dpi instead of pixel.  
 * 
 * */

package com.jmdes.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import static com.jmdes.myapplication.R.layout.main;

@SuppressLint("NewApi")
public class BTClient extends Activity {
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);


    private final static String TAG = BTClient.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService; //BLE transceiver services
    private boolean mConnected = false;

	private final static int REQUEST_CONNECT_DEVICE = 1; //The macro defines the device handle
    private final static int ACTION_RECOGNIZE_SPEECH = 2;

	//BLE to send data to the cycle, is now two types of data, one rocker 4-channel value,
    // and the second is to request IMU data with the new command
    //BLE module itself transmission rate is limited, to minimize the amount of data sent
	private final static int WRITE_DATA_PERIOD=40;
    private static int IMU_CNT = 0; //update IMU period，Update the IMU data period，40*10ms
    Handler timeHandler = new Handler();    // Timer period, used to update the IMU data
	 
	private TextView throttleText,yawText,pitchText,rollText;
	private TextView pitchAngText,rollAngText,yawAngText,altText,distanceText,voltageText;
	private Button connectButton, armButton,lauchLandButton,headFreeButton,altHoldButton, speechButton;

    //Joystick interface implementation class，joystick UI
	private MySurfaceView stickView;

    Dialog match_text_dialog;
    ArrayList<String> matches_text;

    // Code to manage Service lifecycle.
    // Manage the entire lifecycle of the BLE data service
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    // Define various types of event receiver mGattUpdateReceiver that deal with BLE to receive
    // and dispatch the service, mainly include the following several kinds：
    // ACTION_GATT_CONNECTED: Connected to GATT
    // ACTION_GATT_DISCONNECTED: Disconected from GATT
    // ACTION_GATT_SERVICES_DISCOVERED: Discover services under GATT
    // ACTION_DATA_AVAILABLE: BLE receives the data
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            int reCmd=-2;

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                resetButtonValue(R.string.Disconnect);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                resetButtonValue(R.string.Connect);
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

                // Show all the supported services and characteristics on the user interface.
                // Access to all the GATT services, for Crazepony BLE transparent
                // transmission module, including GAP (General Access Profile),
                // GATT（General Attribute Profile），There Unknown (for data reading)
                mBluetoothLeService.getSupportedGattServices();

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                final byte[] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

                if (data != null && data.length > 0) {
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for(byte byteChar : data)
                        stringBuilder.append(String.format("%02X ", byteChar));

                    Log.i(TAG, "RX Data:"+stringBuilder);
                }


                //The resulting data is parsed and the MSP command number is obtained
                reCmd=Protocol.processDataIn( data,data.length);
                updateLogData(1);   // Update the IMU data, update the IMU data
            }
        }
    };

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            try {
                timeHandler.postDelayed(this, WRITE_DATA_PERIOD);

                if(IMU_CNT >= 10){
                    IMU_CNT = 0;
                    //request for IMU data update，Request IMU update
                    btSendBytes(Protocol.getSendData(Protocol.FLY_STATE,
                            Protocol.getCommandData(Protocol.FLY_STATE)));
                }
                IMU_CNT++;


                // process stick movement，Processing remote sensing data
                if(stickView.touchReadyToSend==true){
                    btSendBytes(Protocol.getSendData(Protocol.SET_4CON,
                            Protocol.getCommandData(Protocol.SET_4CON)));

                    Log.i(TAG,"Thro: " +Protocol.throttle +",yaw: " +Protocol.yaw+ ",roll: "
                            + Protocol.roll +",pitch: "+ Protocol.pitch);

                    stickView.touchReadyToSend=false;
                }

                // Update the display of remote sensing data，update the joystick data
                updateLogData(0);

            } catch (Exception e) {

            }
        }
    };


    // The Settings button appears as the default initialization value
    // The connection is successful or disconnected, you need to reset the value of button
    private void resetButtonValue(final int connectBtnId) {
        connectButton.setText("CONNECTED");
        connectButton.setTextColor(Color.GREEN);
        armButton.setText(R.string.Unarm);
        lauchLandButton.setText(R.string.Launch);
        headFreeButton.setTextColor(Color.WHITE);
        altHoldButton.setTextColor(Color.WHITE);
    }



	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(main); // The setting screen is the main screen main.xml
		// display text
		throttleText = (TextView)findViewById(R.id.throttleText); // 
		yawText = (TextView)findViewById(R.id.yawText);
		pitchText = (TextView)findViewById(R.id.pitchText);
		rollText = (TextView)findViewById(R.id.rollText);
		//pitchAngText,rollAngText,yawAngText,altText,voltageText
		pitchAngText = (TextView)findViewById(R.id.pitchAngText);
		rollAngText = (TextView)findViewById(R.id.rollAngText);
		yawAngText = (TextView)findViewById(R.id.yawAngText);
		altText = (TextView)findViewById(R.id.altText);
		voltageText = (TextView)findViewById(R.id.voltageText);
		distanceText= (TextView)findViewById(R.id.distanceText);
		 
		// Rocker
//		stickView=(MySurfaceView)findViewById(R.id.stickView);

		// Button
        connectButton=(Button)findViewById(R.id.connectButton);
		armButton=(Button)findViewById(R.id.armButton);
		lauchLandButton=(Button)findViewById(R.id.lauchLandButton);
		headFreeButton=(Button)findViewById(R.id.headFreeButton);
		altHoldButton=(Button)findViewById(R.id.altHoldButton);

        // Bind the BLE transceiver service mServiceConnection
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Enable the IMU data update timer
        timeHandler.postDelayed(runnable, WRITE_DATA_PERIOD); // Executed every 1s

        onConnectButtonClicked(findViewById(R.id.connectButton));
	}

    @Override
    protected void onResume() {
        super.onResume();

        // Register BLE transceiver service receiver macUpdate Receiver
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            Log.d(TAG, "mBluetoothLeService NOT null");
        }

    }

	@Override
	public void onPause()
	{
		super.onPause();
        // Unregister BLE Transceiver Receiver macUpdate Receiver
        unregisterReceiver(mGattUpdateReceiver);
	}
	@Override
	public void onStop()
	{
		super.onStop();
	}

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unbind the BLE transceiver service mServiceConnection
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }


    //speechButton = (ImageButton) findViewById(R.id.speech);
    public void onSpeechButtonClicked(View v)
    {
        if(isConnected()){
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            startActivityForResult(intent, ACTION_RECOGNIZE_SPEECH);
        }
        else{
            Toast.makeText(getApplicationContext(), "Plese Connect to Internet", Toast.LENGTH_LONG).show();
        }
    }

    // Connect the button response function
    public void onConnectButtonClicked(View v)
    {
        if (!mConnected) {
            // Go to the Scan page
            Intent serverIntent = new Intent(this, DeviceScanActivity.class); // Jump program settings
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE); // Set Returns the macro definition
        } else {
            // Disconnect
            mBluetoothLeService.disconnect();
        }
    }


    public void onSendArmButtonClicked(View v)
	{
        String arm = getResources().getString(R.string.Arm);
        String unarm = getResources().getString(R.string.Unarm);
        String disconnectToast = getResources().getString(R.string.DisconnectToast);

		if(mConnected){
			if(armButton.getText() != arm)	{
				btSendBytes(Protocol.getSendData(Protocol.ARM_IT, Protocol.getCommandData(Protocol.ARM_IT)));
				armButton.setText(arm);
			}else{
				btSendBytes(Protocol.getSendData(Protocol.DISARM_IT, Protocol.getCommandData(Protocol.DISARM_IT)));
				armButton.setText(unarm);
			}
		}else {
            Toast.makeText(this, disconnectToast, Toast.LENGTH_SHORT).show();
        }
	} 
	
	//Take off , land down
	public void onlauchLandButtonClicked(View v)
	{
        String launch  = getResources().getString(R.string.Launch);
        String land = getResources().getString(R.string.Land);
        String disconnectToast = getResources().getString(R.string.DisconnectToast);

        if(mConnected){
            if(!lauchLandButton.getText().equals(land)){
                btSendBytes(Protocol.getSendData(Protocol.LAUCH, Protocol.getCommandData(Protocol.LAUCH)));
                lauchLandButton.setText(land);
                Protocol.throttle=Protocol.LAUCH_THROTTLE;
                Protocol.throttle += 100;
//                stickView.SmallRockerCircleY=stickView.rc2StickPosY(Protocol.throttle);
//                stickView.touchReadyToSend=true;
            }else{
                btSendBytes(Protocol.getSendData(Protocol.LAND_DOWN, Protocol.getCommandData(Protocol.LAND_DOWN)));
                lauchLandButton.setText(launch);
                Protocol.throttle=Protocol.LAND_THROTTLE;
//                stickView.SmallRockerCircleY=stickView.rc2StickPosY(Protocol.throttle);
//                stickView.touchReadyToSend=true;
            }
        }else {
            Toast.makeText(this, disconnectToast, Toast.LENGTH_SHORT).show();
        }
	}

	// Headless mode key
	public void onheadFreeButtonClicked(View v)
	{
        String disconnectToast = getResources().getString(R.string.DisconnectToast);

		if(mConnected){
			if(headFreeButton.getCurrentTextColor()!=Color.GREEN)
			{	btSendBytes(Protocol.getSendData(Protocol.HEAD_FREE, Protocol.getCommandData(Protocol.HEAD_FREE)));
				headFreeButton.setTextColor(Color.GREEN);
			}else{
				btSendBytes(Protocol.getSendData(Protocol.STOP_HEAD_FREE, Protocol.getCommandData(Protocol.STOP_HEAD_FREE)));
				headFreeButton.setTextColor(Color.WHITE);
			}
		}else {
            Toast.makeText(this, disconnectToast, Toast.LENGTH_SHORT).show();
        }
		
	}

	// set hover
	public void onaltHoldButtonClicked(View v)
	{
        String disconnectToast = getResources().getString(R.string.DisconnectToast);

		if(mConnected){
			if( altHoldButton.getCurrentTextColor()!=Color.GREEN )
			{	// Fixed high-point are open
				btSendBytes(Protocol.getSendData(Protocol.HOLD_ALT, Protocol.getCommandData(Protocol.HOLD_ALT))); 
				altHoldButton.setTextColor(Color.GREEN);
				stickView.altCtrlMode=1;
			}else{
				btSendBytes(Protocol.getSendData(Protocol.STOP_HOLD_ALT, Protocol.getCommandData(Protocol.STOP_HOLD_ALT)));
				altHoldButton.setTextColor(Color.WHITE); 
				stickView.altCtrlMode=0;
			}
		}else {
            Toast.makeText(this, disconnectToast, Toast.LENGTH_SHORT).show();
        }
	}
	
	// calibration
	public void onAccCaliButtonClicked(View v)
	{
        String disconnectToast = getResources().getString(R.string.DisconnectToast);

		if(mConnected){
			btSendBytes(Protocol.getSendData(Protocol.MSP_ACC_CALIBRATION, Protocol.getCommandData(Protocol.MSP_ACC_CALIBRATION)));
		}else {
            Toast.makeText(this, disconnectToast, Toast.LENGTH_SHORT).show();
        }
	}

	public void btSendBytes(byte[] data)
	{
        // Sent when already connected
		if(mConnected){
            mBluetoothLeService.writeCharacteristic(data);
		}
	}
	
	// Receive scan results in response to startActivityForResult ()
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == Activity.RESULT_OK){
                mDeviceName = "Crazepony";
                mDeviceAddress = "E0:E5:CF:CD:DF:3C";

                Log.i(TAG, "mDeviceName:"+mDeviceName+",mDeviceAddress:"+mDeviceAddress);

                // Connect the BLE Crazepony module
                if (mBluetoothLeService != null) {
                    final boolean result = mBluetoothLeService.connect(mDeviceAddress);
                    Log.d(TAG, "Connect request result=" + result);
                }
            }
            super.onActivityResult(requestCode, resultCode, data);
			break;
        case ACTION_RECOGNIZE_SPEECH:
            // call function to handle commands
            matches_text = data
                    .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            Log.d("VoiceTest",matches_text.toString());

            String command = "";
            if (matches_text.contains("up")) {
                Toast.makeText(getApplicationContext(), matches_text.get(matches_text.indexOf("up")) , Toast.LENGTH_LONG).show();
                command = "Up";
            } else if (matches_text.contains("down")) {
                Toast.makeText(getApplicationContext(), matches_text.get(matches_text.indexOf("down")) , Toast.LENGTH_LONG).show();
                command = "Down";
            } else if (matches_text.contains("land")) {
                Toast.makeText(getApplicationContext(), matches_text.get(matches_text.indexOf("land")) , Toast.LENGTH_LONG).show();
                onlauchLandButtonClicked(findViewById(R.id.lauchLandButton));
                command = "Land";
            } else if (matches_text.contains("launch")) {
//                Toast.makeText(getApplicationContext(), matches_text.get(matches_text.indexOf("Launch")) , Toast.LENGTH_LONG).show();
                onSendArmButtonClicked(findViewById(R.id.lauchLandButton));
                command = "Launch";
            } else {
                Toast.makeText(getApplicationContext(), "No such command!" , Toast.LENGTH_LONG).show();
            }
            Toast.makeText(getApplicationContext(), command + " Command Received", Toast.LENGTH_LONG).show();
//            startActivityForResult(intent, ACTION_RECOGNIZE_SPEECH);
            break;
		default:
			break;

		}

	}

    // Update the Log-related data, mainly fly control over the IMU data and joystick value data
    //update log,included the IMU data from FC and joysticks data
    //msg 0 -> joystick data
    //msg 1 -> IMU data
    private void updateLogData(int msg){
        if(0 == msg)
        {
            throttleText.setText("Throttle:"+Integer.toString(Protocol.throttle));
            yawText.setText("Yaw:"+Integer.toString(Protocol.yaw));
            pitchText.setText("Pitch:"+Integer.toString(Protocol.pitch));
            rollText.setText("Roll:"+Integer.toString(Protocol.roll));
        }
        else if(1 == msg)
        {
            pitchAngText.setText("Pitch Ang: "+Protocol.pitchAng);
            rollAngText.setText("Roll Ang: "+Protocol.rollAng);
            yawAngText.setText("Yaw Ang: "+Protocol.yawAng);
            altText.setText("Alt:"+Protocol.alt + "m");

            voltageText.setText("Voltage:"+Protocol.voltage + " V");
            distanceText.setText("speedZ:"+Protocol.speedZ + "m/s");
        }
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public boolean isConnected()
    {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo net = cm.getActiveNetworkInfo();
        return net != null && net.isAvailable() && net.isConnected();
    }
}