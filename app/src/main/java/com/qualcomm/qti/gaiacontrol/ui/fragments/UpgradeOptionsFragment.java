/* ************************************************************************************************
 * Copyright 2017 Qualcomm Technologies International, Ltd.                                       *
 ************************************************************************************************ */

package com.qualcomm.qti.gaiacontrol.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.qualcomm.qti.gaiacontrol.Consts;
import com.qualcomm.qti.gaiacontrol.R;
import com.qualcomm.qti.gaiacontrol.services.GAIAGATTBLEService;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * <p>This fragment displays an user interface to manage options of an update using the following options: RWCP and
 * MTU size. It also displays the file a user has selected.</p>
 */
public class UpgradeOptionsFragment extends Fragment {

    /**
     * For debug mode, the tag to display for logs.
     */
    private static final String TAG = "UpgradeOptionsFragment";
    /**
     * The layout which has all the file information.
     */
    private View mLayoutFileInformation;
    /**
     * The button to start the upgrade action.
     */
    private View mButtonActionUpgrade;
    /**
     * The text view which displays the sub-message for picking a file. If there is a file this message explains how
     * to change it, if there is no selected file, this message explains how to pick one.
     */
    private TextView mTextViewActionPickFileMessage;
    /**
     * The text view to display when the selected file has been modified for the last time.
     */
    private TextView mTextViewFileLastModification;
    /**
     * The text view to display the selected file name.
     */
    private TextView mTextViewFileName;
    /**
     * The text view to display the selected file path.
     */
    private TextView mTextViewFilePath;
    /**
     * The text view to display the selected file size.
     */
    private TextView mTextViewFileSize;
    /**
     * The text view which displays the title for the RWCP option.
     */
    private TextView mTextViewRWCPTitle;
    /**
     * The text view which displays the explanation for the RWCP option.
     */
    private TextView mTextViewRWCPMessage;
    /**
     * The text view which displays the title for the MTU option.
     */
    private TextView mTextViewMTUTitle;
    /**
     * The text view which displays the explanation for the MTU option.
     */
    private TextView mTextViewMTUMessage;
    /**
     * The switch the user uses to enable and disable the RWCP mode.
     */
    private Switch mSwitchRWCP;
    /**
     * The switch the user uses to enable or disable the maximum MTU size.
     */
    private Switch mSwitchMTU;
    /**
     * The progress bar shown while the app communicates with the device to enable/disable the RWCP mode.
     */
    private View mRWCPProgressBar;
    /**
     * The progress bar shown while the app communicates with the device to enable/disable the maximum MTU size.
     */
    private View mMTUProgressBar;
    /**
     * To know if the user has checked the RWCP switch.
     * This is set to false when done programmatically.
     */
    private boolean isRWCPCheckedByUser = true;
    /**
     * To know if the user has checked the MTU switch.
     * This is set to false when done programmatically.
     */
    private boolean isMTUCheckedByUser = true;
    /**
     * To know if the user has already change the MTU size to know if the Warning dialog needs to be displayed.
     */
    private boolean hasUserChangedMTU = false;
    /**
     * The listener to trigger events from this fragment.
     */
    private UpgradeOptionsFragmentListener mListener;

    /**
     * The factory method to create a new instance of this fragment using the provided parameters.
     *
     * @return A new instance of fragment UpgradeVMFragment.
     */
    public static UpgradeOptionsFragment newInstance() {
        return new UpgradeOptionsFragment();
    }

    /**
     * Empty constructor - required.
     */
    public UpgradeOptionsFragment() {
    }

