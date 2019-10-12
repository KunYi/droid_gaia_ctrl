/* ************************************************************************************************
 * Copyright 2017 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/

package com.qualcomm.qti.gaiacontrol.activities;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Message;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.qualcomm.qti.gaiacontrol.R;
import com.qualcomm.qti.gaiacontrol.gaia.FindMeGaiaManager;
import com.qualcomm.qti.gaiacontrol.models.gatt.GATT;
import com.qualcomm.qti.gaiacontrol.models.gatt.GATTServices;
import com.qualcomm.qti.gaiacontrol.services.BluetoothService;
import com.qualcomm.qti.libraries.gaia.GAIA;

/**
 * <p>This activity is for demonstration of the Find Me Profile over BLE.</p>
 * <p>It also implements the FindMeGaiaManager in order to demonstrate the Find Me Profile of a connected remote
 * control to the device. Also works with BR/EDR.</p>
 */
public class FindMeActivity extends ServiceActivity implements FindMeGaiaManager.FindMeGaiaManagerListener {

    // ====== CONSTS FIELDS =======================================================================

    /**
     * For debug mode, the tag to display for logs.
     */
    @SuppressWarnings("unused")
    private static final String TAG = "FindMeActivity";


    // ====== PRIVATE FIELDS =======================================================================

    /**
     * <p>To manage the GAIA packets which have been received from the device and which will be sent to the device.</p>
     */
    private FindMeGaiaManager mGaiaManager;


    // ====== UI PRIVATE FIELDS =======================================================================

    /**
     * <p>The layout which groups the Link Loss alert settings.</p>
     */
    private View mLayoutFindMeAlertLevel;
    /**
     * <p>The message to display if the Link Loss service is considered as unavailable.</p>
     */
    private View mViewFindMeAlertLevelUnavailable;
    /**
     * <p>The layout which groups the alert levels settings for the Find My Remote feature.</p>
     */
    private View mLayoutFindMyRemoteAlertLevel;
    /**
     * <p>The message to display if the Find My Remote feature is considered as not supported.</p>
     */
    private View mViewFindMyRemoteAlertLevelUnavailable;
    /**
     * <p>The whole layout of this view.</p>
     */
    private View mMainLayout;
    /**
     * <p>The snack bar alert to display an error/alert message.</p>
     */
    private TextView mBarAlertDisconnection;


    // ====== ACTIVITY METHODS =======================================================================

