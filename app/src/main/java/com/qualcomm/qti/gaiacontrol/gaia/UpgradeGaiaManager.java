/* ************************************************************************************************
 * Copyright 2017 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/


package com.qualcomm.qti.gaiacontrol.gaia;

import android.util.Log;

import com.qualcomm.qti.gaiacontrol.Consts;
import com.qualcomm.qti.gaiacontrol.rwcp.RWCP;
import com.qualcomm.qti.libraries.gaia.GAIA;
import com.qualcomm.qti.libraries.gaia.GaiaException;
import com.qualcomm.qti.libraries.gaia.packets.GaiaPacket;
import com.qualcomm.qti.libraries.gaia.packets.GaiaPacketBLE;
import com.qualcomm.qti.libraries.gaia.packets.GaiaPacketBREDR;
import com.qualcomm.qti.libraries.vmupgrade.UpgradeError;
import com.qualcomm.qti.libraries.vmupgrade.UpgradeManager;
import com.qualcomm.qti.libraries.vmupgrade.codes.ResumePoints;

import java.io.File;

/**
 * <p>This class follows the GAIA protocol. It manages all messages which are sent and received over the protocol in
 * order to process an upgrade using the VM Upgrade protocol.</p>
 * <p>For all GAIA commands used in this class, the Vendor ID is always {@link GAIA#VENDOR_QUALCOMM}.</p>
 */
public class UpgradeGaiaManager extends AGaiaManager implements UpgradeManager.UpgradeManagerListener {

    // ====== PRIVATE FIELDS =======================================================================

    /**
     * <p>The tag to display for logs.</p>
     */
    private final String TAG = "UpgradeGaiaManager";
    /**
     * <p>The listener which implements the GaiaManagerListener interface to allow this manager to communicate with a
     * device.</p>
     */
    private final GaiaManagerListener mListener;
    /**
     * <p>The manager to process the upgrade.</p>
     */
    private final UpgradeManager mUpgradeManager;
	/**
     * To know if RWCP is enabled.
     */
    private boolean mIsRWCPEnabled = false;
    /**
     * To get the maximum size a payload can be.
     */
    private int mPayloadSizeMax;


    // ====== CONSTRUCTOR ==========================================================================

    /**
     * <p>Main constructor of this class which initialises a listener to send messages to a device or dispatch
     * any GAIA received messages.</p>
     *
     * @param myListener
     *         An object which implements the {@link GaiaManagerListener MyGaiaManagerListener} interface.
     * @param transport
     *          The type of transport this manager should use for the GAIA packet format:
     *          {@link com.qualcomm.qti.libraries.gaia.GAIA.Transport#BLE BLE} or
     *          {@link com.qualcomm.qti.libraries.gaia.GAIA.Transport#BR_EDR BR/EDR}.
     */
    public UpgradeGaiaManager(GaiaManagerListener myListener, @GAIA.Transport int transport) {
        super(transport);
        this.mListener = myListener;
        mPayloadSizeMax = transport == GAIA.Transport.BR_EDR ? GaiaPacketBREDR.MAX_PAYLOAD : GaiaPacketBLE.MAX_PAYLOAD;
        mUpgradeManager = new UpgradeManager(this);
        mUpgradeManager.showDebugLogs(Consts.DEBUG);
    }


    // ====== PUBLIC METHODS =======================================================================

    /**
     * <p>To enable the display of the debug logs in the Android log system.</p>
     *
     * @param enable
     *          True to enable the display of the logs, false otherwise.
     */
    public void enableDebugLogs(boolean enable) {
        showDebugLogs(enable);
        mUpgradeManager.showDebugLogs(enable);
    }

    /**
     * <p>To start the VM Upgrade process. This method sends the {@link GAIA#COMMAND_VM_UPGRADE_CONNECT
     * COMMAND_VM_UPGRADE_CONNECT} command and registers for the {@link GAIA.NotificationEvents#VMU_PACKET VMU_PACKET}
     * event. Once this step has been done and acknowledged, this manager asks the VMU Manager to start its process
     * .</p>
     *
     */
    public void startUpgrade(File file) {
        if (!mUpgradeManager.isUpgrading()) {
            registerNotification(GAIA.NotificationEvents.VMU_PACKET);
            mUpgradeManager.setFile(file);
            sendUpgradeConnect();
        }
    }