    // This event fires first, before creation of fragment or any views
    // The onAttach method is called when the Fragment instance is associated with an Activity.
    // This does not mean the Activity is fully initialized.
    @Override // Fragment
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof UpgradeOptionsFragmentListener) {
            this.mListener = (UpgradeOptionsFragmentListener) context;
            if (mTextViewFileName != null) {
                // views had been initialised, the setFileInformation method can be called
                setFileInformation(mListener.getFile());
                onRWCPEnabled(mListener.isRWCPEnabled(), mListener.getFile() != null);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mListener != null) {
            setFileInformation(mListener.getFile());
            onRWCPEnabled(mListener.isRWCPEnabled(), mListener.getFile() != null);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_upgrade_options, container, false);
        init(view);
        return view;
    }

    /**
     * <p>Called when this activity receives a
     * {@link GAIAGATTBLEService.GattMessage#RWCP_SUPPORTED RWCP_SUPPORTED} message from
     * the attached service.</p>
     * <p>It manages the corresponding view components depending on the given support value.</p>
     *
     * @param supported
     *          True if RWCP is supported, false otherwise.
     */
    public void onRWCPSupported(boolean supported) {
        if (supported) {
            // wait for RWCP_ENABLED message to enable the RWCP switch
            mTextViewRWCPTitle.setTextColor(getResources().getColor(R.color.primary_text, null));
            mTextViewRWCPMessage.setTextColor(getResources().getColor(R.color.secondary_text, null));
        }
        else {
            mSwitchRWCP.setEnabled(false);
            mTextViewRWCPTitle.setTextColor(getResources().getColor(R.color.secondary_text, null));
            mTextViewRWCPMessage.setTextColor(getResources().getColor(R.color.tertiary_text, null));
        }
    }

    /**
     * <p>Called when this activity receives a
     * {@link GAIAGATTBLEService.GattMessage#RWCP_ENABLED RWCP_ENABLED} message from
     * the attached service.</p>
     * <p>It manages the corresponding view components depending on the given support value.</p>
     *
     * @param enabled
     *          True if RWCP had been enabled, false otherwise.
     * @param fileSelected
     *          True if a file is selected, false otherwise.
     */
    public void onRWCPEnabled(boolean enabled, boolean fileSelected) {
        mRWCPProgressBar.setVisibility(View.GONE);
        mSwitchRWCP.setVisibility(View.VISIBLE);
        mButtonActionUpgrade.setEnabled(fileSelected);
        setRWCPSwitchChecked(enabled);
    }

    /**
     * <p>Called when this activity receives a
     * {@link GAIAGATTBLEService.GattMessage#MTU_SUPPORTED MTU_SUPPORTED} message from
     * the attached service.</p>
     * <p>It manages the corresponding view components depending on the given support value.</p>
     *
     * @param supported
     *          True if the maximum MTU size is supported, false otherwise.
     * @param fileSelected
     *          True if a file is selected, false otherwise.
     */
    public void onMTUSupported(boolean supported, boolean fileSelected) {
        if (supported) {
            mSwitchMTU.setEnabled(true);
            mTextViewMTUTitle.setTextColor(getResources().getColor(R.color.primary_text, null));
            mTextViewMTUMessage.setTextColor(getResources().getColor(R.color.secondary_text, null));
        }
        else {
            mMTUProgressBar.setVisibility(View.GONE);
            mButtonActionUpgrade.setEnabled(fileSelected);
            mSwitchMTU.setVisibility(View.VISIBLE);
            mSwitchMTU.setEnabled(false);
            mTextViewMTUTitle.setTextColor(getResources().getColor(R.color.secondary_text, null));
            mTextViewMTUMessage.setTextColor(getResources().getColor(R.color.tertiary_text, null));
        }
    }

    /**
     * <p>Called when this activity receives a
     * {@link GAIAGATTBLEService.GattMessage#MTU_UPDATED MTU_UPDATED} message from
     * the attached service.</p>
     * <p>It manages the corresponding view components depending on the given support value.</p>
     *
     * @param fileSelected
     *          True if a file is selected, false otherwise.
     */
    public void onMTUUpdated (boolean fileSelected) {
        mMTUProgressBar.setVisibility(View.GONE);
        mSwitchMTU.setVisibility(View.VISIBLE);
        mButtonActionUpgrade.setEnabled(fileSelected);
    }

    /**
     * This method allows initialisation of components.
     *
     * @param view
     *            The inflated view for this fragment.
     */
    private void init(View view) {
        // get UI components
        Button buttonFilePicker = (Button) view.findViewById(R.id.bt_action_pick_file);
        mLayoutFileInformation = view.findViewById(R.id.layout_file_information);
        mButtonActionUpgrade = view.findViewById(R.id.bt_action_upgrade);
        mTextViewActionPickFileMessage = (TextView) view.findViewById(R.id.tv_action_pick_file_message);
        mTextViewFileLastModification = (TextView) view.findViewById(R.id.tv_file_last_modification);
        mTextViewFileName = (TextView) view.findViewById(R.id.tv_file_name);
        mTextViewFilePath = (TextView) view.findViewById(R.id.tv_file_path);
        mTextViewFileSize = (TextView) view.findViewById(R.id.tv_file_size);
        mTextViewRWCPTitle = (TextView) view.findViewById(R.id.tv_action_rwcp_title);
        mTextViewRWCPMessage = (TextView) view.findViewById(R.id.tv_action_rwcp_message);
        mTextViewMTUTitle = (TextView) view.findViewById(R.id.tv_action_mtu_title);
        mTextViewMTUMessage = (TextView) view.findViewById(R.id.tv_action_mtu_message);
        mSwitchRWCP = (Switch) view.findViewById(R.id.sw_action_rwcp);
        mSwitchMTU = (Switch) view.findViewById(R.id.sw_action_mtu);
        Switch switchLogs = (Switch) view.findViewById(R.id.sw_action_logs);
        mRWCPProgressBar = view.findViewById(R.id.progress_bar_rwcp);
        mMTUProgressBar = view.findViewById(R.id.progress_bar_mtu);

        // setting up components
        buttonFilePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mListener.pickFile();
            }
        });
        mButtonActionUpgrade.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mListener.startUpgrade();
            }
        });
        mSwitchRWCP.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onRWCPChecked(isChecked);
            }
        });
        mSwitchMTU.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onMTUChecked(isChecked);
            }
        });
        switchLogs.setChecked(Consts.DEBUG);
        switchLogs.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                onLogsChecked(isChecked);
            }
        });
    }

    /**
     * <p>To display the file information of the given file on the UI.</p>
     * <p>This method shows the file description view and updates it with: the file name, the last modified date,
     * the canonical path and the size in KB.</p>
     * <p>If the file is null, the file description view is hidden.</p>
     * <p>This method also the sub message from the picking option depending of a file is selected or not.</p>
     *
     * @param file
     *          The file object to display information from.
     */
    private void setFileInformation(File file) {
        if (file != null) {
            mButtonActionUpgrade.setEnabled(true);
            mLayoutFileInformation.setVisibility(View.VISIBLE);
            mTextViewActionPickFileMessage.setText(R.string.message_pick_file_change);
            mTextViewFileName.setText(file.getName());
            String date = DateFormat.format(Consts.DATE_FORMAT, new Date(file.lastModified())).toString();
            mTextViewFileLastModification.setText(date);
            try {
                mTextViewFilePath.setText(file.getCanonicalPath());
            } catch (IOException e) {
                Log.e(TAG, "Get Canonical path error: " + e.getMessage());
            }
            long size = file.length() / 1024; // file size in bytes changed to kb
            String sizeText = size + Consts.UNIT_FILE_SIZE;
            mTextViewFileSize.setText(sizeText);
        }
        else {
            mButtonActionUpgrade.setEnabled(false);
            mLayoutFileInformation.setVisibility(View.GONE);
            mTextViewActionPickFileMessage.setText(R.string.message_pick_file_default);
        }
    }

    /**
     * <p>Called when the user checks the RWCP switch in order to enable or disable the RWCP mode.</p>
     * <p>This method calls {@link GAIAGATTBLEService#enableRWCP(boolean) enableRWCP} and hide or show components
     * while the request is executed.</p>
     *
     * @param checked True if the user has enabled the RWCP mode, false otherwise.
     */
    private void onRWCPChecked(boolean checked) {
        if (!isRWCPCheckedByUser) {
            // is programmatically checked no need to send value to device
            return;
        }

        if (mListener.enableRWCP(checked)) {
            mSwitchRWCP.setVisibility(View.INVISIBLE);
            mRWCPProgressBar.setVisibility(View.VISIBLE);
            mButtonActionUpgrade.setEnabled(false);
        }
        else {
            // RWCP is not supported
            mSwitchRWCP.setEnabled(false);
            setRWCPSwitchChecked(!checked);
            mRWCPProgressBar.setVisibility(View.GONE);
            mSwitchRWCP.setVisibility(View.VISIBLE);
        }
    }

    /**
     * <p>To set up the value of the RWCP switch to the given value without interfering with the user events.</p>
     *
     * @param checked true to check the switch, false otherwise.
     */
    private void setRWCPSwitchChecked(boolean checked) {
        // lock to not loop on the switch being programmatically checked
        isRWCPCheckedByUser = false;
        mSwitchRWCP.setChecked(checked);
        isRWCPCheckedByUser = true;
    }

    /**
     * <p>Called when the user checks the MTU switch in order to enable or disable the maximum MTU size.</p>
     * <p>This method shows a warning dialog if the user has never been informed yet of the possible long delay when
     * activating the maximum size. Otherwise it calls {@link #enableMTU(boolean) enableMTU} to request the
     * activation or deactivation.</p>
     *
     * @param checked True if the user has enabled the maximum MTU size, false otherwise.
     */
    private void onMTUChecked(boolean checked) {
        if (!isMTUCheckedByUser) {
            // is programmatically checked no need to send value to device
            return;
        }

        if (!hasUserChangedMTU) {
            mListener.showMTUDialog(checked);
        }
        else {
            enableMTU(checked);
        }
    }

    /**
     * <p>To set up the value of the MTU switch to the given value without interfering with the user events.</p>
     *
     * @param checked true to check the switch, false otherwise.
     */
    private void setMTUSwitchChecked(boolean checked) {
        // lock to not loop on the switch being programmatically checked
        isMTUCheckedByUser = false;
        mSwitchMTU.setChecked(checked);
        isMTUCheckedByUser = true;
    }



    /**
     * <p>Called when the user validates the activation or deactivation of the maximum MTU size.</p>
     * <p>This method calls {@link GAIAGATTBLEService#enableMaximumMTU(boolean) enableMaximumMTU} and hide or show
     * components while the request is executed.</p>
     *
     * @param enabled True if the user has enabled the maximum MTU size, false otherwise.
     */
    private void enableMTU(boolean enabled) {
        hasUserChangedMTU = true;
        if (mListener.enableMaximumMTU(enabled)) {
            mSwitchMTU.setVisibility(View.INVISIBLE);
            mMTUProgressBar.setVisibility(View.VISIBLE);
            mButtonActionUpgrade.setEnabled(false);
        }
        else {
            setMTUSwitchChecked(!enabled);
            mSwitchMTU.setVisibility(View.VISIBLE);
            mMTUProgressBar.setVisibility(View.GONE);
        }

    }

    /**
     * <p>Called when the user does not validate the activation or deactivation of the maximum MTu size.</p>
     * <p>This method manages the properties if related components.</p>
     */
    public void onMTUDialogCancelled() {
        setMTUSwitchChecked(false);
        mSwitchMTU.setVisibility(View.VISIBLE);
        mMTUProgressBar.setVisibility(View.GONE);
        mButtonActionUpgrade.setEnabled(mListener.getFile() != null);
    }

    /**
     * <p>Called when the user checks the Logs switch in order to enable or disable the Debug logs.</p>
     * <p>This method sends the request to this class listener.</p>
     *
     * @param checked True if the user has enabled the logs, false otherwise.
     */
    private void onLogsChecked(boolean checked) {
        mListener.enableDebugLogs(checked);
    }

    /**
     * The listener triggered by events from this fragment.
     */
    public interface UpgradeOptionsFragmentListener {
        /**
         * <p>Called when the user checks the RWCP switch to enable or disable the RWCP mode. This method must enable
         * or disable the RWCP mode in the application and from the device.</p>
         *
         * @param enabled
         *          True when the user chooses to enable the RWCP mode, false otherwise.
         *
         * @return True if it was possible to initiate the request, false if it seems unsupported by the device.
         */
        boolean enableRWCP(boolean enabled);

        /**
         * <p>Called when the user checks the MTU switch to use the maximum MTU size or to disable this. This method
         * must enable or disable the maximum MTU size in the application and from the device.</p>
         *
         * @param enabled
         *          True to use the maximum MTU size, false otherwise.
         *
         * @return True if it was possible to initiate the request, false if it seems unsupported by the device.
         */
        boolean enableMaximumMTU(boolean enabled);

        /**
         * <p>To get the current selected file.</p>
         *
         * @return The current selected file as known by the application. Null if there is no file selected.
         */
        File getFile();

        /**
         * <p>To display a dialog to inform the user that activating the maximum MTU size might take a long time if it
         * is not available on the connected device.</p>
         * <p>This dialog lets the user confirm or cancel the activation.</p>
         *
         * @param enabledMTU True if the user action was to activate the maximum MTU size, false otherwise.
         */
        void showMTUDialog(final boolean enabledMTU);

        /**
         * <p>This method is called when the user taps on a button in order to pick a file.</p>
         */
        void pickFile();

        /**
         * This method allows to start the upgrade process as asked by the user.
         */
        void startUpgrade();

        /**
         * <p>To get the current status of the RWCP mode: enabled or disabled.</p>
         */
        boolean isRWCPEnabled();

        /**
         * <p>To enable the display of the debug logs in the Android log system.</p>
         *
         * @param enable
         *          True to enable the display of the logs, false otherwise.
         */
        void enableDebugLogs(boolean enable);
    }
}
