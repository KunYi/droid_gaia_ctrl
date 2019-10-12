/* ************************************************************************************************
 * Copyright 2017 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/

package com.qualcomm.qti.gaiacontrol.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.qualcomm.qti.gaiacontrol.R;
import com.qualcomm.qti.gaiacontrol.Utils;
import com.qualcomm.qti.libraries.vmupgrade.codes.ResumePoints;

import java.text.DecimalFormat;

/**
 * This fragment allows building of a dialog to display information during the VM upgrade.
 */
public class VMUpgradeDialog extends DialogFragment {

    /**
     * The listener to interact with the fragment which implements this fragment.
     */
    private UpgradeDialogListener mListener;
    /**
     * The progress bar displayed to the user to show the transfer progress.
     */
    private ProgressBar mProgressBar;
    /**
     * The progress bar displayed to the user to for steps other than the DATA_TRANSFER.
     */
    private View mIndeterminateProgressBar;
    /**
     * The textView error to display the error message which corresponds to the error code.
     */
    private TextView mTVErrorCodeMessage;
    /**
     * The text view to display the actual step.
     */
    private TextView mTVStep;
    /**
     * The text view to display a percentage during a process.
     */
    private TextView mTVPercentage;
    /**
     * The view to display information about data transfer.
     */
    private View mLTransfer;
    /**
     * The text view which displays the chronometer during the upgrade.
     */
    private Chronometer mChronometer;
    /**
     * The view to display an error.
     */
    private View mLayoutError;
    /**
     * The view to inform the user the error is coming from the board.
     */
    private TextView mTVError;
    /**
     * The text view to display the error code.
     */
    private TextView mTVErrorCode;
    /**
     * To display a number in a specific decimal format.
     */
    private final DecimalFormat mDecimalFormat = new DecimalFormat();
    /**
     * The positive button of the dialog.
     */
    private Button mPositiveButton;
    /**
     * The negative button of the dialog.
     */
    private Button mNegativeButton;
    /**
     * The dialog itself in order to get its component.
     */
    private AlertDialog mDialog;

    /**
     * The factory method to create a new instance of this fragment using the provided parameters.
     *
     * @return A new instance of fragment VMUpgradeDialog.
     */
    public static VMUpgradeDialog newInstance(UpgradeDialogListener listener) {
        VMUpgradeDialog fragment = new VMUpgradeDialog();
        fragment.setListener(listener);
        return fragment;
    }

    /**
     * Constructor.
     */
    public VMUpgradeDialog() {
        super();
    }