    /**
     * <p>To abort an ongoing upgrade.</p>
     */
    public void abortUpgrade() {
        mUpgradeManager.abortUpgrade();
    }

    /**
     * <p>To get the current {@link ResumePoints ResumePoint} of the upgrade process.</p>
     *
     * @return The corresponding ResumePoint. If there is no ongoing upgrade the given ResumePoint is not accurate.
     */
    public @ResumePoints.Enum int getResumePoint() {
        return mUpgradeManager.getResumePoint();
    }

    /**
     * <p>To give an answer to the {@link UpgradeManager UpgradeManager} about a confirmation it is waiting for before
     * continuing the upgrade process.</p>
     *
     * @param type
     *              The type of confirmation the UpgradeManager requested.
     * @param confirmation
     *              To know if the UpgradeManager should continue the process or abort it.
     */
    public void sendConfirmation(@UpgradeManager.ConfirmationType int type, boolean confirmation) {
        if (mUpgradeManager.isUpgrading()) {
            mUpgradeManager.sendConfirmation(type, confirmation);
        }
    }

    /**
     * <p>To turn on or off the RWCP mode on the remote device. This is done using the GAIA command
     * {@link GAIA#COMMAND_SET_DATA_ENDPOINT_MODE COMMAND_SET_DATA_ENDPOINT_MODE}.</p>
     * <p>If the use of the command is not successful, the listener get an information through the method
     * {@link GaiaManagerListener#onRWCPNotSupported() onRWCPNotSupported}.</p>
     *
     * @param enabled
     *          True to enable the RWCP mode, false to disable it.
     */
    public void setRWCPMode(boolean enabled) {
        byte mode = enabled ? TransferModes.MODE_RWCP : TransferModes.MODE_NONE;
        mIsRWCPEnabled = enabled;
        byte[] RWCPMode = {mode};
        sendSetDataEndPointMode(RWCPMode);
    }

    /**
     * <p>To get the current status of RWCP: is it enabled or disabled. This is done using the GAIA command
     * {@link GAIA#COMMAND_GET_DATA_ENDPOINT_MODE COMMAND_GET_DATA_ENDPOINT_MODE}.</p>
     * <p>If the use of the command is successful, the listener gets the result of this request through the method
     * {@link GaiaManagerListener#onRWCPEnabled(boolean) onRWCPEnabled}.</p>
     * <p>If the use of the command is not successful, the listener get an information through the method
     * {@link GaiaManagerListener#onRWCPNotSupported() onRWCPNotSupported}.</p>
     */
    public void getRWCPStatus() {
        sendGetDataEndPointMode();
    }

    /**
     * <p>This method gives the current known activation of RWCP.</p>
     *
     * @return true if RWCP is known as enabled.
     */
    public boolean isRWCPEnabled() {
        return mIsRWCPEnabled;
    }

    /**
     * <p>Called when the RWCP transfer mode had been set up using the GAIA commands but the transport layer cannot get
     * it successfully activated.</p>
     */
    public void onRWCPNotSupported() {
        mIsRWCPEnabled = false;
    }

    /**
     * <p>This method must be called when the transmission of the file transfer packets are transported through another
     * protocol instead of the default GAIA transport. Here the default GAIA transport is BLE with the GAIA Service.</p>
     */
    public void onTransferFinished() {
        mUpgradeManager.onSuccessfulTransmission();
    }

    @Override // UpgradeManager.UpgradeManagerListener
    public void sendUpgradePacket(byte[] bytes, boolean isTransferringData) {
        sendUpgradeControl(bytes, isTransferringData);
    }

    @Override // UpgradeManager.UpgradeManagerListener
    public void onUpgradeProcessError(UpgradeError error) {
        mListener.onUpgradeError(error);
        switch (error.getError()) {
            case UpgradeError.ErrorTypes.AN_UPGRADE_IS_ALREADY_PROCESSING:
            case UpgradeError.ErrorTypes.NO_FILE:
                // no ongoing upgrade to abort
                break;
            case UpgradeError.ErrorTypes.ERROR_BOARD_NOT_READY:
            case UpgradeError.ErrorTypes.EXCEPTION:
            case UpgradeError.ErrorTypes.RECEIVED_ERROR_FROM_BOARD:
            case UpgradeError.ErrorTypes.WRONG_DATA_PARAMETER:
                mUpgradeManager.abortUpgrade();
                break;
        }
    }

