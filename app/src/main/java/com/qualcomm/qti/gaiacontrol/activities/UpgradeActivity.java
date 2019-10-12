/* ************************************************************************************************
 * Copyright 2017 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/

package com.qualcomm.qti.gaiacontrol.activities;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.qualcomm.qti.gaiacontrol.Consts;
import com.qualcomm.qti.gaiacontrol.R;
import com.qualcomm.qti.gaiacontrol.Utils;
import com.qualcomm.qti.gaiacontrol.services.BluetoothService;
import com.qualcomm.qti.gaiacontrol.services.GAIAGATTBLEService;
import com.qualcomm.qti.gaiacontrol.ui.VMUpgradeDialog;
import com.qualcomm.qti.gaiacontrol.ui.fragments.FilePickerFragment;
import com.qualcomm.qti.gaiacontrol.ui.fragments.UpgradeOptionsFragment;
import com.qualcomm.qti.libraries.ble.BLEService;
import com.qualcomm.qti.libraries.vmupgrade.UpgradeError;
import com.qualcomm.qti.libraries.vmupgrade.UpgradeManager;
import com.qualcomm.qti.libraries.vmupgrade.codes.ResumePoints;
import com.qualcomm.qti.libraries.vmupgrade.codes.ReturnCodes;

import java.io.File;

/**
 * <p>This Activity controls the upgrade UI information through the {@link VMUpgradeDialog VMUpgradeDialog}. It also
 * provides a file explorer by the use of the {@link FilePickerFragment FilePickerFragment} fragment in order to let
 * the user pick a file to upgrade the board.</p>
 * <p>The upgrade process is directly managed by the Service.</p>
 */