    @Override
    public void onStart() {
        super.onStart();
        mPositiveButton = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        mNegativeButton = mDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        clear();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // the central view: no other choice than "null" for the last parameter, see Android developer documentation.
        @SuppressLint("InflateParams")
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_upgrade_progress, null);
        builder.setView(view);
        // the abort button
        builder.setNegativeButton(R.string.button_abort, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                clear();
                if (mListener != null) {
                    mListener.abortUpgrade();
                }
            }
        });
        builder.setPositiveButton(R.string.button_ok, null);
        // the user can not dismiss the dialog using the back button.
        setCancelable(false);

        mDialog = builder.create();

        init(view);

        return mDialog;
    }

    /**
     * To display a percentage number during the Step DATA_TRANSFER.
     *
     * @param percentage
     *        The percentage of how many bytes of the file have been sent to the Board.
     */
    public void displayTransferProgress(double percentage) {
        if (this.isAdded() || this.isVisible()) {
            mTVPercentage.setText(Utils.getStringForPercentage(percentage));
            mProgressBar.setProgress((int) percentage);
        }
    }

    /**
     * To display an error message.
     */
    public void displayError(String message, String code) {
        if (this.isAdded() || this.isVisible()) {
            mLayoutError.setVisibility(View.VISIBLE);
            mTVError.setText(getResources().getString(R.string.dialog_upgrade_error_from_board));
            mIndeterminateProgressBar.setVisibility(View.GONE);
            mPositiveButton.setVisibility(View.VISIBLE);
            mNegativeButton.setVisibility(View.GONE);

            if (code.length() > 0) {
                mTVErrorCode.setVisibility(View.VISIBLE);
                mTVErrorCode.setText(code);
            } else {
                mTVErrorCode.setVisibility(View.GONE);
            }
            if (message.length() > 0) {
                mTVErrorCodeMessage.setVisibility(View.VISIBLE);
                mTVErrorCodeMessage.setText(message);
            } else {
                mTVErrorCodeMessage.setVisibility(View.GONE);
            }
        }
    }

    /**
     * To display a specific message as an error.
     *
     * @param message
     *              THe specific message to display.
     */
    public void displayError(String message) {
        if (this.isAdded() || this.isVisible()) {
            mLayoutError.setVisibility(View.VISIBLE);
            mTVErrorCode.setVisibility(View.GONE);
            mTVErrorCodeMessage.setVisibility(View.GONE);
            mTVError.setText(message);
            mIndeterminateProgressBar.setVisibility(View.GONE);
            mPositiveButton.setVisibility(View.VISIBLE);
            mNegativeButton.setVisibility(View.GONE);
        }
    }

    /**
     * To update the view depending on the actual step.
     */
    public void updateStep(@ResumePoints.Enum int step) {
        String text = ResumePoints.getLabel(step);
        mTVStep.setText(text);

        if (step == ResumePoints.Enum.DATA_TRANSFER) {
            mChronometer.setBase(mListener.getStartTime());
            mChronometer.start();
            mLTransfer.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.VISIBLE);
            mIndeterminateProgressBar.setVisibility(View.GONE);
        }
        else {
            mChronometer.stop();
            mLTransfer.setVisibility(View.GONE);
            mProgressBar.setVisibility(View.GONE);
            mIndeterminateProgressBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * To clear the content on this view.
     */
    private void clear() {
        mTVStep.setText(R.string.dialog_initialisation);
        mLTransfer.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.GONE);
        mIndeterminateProgressBar.setVisibility(View.VISIBLE);
        hideError();
    }

    /**
     * To hide the error message.
     */
    private void hideError() {
        mLayoutError.setVisibility(View.GONE);
        mPositiveButton.setVisibility(View.GONE);
        mNegativeButton.setVisibility(View.VISIBLE);
    }

    /**
     * This method allows initialisation of components.
     *
     * @param view
     *            The inflated view for this fragment.
     */
    private void init(View view) {
        mTVStep = (TextView) view.findViewById(R.id.tv_step);
        mLTransfer = view.findViewById(R.id.layout_transfer);
        mTVPercentage = (TextView) view.findViewById(R.id.tv_percentage);
        mChronometer = (Chronometer) view.findViewById(R.id.c_chronometer);
        mProgressBar = (ProgressBar) view.findViewById(R.id.pb_upgrade);
        mIndeterminateProgressBar = view.findViewById(R.id.pb_upgrade_indeterminate);
        mLayoutError = view.findViewById(R.id.layout_error);
        mTVError = (TextView) view.findViewById(R.id.tv_upgrade_error_message);
        mTVErrorCode = (TextView) view.findViewById(R.id.tv_upgrade_error_code);
        mTVErrorCodeMessage = (TextView) view.findViewById(R.id.tv_upgrade_error_code_message);

        mDecimalFormat.setMaximumFractionDigits(1);
    }

    /**
     * To define the listener for actions on this dialog. We can't use the onAttach method to define a listener: here
     * the listener is a fragment.
     *
     * @param listener
     *            The listener which will listen this dialog.
     */
    private void setListener(UpgradeDialogListener listener) {
        this.mListener = listener;
    }

    /**
     * This interface allows this Dialog fragment to communicate with its listener.
     */
    public interface UpgradeDialogListener {
        /**
         * To abort the upgrade.
         */
        void abortUpgrade();

        /**
         * To get the start time of the data_transfer.
         *
         * @return The real elapsed time of the data transfer start.
         */
        long getStartTime();
    }
}