    @Override // UpgradeManager.UpgradeManagerListener
    public void onResumePointChanged(@ResumePoints.Enum int point) {
        mListener.onResumePointChanged(point);
    }

    @Override // UpgradeManager.UpgradeManagerListener
    public void onUpgradeFinished() {
        mListener.onUpgradeFinish();
        disconnectUpgrade();
    }

    @Override // UpgradeManager.UpgradeManagerListener
    public void onFileUploadProgress(double percentage) {
        mListener.onUploadProgress(percentage);
    }

    @Override // UpgradeManager.UpgradeManagerListener
    public void askConfirmationFor(@UpgradeManager.ConfirmationType int type) {
        mListener.askConfirmation(type);
    }

    @Override // UpgradeManager.UpgradeManagerListener
    public void disconnectUpgrade() {
        cancelNotification(GAIA.NotificationEvents.VMU_PACKET);
        sendUpgradeDisconnect();
    }

    @Override // extends GaiaManager
    public void reset() {
        super.reset();
        if (!isUpgrading()) {
            mIsRWCPEnabled = false;
        }
    }

    /**
     * <p>To know if there is an upgrade going on.</p>
     *
     * @return true if there is an upgrade working, false otherwise.
     */
    public boolean isUpgrading() {
        return mUpgradeManager.isUpgrading();
    }

    /**
     * <p>Once the Bluetooth connection is suitable to send GAIA messages, this method is called to inform this
     * manager it can start to communicate with a GAIA device.</p>
     * <p>This method will resume any VMU process already started.</p>
     */
    public void onGaiaReady() {
        if (mUpgradeManager.isUpgrading()) {
            if (mIsRWCPEnabled) {
                setRWCPMode(true);
            }
            registerNotification(GAIA.NotificationEvents.VMU_PACKET);
            sendUpgradeConnect();
        }
    }

    /**
     * <p>To set up the maximum size of a GAIA packet.</p>
     *
     * @param packetSize The maximum size.
     */
    public void setPacketMaximumSize(int packetSize) {
        this.mPayloadSizeMax = packetSize - GaiaPacketBLE.PACKET_INFORMATION_LENGTH;
    }


    // ====== PRIVATE METHODS - SENDING =============================================================

    /**
     * <p>To send a {@link GAIA#COMMAND_VM_UPGRADE_CONNECT COMMAND_VM_UPGRADE_CONNECT} packet.</p>
     */
    private void sendUpgradeConnect() {
        GaiaPacket packet = createPacket(GAIA.COMMAND_VM_UPGRADE_CONNECT);
        createRequest(packet);
    }

    /**
     * <p>To send a {@link GAIA#COMMAND_VM_UPGRADE_DISCONNECT COMMAND_VM_UPGRADE_DISCONNECT} packet.</p>
     */
    private void sendUpgradeDisconnect() {
        GaiaPacket packet = createPacket(GAIA.COMMAND_VM_UPGRADE_DISCONNECT);
        createRequest(packet);
    }

    /**
     * <p>To send a {@link GAIA#COMMAND_VM_UPGRADE_CONTROL COMMAND_VM_UPGRADE_CONTROL} GAIA packet. That packet contains
     * the bytes of a VM Upgrade packet.</p>
     *
     * @param payload
     *              The bytes which corresponds to a
     *              {@link com.qualcomm.qti.libraries.vmupgrade.packet.VMUPacket VMUPacket}.
     */
    private void sendUpgradeControl(byte[] payload, boolean isTransferringData) {
        if (isTransferringData && mIsRWCPEnabled) {
            GaiaPacket packet = createPacket(GAIA.COMMAND_VM_UPGRADE_CONTROL, payload);
            try {
                byte[] bytes = packet.getBytes();
                if (!mListener.sendGAIAUpgradePacket(bytes, true)) {
                    Log.w(TAG, "Fail to send GAIA packet for GAIA command: " + packet.getCommandId());
                    onSendingFailed(packet);
                }
            }
            catch (GaiaException e) {
                Log.w(TAG, "Exception when attempting to create GAIA packet: " + e.toString());
            }
        }
        else {
            GaiaPacket packet = createPacket(GAIA.COMMAND_VM_UPGRADE_CONTROL, payload);
            createRequest(packet);
        }
    }