    @Override // Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_me);
        this.init();
    }


    // ====== SERVICE METHODS =======================================================================

    @Override // ServiceActivity
    protected void handleMessageFromService(Message msg) {
        //noinspection UnusedAssignment
        String handleMessage = "Handle a message from BLE service: ";

        switch (msg.what) {
            case BluetoothService.Messages.CONNECTION_STATE_HAS_CHANGED:
                @BluetoothService.State int connectionState = (int) msg.obj;
                onConnectionStateChanged(connectionState);
                String stateLabel = connectionState == BluetoothService.State.CONNECTED ? "CONNECTED"
                        : connectionState == BluetoothService.State.CONNECTING ? "CONNECTING"
                        : connectionState == BluetoothService.State.DISCONNECTING ? "DISCONNECTING"
                        : connectionState == BluetoothService.State.DISCONNECTED ? "DISCONNECTED"
                        : "UNKNOWN";
                displayLongToast(getString(R.string.toast_device_information) + stateLabel);
                if (DEBUG) Log.d(TAG, handleMessage + "CONNECTION_STATE_HAS_CHANGED: " + stateLabel);
                break;

            case BluetoothService.Messages.DEVICE_BOND_STATE_HAS_CHANGED:
                int bondState = (int) msg.obj;
                String bondStateLabel = bondState == BluetoothDevice.BOND_BONDED ? "BONDED"
                        : bondState == BluetoothDevice.BOND_BONDING ? "BONDING"
                        : "BOND NONE";
                displayLongToast(getString(R.string.toast_device_information) + bondStateLabel);
                if (DEBUG) Log.d(TAG, handleMessage + "DEVICE_BOND_STATE_HAS_CHANGED: " + bondStateLabel);
                break;

            case BluetoothService.Messages.GATT_SUPPORT:
                GATTServices services = (GATTServices) msg.obj;
                onGattSupport(services);
                if (DEBUG) Log.d(TAG, handleMessage + "GATT_SUPPORT");
                break;

            case BluetoothService.Messages.GAIA_PACKET:
                if (DEBUG) Log.d(TAG, handleMessage + "GAIA_PACKET");
                break;

            case BluetoothService.Messages.GAIA_READY:
                if (DEBUG) Log.d(TAG, handleMessage + "GAIA_READY");
                break;

            case BluetoothService.Messages.GATT_READY:
                onGattReady();
                if (DEBUG) Log.d(TAG, handleMessage + "GATT_READY");
                break;

            case BluetoothService.Messages.GATT_MESSAGE:
                if (DEBUG) Log.d(TAG, handleMessage + "GATT_MESSAGE");
                break;

            case BluetoothService.Messages.UPGRADE_MESSAGE:
                if (DEBUG) Log.d(TAG, handleMessage + "UPGRADE_MESSAGE");
                break;

            default:
                if (DEBUG)
                    Log.d(TAG, handleMessage + "UNKNOWN MESSAGE: " + msg.what);
                break;
        }
    }

    @Override // ServiceActivity
    protected void onServiceConnected() {
        if (mService != null) {
            GATTServices support = mService.getGattSupport();
            initInformation(support);
            if (support == null || !support.isGattProfileFindMeSupported()) {
                displayLongToast(R.string.toast_find_me_not_supported);
            }
            @GAIA.Transport int transport = getTransport() == BluetoothService.Transport.BR_EDR ?
                    GAIA.Transport.BR_EDR : GAIA.Transport.BLE;
            mGaiaManager = new FindMeGaiaManager(this, transport);
        }
    }

    @Override // ServiceActivity
    protected void onServiceDisconnected() {

    }


    // ====== GAIA MANAGER METHODS ======================================================================

    @Override // FindMeGaiaManager.FindMeGaiaManagerListener
    public boolean sendGAIAPacket(byte[] packet) {
        return mService!= null && mService.sendGAIAPacket(packet);
    }

    @Override // FindMeGaiaManager.FindMeGaiaManagerListener
    public void onFindMyRemoteNotSupported() {
        // the Find My Remote is not supported
        mLayoutFindMyRemoteAlertLevel.setVisibility(View.GONE);
        mViewFindMyRemoteAlertLevelUnavailable.setVisibility(View.VISIBLE);
    }


    // ====== PRIVATE METHODS =================================================================

    /**
     * <p>To initialise all information which is displayed.</p>
     * <p>This method will show or hide the settings which are known as available and sets them up to their known
     * value.</p>
     *
     * @param support The GATT services which are supported by the device in order to know the available settings for
     * the proximity profile.
     */
    private void initInformation(GATTServices support) {
        if (support != null && support.isGattProfileFindMeSupported()) {
            // GATT service immediate alert is supported as it is mandatory for the Find Me profile
            mLayoutFindMeAlertLevel.setVisibility(View.VISIBLE);
            mViewFindMeAlertLevelUnavailable.setVisibility(View.GONE);
        }
        else if (support != null /*&& !support.isGattProfileFindMeSupported()*/) {
            // the Find Me Profile is not supported
            mLayoutFindMeAlertLevel.setVisibility(View.GONE);
            mViewFindMeAlertLevelUnavailable.setVisibility(View.VISIBLE);
        }
    }

    /**
     * <p>This method is called when the Service dispatches a CONNECTION_STATE_HAS_CHANGED message.</p>
     * <p>This method will disable all UI components when the device is known as not connected - disconnecting,
     * connecting, disconnected. It will also disable any ongoing notifications and will display a connection state
     * alert.</p>
     * <p>If the device is disconnected this method will request to the GAIAGATTBLEService to reconnect to the device.</p>
     *
     * @param connectionState The new connection state
     */
    private void onConnectionStateChanged(int connectionState) {
        // automatic reconnection for when the device will be available
        if (connectionState == BluetoothService.State.DISCONNECTED) {
            mService.reconnectToDevice();
        }

        boolean connected = connectionState == BluetoothService.State.CONNECTED;

        // show or hide the alert message
        int visibility = connected ? View.GONE : View.VISIBLE;
        mBarAlertDisconnection.setVisibility(visibility);

        if (!connected) {
            // UI
            enableChildView(mMainLayout, false);
            mBarAlertDisconnection.setVisibility(View.VISIBLE);
            int message = connectionState == BluetoothService.State.DISCONNECTED ? R.string.alert_message_disconnected :
                             connectionState == BluetoothService.State.DISCONNECTING ? R.string.alert_message_disconnecting :
                             connectionState == BluetoothService.State.CONNECTING ? R.string.alert_message_connecting :
                                     R.string.alert_message_connection_state_unknown;
            mBarAlertDisconnection.setText(message);
        }

    }

    /**
     * <p>This method is called when the GATT connection can be used to communicate with the device.</p>
     * <p>This method enables all the UI components to let the user interact with them.</p>
     */
    private void onGattReady() {
        if (mService.getConnectionState() == BluetoothService.State.CONNECTED) {
            enableChildView(mMainLayout, true);
        }
    }

    /**
     * <p>This method is called when the GAIAGATTBLEService knows the services the device supports.</p>
     * <p>This method initialises the UI with the known information.</p>
     *
     * @param services the object which describes the supported services.
     */
    private void onGattSupport(GATTServices services) {
        if (mService.getConnectionState() == BluetoothService.State.CONNECTED) {
            initInformation(services);
        }
    }


    // ====== UI METHODS ======================================================================

    /**
     * To initialise objects used in this activity.
     */
    private void init() {
        // manage the action bar
        this.setSupportActionBar((Toolbar) findViewById(R.id.tb_menu));
        //noinspection ConstantConditions
        this.getSupportActionBar().setLogo(R.drawable.ic_find_32dp);
        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        this.getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_back_24dp);

        mLayoutFindMeAlertLevel = findViewById(R.id.ll_find_me_alert_levels);
        mViewFindMeAlertLevelUnavailable = findViewById(R.id.tv_find_me_alert_level_unavailable);
        mLayoutFindMyRemoteAlertLevel = findViewById(R.id.ll_find_my_remote_alert_levels);
        mViewFindMyRemoteAlertLevelUnavailable = findViewById(R.id.tv_find_my_remote_alert_level_unavailable);
        mMainLayout = findViewById(R.id.layout_find_me_main);
        mBarAlertDisconnection = (TextView) findViewById(R.id.tv_bar_alert_connection_state);

        mLayoutFindMeAlertLevel.findViewById(R.id.bt_level_none).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mService.sendImmediateAlertLevel(GATT.AlertLevel.Levels.NONE)) {
                    displayLongToast(R.string.toast_find_me_failed);
                }
            }
        });
        mLayoutFindMeAlertLevel.findViewById(R.id.bt_level_mild).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mService.sendImmediateAlertLevel(GATT.AlertLevel.Levels.MILD)) {
                    displayLongToast(R.string.toast_find_me_failed);
                }
            }
        });
        mLayoutFindMeAlertLevel.findViewById(R.id.bt_level_high).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mService.sendImmediateAlertLevel(GATT.AlertLevel.Levels.HIGH)) {
                    displayLongToast(R.string.toast_find_me_failed);
                }
            }
        });

        mLayoutFindMyRemoteAlertLevel.findViewById(R.id.bt_level_none).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mGaiaManager.setFindMyRemoteAlertLevel(FindMeGaiaManager.Levels.NO_ALERT);
            }
        });
        mLayoutFindMyRemoteAlertLevel.findViewById(R.id.bt_level_mild).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mGaiaManager.setFindMyRemoteAlertLevel(FindMeGaiaManager.Levels.MILD_ALERT);            }
        });
        mLayoutFindMyRemoteAlertLevel.findViewById(R.id.bt_level_high).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mGaiaManager.setFindMyRemoteAlertLevel(FindMeGaiaManager.Levels.HIGH_ALERT);
            }
        });
    }

    /**
     * <p>This method allows all the child components of the given view to be enabled or disabled. The given view is
     * only disabled if it is not a ViewGroup.</p>
     *
     * @param view
     *          The view which all child components should be deactivated.
     * @param enabled
     *          True to enable the view, false to disable it.
     */
    private void enableChildView(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                enableChildView(child, enabled);
            }
        }
    }
	
}