public class UpgradeActivity extends ServiceActivity implements FilePickerFragment.FilePickerFragmentListener,
        VMUpgradeDialog.UpgradeDialogListener, UpgradeOptionsFragment.UpgradeOptionsFragmentListener {

    // ====== CONSTS ===============================================================================

    /**
     * For debug mode, the tag to display for logs.
     */
    @SuppressWarnings("unused")
    private final String TAG = "UpgradeActivity";
    /**
     * The key used by this application to keep the time of the upgrade start.
     */
    private final static String START_TIME_KEY = "START_TIME";


    // ====== PRIVATE FIELDS =======================================================================

    /**
     * The fragment used to display the file explorer.
     */
    private FilePickerFragment mFilePickerFragment;
    /**
     * The fragment used to display the file explorer.
     */
    private UpgradeOptionsFragment mOptionsFragment;
    /**
     * The dialog to display during the upgrade.
     */
    private VMUpgradeDialog mUpgradeDialog;
    /**
     * The dialog which is used during the device reconnection.
     */
    private AlertDialog mDialogReconnection;
    /**
     * Contains the file which had been selected to process the update.
     */
    private File mFile = null;
    /**
     * To know the state of the RWCP mode: enabled or disabled.
     */
    private boolean mIsRWCPEnabled = false;
    /**
     * The start time of the upgrade. If there is no upgrade going in, this field has no meaning.
     */
    private long mStartTime = 0;


    // ====== ACTIVITY METHODS =====================================================================

    @Override // Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_file);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // to keep screen on during update
        this.init();
    }

    @Override // Activity
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (mStartTime != 0) {
            savedInstanceState.putLong(START_TIME_KEY, mStartTime);
        }
    }

    @Override // Activity
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Restore UI state saved with savedInstanceState
        if (mStartTime == 0 && savedInstanceState.containsKey(START_TIME_KEY)) {
            mStartTime = savedInstanceState.getLong(START_TIME_KEY);
        }
    }

    // When the activity is resumed.
    @Override // FragmentActivity
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mService != null) {
            Fragment fragment = mService.getTransport() == BluetoothService.Transport.BLE ? mOptionsFragment
                    : mService.getTransport() == BluetoothService.Transport.BR_EDR ? mFilePickerFragment
                    : null;
            displayFragment(fragment);
            mService.enableUpgrade(true);
            restoreUpgradeDialog();
        }
    }

    @Override // Activity
    protected void onPause() {
        super.onPause();
        if (mService != null && !mService.isUpgrading()) {
            mService.enableUpgrade(false);
        }
    }


    // ====== SERVICE METHODS =====================================================================

    @Override // ServiceActivity
    protected void  handleMessageFromService(Message msg) {
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
                if (mService == null || !mService.isUpgrading()) {
                    displayLongToast(getString(R.string.toast_device_information) + stateLabel);
                }
                if (DEBUG) Log.d(TAG, handleMessage + "CONNECTION_STATE_HAS_CHANGED: " + stateLabel);
                break;

            case BluetoothService.Messages.DEVICE_BOND_STATE_HAS_CHANGED:
                int bondState = (int) msg.obj;
                String bondStateLabel = bondState == BluetoothDevice.BOND_BONDED ? "BONDED"
                        : bondState == BluetoothDevice.BOND_BONDING ? "BONDING"
                        : "BOND NONE";
                if (mService == null || !mService.isUpgrading()) {
                    displayLongToast(getString(R.string.toast_device_information) + bondStateLabel);
                }
                if (DEBUG) Log.d(TAG, handleMessage + "DEVICE_BOND_STATE_HAS_CHANGED: " + bondStateLabel);
                break;

            case BluetoothService.Messages.GATT_SUPPORT:
                if (DEBUG) Log.d(TAG, handleMessage + "GATT_SUPPORT");
                break;

            case BluetoothService.Messages.GAIA_PACKET:
                break;

            case BluetoothService.Messages.GAIA_READY:
                if (DEBUG) Log.d(TAG, handleMessage + "GAIA_READY");
                break;

            case BluetoothService.Messages.GATT_READY:
                if (DEBUG) Log.d(TAG, handleMessage + "GATT_READY");
                break;

            case BluetoothService.Messages.UPGRADE_MESSAGE:
                @BluetoothService.UpgradeMessage int upgradeMessage = msg.arg1;
                Object content = msg.obj;
                onReceiveUpgradeMessage(upgradeMessage, content);
                break;

            case BluetoothService.Messages.GATT_MESSAGE:
                @GAIAGATTBLEService.GattMessage int gattMessage = msg.arg1;
                Object data = msg.obj;
                onReceiveGattMessage(gattMessage, data);
                if (DEBUG) Log.d(TAG, handleMessage + "GATT_MESSAGE");
                break;

            default:
                if (DEBUG)
                    Log.d(TAG, handleMessage + "UNKNOWN MESSAGE: " + msg.what);
                break;
        }
    }

    @Override // ServiceActivity
    protected void onServiceConnected() {
        mService.enableUpgrade(true);
        mService.enableDebugLogs(Consts.DEBUG);
        Fragment fragment = mService.getTransport() == BluetoothService.Transport.BLE ? mOptionsFragment
                : mService.getTransport() == BluetoothService.Transport.BR_EDR ? mFilePickerFragment
                : null;
        displayFragment(fragment);

        if (mService.getTransport() == BluetoothService.Transport.BLE) {
            ((GAIAGATTBLEService) mService).getRWCPStatus();
        }

        restoreUpgradeDialog();
    }

    @Override // ServiceActivity
    protected void onServiceDisconnected() {
        mService.enableUpgrade(false);
        mService.enableDebugLogs(Consts.DEBUG);
    }


    // ====== OVERRIDE METHODS =====================================================================

    @Override // FilePickerFragment.FilePickerFragmentListener
    public void onFilePicked(File file) {
        mFile = file;

        if (mService.getTransport() == BluetoothService.Transport.BLE) {
            displayFragment(mOptionsFragment);
        }
        else {
            startUpgrade(file);
        }
    }

    @Override // FilePickerFragment.FilePickerFragmentListener
    public int getPickerFileButtonLabel() {
        return mService.getTransport() == BluetoothService.Transport.BLE ? R.string.button_choose
                : R.string.button_start_upgrade;
    }

    /**
     * @see VMUpgradeDialog.UpgradeDialogListener#abortUpgrade()
     * This implementation does not check if the VMUpgradeDialog has been dismissed or is still displayed.
     */
    @Override // VMUpgradeDialog.UpgradeDialogListener
    public void abortUpgrade() {
        mService.abortUpgrade();
    }

    @Override // VMUpgradeDialog.UpgradeDialogListener
    public long getStartTime() {
        return mStartTime;
    }

    @Override // UpgradeOptionsFragment.UpgradeOptionsFragmentListener
    public boolean enableRWCP(boolean enabled) {
        boolean done = (mService.getTransport() == BluetoothService.Transport.BLE)
                && ((GAIAGATTBLEService) mService).enableRWCP(enabled);
        if (!done) {
            int text = enabled ? R.string.toast_rwcp_failed_enabled : R.string.toast_rwcp_failed_disabled;
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        }
        return done;
    }

    @Override // UpgradeOptionsFragment.UpgradeOptionsFragmentListener
    public boolean enableMaximumMTU(boolean enabled) {
        boolean done = (mService.getTransport() == BluetoothService.Transport.BLE)
                && ((GAIAGATTBLEService) mService).enableMaximumMTU(enabled);
        if (!done) {
            int text = enabled ? R.string.toast_mtu_failed_enabled : R.string.toast_mtu_failed_disabled;
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        }
        return done;
    }

    @Override // UpgradeOptionsFragment.UpgradeOptionsFragmentListener
    public File getFile() {
        return mFile;
    }

    @Override // UpgradeOptionsFragment.UpgradeOptionsFragmentListener
    public void showMTUDialog(final boolean enabledMTU) {
        AlertDialog.Builder builder = new AlertDialog.Builder(UpgradeActivity.this);
        builder.setTitle(R.string.alert_mtu_title)
                .setMessage(R.string.alert_mtu_message);
        builder.setCancelable(true);
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                mOptionsFragment.onMTUDialogCancelled();
            }
        });
        builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mOptionsFragment.onMTUDialogCancelled();
            }
        });
        builder.setPositiveButton(R.string.button_confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                enableMaximumMTU(enabledMTU);
            }
        });

        builder.create().show();
    }

    @Override // UpgradeOptionsFragment.UpgradeOptionsFragmentListener
    public void pickFile() {
        displayFragment(mFilePickerFragment);
    }

    @Override // UpgradeOptionsFragment.UpgradeOptionsFragmentListener
    public void startUpgrade() {
        startUpgrade(mFile);
    }

    @Override // UpgradeOptionsFragment.UpgradeOptionsFragmentListener
    public boolean isRWCPEnabled() {
        return mIsRWCPEnabled;
    }

    @Override // UpgradeOptionsFragment.UpgradeOptionsFragmentListener
    public void enableDebugLogs(boolean enable) {
        mService.enableDebugLogs(enable);
    }


    // ====== PRIVATE METHODS ======================================================================

    /**
     * To initialise objects used in this activity.
     */
    private void init() {
        this.setSupportActionBar((Toolbar) findViewById(R.id.toolbar_menu));
        ActionBar actionBar = this.getSupportActionBar();
        {
            assert actionBar != null;
            this.getSupportActionBar().setLogo(R.drawable.ic_upload_32dp);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_back_24dp);
        }

        mFilePickerFragment = FilePickerFragment.newInstance();
        mOptionsFragment = UpgradeOptionsFragment.newInstance();

        initDialog();
    }

    /**
     * <p>This method starts the upgrade process through the {@link BLEService Service} and displays the VM Upgrade
     * dialog to show the upgrade process.</p>
     *
     * @param file The file to use for the upgrade. If the file is null, this activity displays an error to the user.
     *
     */
    private void startUpgrade(File file) {
        if (file != null) {
            mService.startUpgrade(file);
            showUpgradeDialog(true);
        }
        else {
            displayFileError();
        }
    }

    /**
     * <p>This method is called when the service has informed the activity that the device connection state has
     * changed.</p>
     *
     * @param state
     *        The new connection state sent by the service.
     */
    private void onConnectionStateChanged(@BluetoothService.State int state) {
        showUpgradingDialogs(state);
    }

    /**
     * <p>This method is called when the service informs the activity about updates of the upgrade process.
     * Depending on the type of update information, this method acts as follows:
     * <ul>
     *     <li>{@link BluetoothService.UpgradeMessage#UPGRADE_STEP_HAS_CHANGED
     *     UPGRADE_STEP_HAS_CHANGED}: updates the step information of the VM Upgrade Dialog through the
     *     method {@link VMUpgradeDialog#updateStep(int) updateStep}.</li>
     *     <li>{@link BluetoothService.UpgradeMessage#UPGRADE_UPLOAD_PROGRESS
     *     UPGRADE_UPLOAD_PROGRESS}: updates the progress bar information through the method
     *     {@link VMUpgradeDialog#displayTransferProgress(double) displayTransferProgress}</li>
     *     <li>{@link BluetoothService.UpgradeMessage#UPGRADE_REQUEST_CONFIRMATION
     *     UPGRADE_REQUEST_CONFIRMATION}: displays a dialog to the user to ask their confirmation to continue the
     *     process.</li>
     *     <li>{@link BluetoothService.UpgradeMessage#UPGRADE_FINISHED
     *     UPGRADE_FINISHED}: informs the user that the upgrade had successfully finished.</li>
     *     <li>{@link BluetoothService.UpgradeMessage#UPGRADE_ERROR UPGRADE_ERROR}:
     *     this method will handle the error through the method {@link #manageError(UpgradeError) manageError}
     *     which will let the user knows.</li>
     * </ul></p>
     *
     * @param message
     *          The type of Upgrade message the Service wants the activity to have an update about.
     * @param content
     *          The complementary information corresponding to the message.
     */
    private void onReceiveUpgradeMessage(@BluetoothService.UpgradeMessage int message, Object content) {
        StringBuilder handleMessage = new StringBuilder("Handle a message from BLE service: UPGRADE_MESSAGE, ");
        switch (message) {
            case BluetoothService.UpgradeMessage.UPGRADE_FINISHED:
                displayUpgradeComplete();
                mStartTime = 0;
                handleMessage.append("UPGRADE_FINISHED");
                break;

            case BluetoothService.UpgradeMessage.UPGRADE_REQUEST_CONFIRMATION:
                @UpgradeManager.ConfirmationType int confirmation = (int) content;
                askForConfirmation(confirmation);
                handleMessage.append("UPGRADE_REQUEST_CONFIRMATION");
                break;

            case BluetoothService.UpgradeMessage.UPGRADE_STEP_HAS_CHANGED:
                @ResumePoints.Enum int step = (int) content;
                if (step == ResumePoints.Enum.DATA_TRANSFER
                        && mStartTime == 0 /* step does not change due to a reconnection */) {
                    mStartTime = SystemClock.elapsedRealtime();
                }
                mUpgradeDialog.updateStep(step);
                handleMessage.append("UPGRADE_STEP_HAS_CHANGED");
                break;

            case BluetoothService.UpgradeMessage.UPGRADE_ERROR:
                UpgradeError error = (UpgradeError) content;
                mStartTime = 0;
                manageError(error);
                handleMessage.append("UPGRADE_ERROR");
                break;

            case BluetoothService.UpgradeMessage.UPGRADE_UPLOAD_PROGRESS:
                double percentage = (double) content;
                mUpgradeDialog.displayTransferProgress(percentage);
                handleMessage.append("UPGRADE_UPLOAD_PROGRESS");
                break;
        }

        if (DEBUG && message != BluetoothService.UpgradeMessage.UPGRADE_UPLOAD_PROGRESS) {
            // The upgrade upload messages are not displayed to avoid too many logs.
            Log.d(TAG, handleMessage.toString());
        }
    }

    /**
     * <p>This method is called when this activity receives a
     * {@link GAIAGATTBLEService.GattMessage GattMessage} from the Service.</p>
     * <p>This method will act depending on the type of GATT message which had been broadcast to this activity.</p>
     *
     * @param gattMessage
     *          The GATT Message type.
     * @param data
     *          Any complementary information provided with the GATT Message.
     */
    @SuppressLint("SwitchIntDef")
    private void onReceiveGattMessage(@GAIAGATTBLEService.GattMessage int gattMessage, Object data) {
        switch (gattMessage) {

            case GAIAGATTBLEService.GattMessage.RWCP_SUPPORTED:
                boolean rwcpSupported = (boolean) data;
                mOptionsFragment.onRWCPSupported(rwcpSupported);
                if (!rwcpSupported) {
                    Toast.makeText(this, R.string.toast_rwcp_not_supported, Toast.LENGTH_SHORT).show();
                }
                break;

            case GAIAGATTBLEService.GattMessage.RWCP_ENABLED:
                mIsRWCPEnabled = (boolean) data;
                mOptionsFragment.onRWCPEnabled(mIsRWCPEnabled, mFile != null);
                int textRWCPEnabled = mIsRWCPEnabled ? R.string.toast_rwcp_enabled : R.string.toast_rwcp_disabled;
                Toast.makeText(this, textRWCPEnabled, Toast.LENGTH_SHORT).show();
                break;

            case GAIAGATTBLEService.GattMessage.TRANSFER_FAILED:
                // The transport layer has failed to transmit bytes to the device using RWCP
                mUpgradeDialog.displayError(getString(R.string.dialog_upgrade_transfer_failed));
                break;

            case GAIAGATTBLEService.GattMessage.MTU_SUPPORTED:
                boolean mtuSupported = (boolean) data;
                mOptionsFragment.onMTUSupported(mtuSupported, mFile != null);
                if (!mtuSupported) {
                    Toast.makeText(this, R.string.toast_mtu_not_supported, Toast.LENGTH_SHORT).show();
                }
                break;

            case GAIAGATTBLEService.GattMessage.MTU_UPDATED:
                int mtu = (int) data;
                mOptionsFragment.onMTUUpdated(mFile != null);
                Toast.makeText(this, getString(R.string.toast_mtu_updated) + " " + mtu, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    /**
     * <p>This method allows the Upgrade process to ask the user for any confirmation to carry on the upgrade process.</p>
     *
     * @param confirmation
     *        The type of confirmation which has to be requested from the user.
     */
    private void askForConfirmation(@UpgradeManager.ConfirmationType final int confirmation) {
        switch (confirmation) {
            case UpgradeManager.ConfirmationType.COMMIT:
                displayConfirmationDialog(confirmation, R.string.alert_upgrade_commit_title,
                        R.string.alert_upgrade_commit_message);
                break;
            case UpgradeManager.ConfirmationType.IN_PROGRESS:
                // no obligation to ask for confirmation as the commit confirmation should happen next
                mService.sendConfirmation(confirmation, true);
                break;
            case UpgradeManager.ConfirmationType.TRANSFER_COMPLETE:
                displayConfirmationDialog(confirmation, R.string.alert_upgrade_transfer_complete_title,
                        R.string.alert_upgrade_transfer_complete_message);
                break;
            case UpgradeManager.ConfirmationType.BATTERY_LOW_ON_DEVICE:
                displayConfirmationDialog(confirmation, R.string.alert_upgrade_low_battery_title,
                        R.string.alert_upgrade_low_battery_message);
                break;
            case UpgradeManager.ConfirmationType.WARNING_FILE_IS_DIFFERENT:
                displayConfirmationDialog(confirmation, R.string.alert_upgrade_sync_id_different_title,
                        R.string.alert_upgrade_sync_id_different_message);
                break;
        }
    }

    /**
     * <p>When an error occurs during the upgrade, this method allows display of error information to the user
     * depending on the error type contained on the {@link UpgradeError UpgradeError} parameter.</p>
     *
     * @param error
     *              The information related to the error which occurred during the upgrade process.
     */
    private void manageError(UpgradeError error) {
        switch (error.getError()) {
            case UpgradeError.ErrorTypes.AN_UPGRADE_IS_ALREADY_PROCESSING:
                // nothing should happen as there is already an upgrade processing.
                // in case it's not already displayed, we display the Upgrade dialog
                showUpgradeDialog(true);
                break;

            case UpgradeError.ErrorTypes.ERROR_BOARD_NOT_READY:
                // display error message + "please try again later"
                mUpgradeDialog.displayError(getString(R.string.dialog_upgrade_error_board_not_ready));
                break;

            case UpgradeError.ErrorTypes.EXCEPTION:
                // display that an error has occurred?
                mUpgradeDialog.displayError(getString(R.string.dialog_upgrade_error_exception));
                break;

            case UpgradeError.ErrorTypes.NO_FILE:
                displayFileError();
                break;

            case UpgradeError.ErrorTypes.RECEIVED_ERROR_FROM_BOARD:
                mUpgradeDialog.displayError(ReturnCodes.getReturnCodesMessage(error.getReturnCode()),
                        Utils.getIntToHexadecimal(error.getReturnCode()));
                break;

            case UpgradeError.ErrorTypes.WRONG_DATA_PARAMETER:
                mUpgradeDialog.displayError(getString(R.string.dialog_upgrade_error_protocol_exception));
                break;
        }
    }


    // ====== UI METHODS ===========================================================================

    /**
     * This method is used to initialize the default dialogs which will be used during the upgrade process.
     */
    private void initDialog() {
        // build the Upgrade dialog
        mUpgradeDialog = VMUpgradeDialog.newInstance(this);

        // build the dialog to show a progress bar while device is disconnected
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(UpgradeActivity.this);
        dialogBuilder.setTitle(getString(R.string.alert_reconnection_title));

        LayoutInflater inflater = getLayoutInflater();
        @SuppressLint("InflateParams") // the root can be null as "attachToRoot" is false
                View dialogLayout = inflater.inflate(R.layout.dialog_progress_bar, null, false);
        dialogBuilder.setView(dialogLayout);
        dialogBuilder.setCancelable(false);
        mDialogReconnection = dialogBuilder.create();
    }

    /**
     * <p>To display the given fragment on the UI.</p>
     *
     * @param fragment The fragment to display.
     */
    private void displayFragment(Fragment fragment) {
        if (fragment != null && !fragment.isVisible()) {
            FragmentTransaction fragmentTransaction = this.getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.fragment_container, fragment);
            fragmentTransaction.commit();
        }
        else if (fragment == null) {
            Log.w(TAG, "No fragment to display, fragment is null.");
        }
    }

    /**
     * <p>To display the Upgrade Dialog. This method will add the dialog to the fragment manager or will dismiss it.</p>
     *
     * @param show
     *          True to display the dialog to the user, false to dismiss the dialog.
     */
    private void showUpgradeDialog(boolean show) {
        if (show && !mUpgradeDialog.isAdded()) {
            mUpgradeDialog.show(getSupportFragmentManager(), getResources().getString(R.string.dialog_upgrade_title));
        }
        else
        if (!show && mUpgradeDialog.isAdded()) {
            mUpgradeDialog.dismiss();
        }
    }

    /**
     * <p>Displays an alert dialog with an "error file" message.</p>
     */
    private void displayFileError() {
        showUpgradeDialog(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_file_error_message)
                .setTitle(R.string.alert_file_error_title)
                .setPositiveButton(R.string.button_ok, null);
        builder.show();
    }

    /**
     * <p>If an upgrade is processing, this method displays the reconnection dialog or the upgrade dialog depending on
     * the given state.</p>
     *
     * @param state
     *          {@link BluetoothService.State#CONNECTED CONNECTED} state to display the upgrade
     *          dialog and hide the reconnection dialog. Any other state will display the reconnection one and
     *          hide the upgrade one.
     */
    private void showUpgradingDialogs(@BluetoothService.State int state) {
        if (mService != null && mService.isUpgrading()) {
            if (state == BluetoothService.State.CONNECTED) {
                showReconnectionDialog(false);
                showUpgradeDialog(true);
            }
            else {
                showUpgradeDialog(false);
                showReconnectionDialog(true);
            }
        }
    }

    /**
     * <p>This method displays or hides the reconnection dialog.</p>
     *
     * @param display
     *          True to display the reconnection dialog to the user, false to dismiss it.
     */
    private void showReconnectionDialog (boolean display) {
        if (display) {
            if (!mDialogReconnection.isShowing()) mDialogReconnection.show();
        }
        else {
            if (mDialogReconnection.isShowing()) mDialogReconnection.dismiss();
        }
    }

    /**
     * <p>To restore the upgrade dialog if there is an upgrade going on. This is used when this activity had been
     * paused or destroyed.</p>
     */
    private void restoreUpgradeDialog() {
        if (mService != null && mService.isUpgrading()) {
            showUpgradingDialogs(mService.getConnectionState());
            if (mService.getConnectionState() == BluetoothService.State.CONNECTED) {
                mUpgradeDialog.updateStep(mService.getResumePoint());
            }
        }
    }

    /**
     * To display an alert when the upgrade process successfully completed.
     */
    private void displayUpgradeComplete() {
        showUpgradeDialog(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_upgrade_complete_message).setTitle(R.string.alert_upgrade_complete_title)
                .setPositiveButton(R.string.button_ok, null);
        builder.show();
    }

    /**
     * <p>To display a confirmation dialog for the user to pick a choice when the Upgrade process needs to
     * know if it should carry on.</p>
     *
     * @param confirmation
     *        The type of confirmation which has been asked by the Upgrade process.
     * @param title
     *        The tile of the dialog.
     * @param message
     *        The message which should be displayed in the dialog.
     */
    private void displayConfirmationDialog (@UpgradeManager.ConfirmationType final int confirmation, int title, int
            message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setTitle(title)
                .setPositiveButton(R.string.button_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mService.sendConfirmation(confirmation, true);
                    }
                })
                .setNegativeButton(R.string.button_abort, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mService.sendConfirmation(confirmation, false);
                        showUpgradeDialog(false);
                    }
                });
        builder.setCancelable(false);
        builder.show();
    }

}