    /**
     * <p>To send a {@link GAIA#COMMAND_SET_DATA_ENDPOINT_MODE COMMAND_SET_DATA_ENDPOINT_MODE} packet with the given
     * payload.</p>
     * <p>This command is used to set up the transfer mode to use with the GAIA DATA ENDPOINT GATT characteristic
     * when connected through a GATT connection.</p>
     *
     * @param payload
     *          The payload to send with the command SET DATA ENDPOINT MODE.
     */
    private void sendSetDataEndPointMode(byte[] payload) {
        GaiaPacket packet = new GaiaPacketBLE(GAIA.VENDOR_QUALCOMM, GAIA.COMMAND_SET_DATA_ENDPOINT_MODE, payload);
        createRequest(packet);
    }

    /**
     * <p>To send a {@link GAIA#COMMAND_GET_DATA_ENDPOINT_MODE COMMAND_GET_DATA_ENDPOINT_MODE} packet.</p>
     * <p>This command is used to get the transfer mode used with the GAIA DATA ENDPOINT GATT characteristic
     * when connected through a GATT connection.</p>
     */
    private void sendGetDataEndPointMode() {
        GaiaPacket packet = new GaiaPacketBLE(GAIA.VENDOR_QUALCOMM, GAIA.COMMAND_GET_DATA_ENDPOINT_MODE);
        createRequest(packet);
    }

    /**
     * <p>To register for a {@link GAIA.NotificationEvents GAIA event notification}.</p>
     *
     * @param event
     *              The event to register with.
     */
    @SuppressWarnings("SameParameterValue") // the parameter is always VMU_PACKET in this application
    private void registerNotification (@GAIA.NotificationEvents int event) {
        try {
            GaiaPacket packet = GaiaPacketBLE.buildGaiaNotificationPacket(GAIA.VENDOR_QUALCOMM, GAIA
                    .COMMAND_REGISTER_NOTIFICATION, event, null, getTransportType());
            createRequest(packet);
        } catch (GaiaException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * <p>To cancel a {@link GAIA.NotificationEvents GAIA event notification}.</p>
     *
     * @param event
     *              The notification event to cancel.
     */
    @SuppressWarnings("SameParameterValue") // the parameter is always VMU_PACKET in this application
    private void cancelNotification (@GAIA.NotificationEvents int event) {
        try {
            GaiaPacket packet = GaiaPacketBLE.buildGaiaNotificationPacket(GAIA.VENDOR_QUALCOMM, GAIA
                    .COMMAND_CANCEL_NOTIFICATION, event, null, getTransportType());
            createRequest(packet);
        } catch (GaiaException e) {
            Log.e(TAG, e.getMessage());
        }
    }


    // ====== PRIVATE METHODS - RECEIVING =============================================================

    /**
     * <p>To manage a received {@link GaiaPacket} which has {@link GAIA#COMMAND_EVENT_NOTIFICATION} for command.</p>
     * <p>This manager is only interested by the
     * {@link GAIA.NotificationEvents#VMU_PACKET VMU_PACKET} event to manage a VM Upgrade.</p>
     *
     * @param packet
     *              The receive notification event packet.
     *
     * @return
     *          true if an acknowledgement has been sent.
     */
    private boolean receiveEventNotification(GaiaPacket packet) {
        byte[] payload = packet.getPayload();

        if (payload.length > 0) {
            @GAIA.NotificationEvents int event = packet.getEvent();
            if (event == GAIA.NotificationEvents.VMU_PACKET && mUpgradeManager != null) {
                createAcknowledgmentRequest(packet, GAIA.Status.SUCCESS, null);
                byte[] data = new byte[payload.length - 1];
                System.arraycopy(payload, 1, data, 0, payload.length - 1);
                mUpgradeManager.receiveVMUPacket(data);
                return true;
            }
            else {
                // not supported
                return false;
            }
        }
        else {
            createAcknowledgmentRequest(packet, GAIA.Status.INVALID_PARAMETER, null);
            return true;
        }
    }


    // ====== PROTECTED METHODS ====================================================================

    @Override // GaiaManager
    protected void receiveSuccessfulAcknowledgement(GaiaPacket packet) {
        switch (packet.getCommand()) {
            case GAIA.COMMAND_VM_UPGRADE_CONNECT:
                if (mUpgradeManager.isUpgrading()) {
                    mUpgradeManager.resumeUpgrade();
                }
                else {
                    // The size of the Upgrade packets depends on the support of RWCP which uses some bytes
                    // For the data transfer it is better to have an even number of bytes sent.
                    int size = mPayloadSizeMax;
                    if (mIsRWCPEnabled) {
                        size = mPayloadSizeMax - RWCP.Segment.REQUIRED_INFORMATION_LENGTH;
                        size = (size%2 == 0) ? size : size-1;
                    }
                    mUpgradeManager.startUpgrade(size, isRWCPEnabled());
                }
                break;

            case GAIA.COMMAND_REGISTER_NOTIFICATION:
            case GAIA.COMMAND_CANCEL_NOTIFICATION:
            case GAIA.COMMAND_EVENT_NOTIFICATION:
                /* we assume that if the VM_UPGRADE commands are supported the NOTIFICATION ones also are as there
                are necessary for the Device to communicate with the Host.*/
                break;

            case GAIA.COMMAND_VM_UPGRADE_DISCONNECT:
                mUpgradeManager.onUpgradeDisconnected();
                mListener.onVMUpgradeDisconnected();
                break;

            case GAIA.COMMAND_VM_UPGRADE_CONTROL:
                mUpgradeManager.onSuccessfulTransmission();
                break;

            case GAIA.COMMAND_SET_DATA_ENDPOINT_MODE:
                mListener.onRWCPEnabled(mIsRWCPEnabled);
                break;

            case GAIA.COMMAND_GET_DATA_ENDPOINT_MODE:
                final int MODE_OFFSET = 1;
                byte mode = packet.getPayload()[MODE_OFFSET];
                mIsRWCPEnabled = mode == TransferModes.MODE_RWCP;
                mListener.onRWCPEnabled(mIsRWCPEnabled);
                break;
        }
    }

    @Override // GaiaManager
    protected void receiveUnsuccessfulAcknowledgement(GaiaPacket packet) {
        if (packet.getCommand() == GAIA.COMMAND_VM_UPGRADE_CONNECT
                || packet.getCommand() == GAIA.COMMAND_VM_UPGRADE_CONTROL) {
            sendUpgradeDisconnect();
        }
        else if (packet.getCommand() == GAIA.COMMAND_VM_UPGRADE_DISCONNECT) {
            mListener.onVMUpgradeDisconnected();
        }
        else if (packet.getCommand() == GAIA.COMMAND_SET_DATA_ENDPOINT_MODE
                || packet.getCommand() == GAIA.COMMAND_GET_DATA_ENDPOINT_MODE) {
            mIsRWCPEnabled = false;
            mListener.onRWCPNotSupported();
        }
    }

    @Override // GaiaManager
    protected void hasNotReceivedAcknowledgementPacket(GaiaPacket packet) {
        if (packet.getCommand() == GAIA.COMMAND_DISCONNECT) {
            mListener.onVMUpgradeDisconnected();
        }
        else if (packet.getCommand() == GAIA.COMMAND_SET_DATA_ENDPOINT_MODE
                || packet.getCommand() == GAIA.COMMAND_GET_DATA_ENDPOINT_MODE) {
            mListener.onRWCPNotSupported();
        }
    }

    @Override // extends GaiaManager
    protected void onSendingFailed(GaiaPacket packet) {
    }

    @Override // GaiaManager
    protected boolean manageReceivedPacket(GaiaPacket packet) {
        switch (packet.getCommand()) {
            case GAIA.COMMAND_EVENT_NOTIFICATION:
                return receiveEventNotification(packet);
        }

        return false;
    }

    @Override // GaiaManager
    protected boolean sendGAIAPacket(byte[] packet) {
        return mListener.sendGAIAUpgradePacket(packet, false);
    }


    // ====== INTERFACES ===========================================================================

    /**
     * <p>This interface allows this manager to dispatch messages or events to a listener.</p>
     */
    public interface GaiaManagerListener {

        /**
         * <p>This method is called to inform the listener that the VM Upgrade process has been disconnected.</p>
         * <p>This method is called if a {@link GAIA#COMMAND_DISCONNECT COMMAND_DISCONNECT} packet has been sent to
         * the device. Before being called, the manager waits for any ACK to the command or a time out.</p>
         */
        @SuppressWarnings("EmptyMethod") // this method is empty for all its implementation
        void onVMUpgradeDisconnected();

        /**
         * <p>This method is called when there is progress to a new step of the upgrade process.</p>
         *
         * @see ResumePoints
         *
         * @param point
         *              the new step reached by the process.
         */
        void onResumePointChanged(@ResumePoints.Enum int point);

        /**
         * <p>This method informs the listener that an error occurs during the upgrade process.</p>
         * <p>For the following error types, the upgrade is automatically aborted:
         * <ul>
         *     <li>{@link UpgradeError.ErrorTypes#EXCEPTION EXCEPTION}</li>
         *     <li>{@link UpgradeError.ErrorTypes#RECEIVED_ERROR_FROM_BOARD RECEIVED_ERROR_FROM_BOARD}</li>
         *     <li>{@link UpgradeError.ErrorTypes#WRONG_DATA_PARAMETER WRONG_DATA_PARAMETER}</li>
         * </ul></p>
         *
         * @param error
         *          All the information relative to the occurred error.
         */
        void onUpgradeError(UpgradeError error);

        /**
         * <p>This method is called when progress has been made uploading the file to the device.</p>
         * <p>This method is used when the actual step is
         * {@link com.qualcomm.qti.libraries.vmupgrade.codes.ResumePoints.Enum#DATA_TRANSFER DATA_TRANSFER}.</p>
         *
         * @param percentage
         *          The percentage of the bytes of the file that have been sent to the board.
         */
        void onUploadProgress(double percentage);

        /**
         * <p>To transmit the bytes of a GAIA packet to a connected device.</p>
         *
         * @param packet
         *          The byte array to send to a device.
         * @param isTransferringData
         *          To know if the packet contains data to transfer.
         *
         * @return
         *          true if the sending could be done.
         */
        boolean sendGAIAUpgradePacket(byte[] packet, boolean isTransferringData);

        /**
         * <p>To inform any listener that the upgrade has ended successfully. This method is called after the last
         * Upgrade packet has been sent by the Device to inform that it has been successfully upgraded.</p>
         */
        void onUpgradeFinish();

        /**
         * <p>This method is called when the manager needs the listener to confirm any action before continuing the
         * process.</p>
         * <p>To inform the manager about its decision, the listener has to call the
         * {@link UpgradeGaiaManager#sendConfirmation(int, boolean) sendConfirmation} method.</p>
         */
        void askConfirmation(@UpgradeManager.ConfirmationType int type);

        /**
         * <p>This method is called when this manager knows if the RWCP mode is enabled - and supported - on the
         * connected device.</p>
         * <p>This method is called when one of the following happens:
         * <ul>
         *     <li>The commands {@link GAIA#COMMAND_GET_DATA_ENDPOINT_MODE COMMAND_GET_DATA_ENDPOINT_MODE} and
         *      {@link GAIA#COMMAND_SET_DATA_ENDPOINT_MODE COMMAND_SET_DATA_ENDPOINT_MODE} are not supported, RWCP
         *      is assumed as not being supported by the device.</li>
         *      <li>When this manager gets a successful acknowledgement to the command
         *      {@link GAIA#COMMAND_GET_DATA_ENDPOINT_MODE COMMAND_GET_DATA_ENDPOINT_MODE} which gives the
         *      current enabled transfer mode. If it is {@link TransferModes#MODE_RWCP MODE_RWCP}, this method
         *      returns true, false otherwise.</li>
         * </ul></p>
         *
         * @param enabled
         *          True if RWCP transfer mode is assumed supported and enabled by the device. False otherwise.
         */
        void onRWCPEnabled(boolean enabled);

        /**
         * <p>Called when the RWCP transfer mode couldn't be successfully set up through the command
         * {@link GAIA#COMMAND_SET_DATA_ENDPOINT_MODE COMMAND_SET_DATA_ENDPOINT_MODE}.</p>
         */
        void onRWCPNotSupported();

    }


    // ====== INNER CLASSES ===========================================================================

    /**
     * <p>This class of constants represents all data transfer modes values which can be sent and received through the
     * commands : {@link GAIA#COMMAND_GET_DATA_ENDPOINT_MODE COMMAND_GET_DATA_ENDPOINT_MODE} and
     * {@link GAIA#COMMAND_SET_DATA_ENDPOINT_MODE COMMAND_SET_DATA_ENDPOINT_MODE}.</p>
     */
    private static final class TransferModes {
        /**
         * <p>Sending this value enables the RWCP transfer mode. Receiving it means it is activated.</p>
         */
        private static final byte MODE_RWCP = (byte) 0x01;
        /**
         * <p>Sending this value disables all transfer mode and activates the default one for data transfer.</p>
         */
        private static final byte MODE_NONE = (byte) 0x00;
    }

}
