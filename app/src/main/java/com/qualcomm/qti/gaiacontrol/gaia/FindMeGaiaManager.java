/* ************************************************************************************************
 * Copyright 2017 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/


package com.qualcomm.qti.gaiacontrol.gaia;

import com.qualcomm.qti.gaiacontrol.Consts;
import com.qualcomm.qti.libraries.gaia.GAIA;
import com.qualcomm.qti.libraries.gaia.packets.GaiaPacket;

/**
 * <p>This class follows the GAIA protocol. It manages all messages which are sent and received over the protocol for
 * the Find Me Activity.</p>
 * <p>For all GAIA commands used in this class, the Vendor ID is always {@link GAIA#VENDOR_QUALCOMM}.</p>
 */
public class FindMeGaiaManager extends AGaiaManager {

    // ====== STATIC FIELDS =======================================================================

    /**
     * To know if we are using the application in debug mode.
     */
    @SuppressWarnings("unused")
    private static final boolean DEBUG = Consts.DEBUG;


    // ====== PRIVATE FIELDS =======================================================================

    /**
     * <p>The tag to display for logs.</p>
     */
    @SuppressWarnings("unused")
    private final String TAG = "FindMeGaiaManager";
    /**
     * <p>The listener which implements the ProximityGaiaManagerListener interface to allow this manager to communicate
     * with a device.</p>
     */
    private final FindMeGaiaManagerListener mListener;


    // ====== CONSTRUCTOR ==========================================================================

    /**
     * <p>Main constructor of this class which allows initialisation of a listener to send messages to a device or dispatch
     * any received GAIA messages.</p>
     *
     * @param myListener
     *         An object which implements the {@link FindMeGaiaManagerListener MyGaiaManagerListener} interface.
     * @param transport
     *          The type of transport this manager should use for the GAIA packet format:
     *          {@link GAIA.Transport#BLE BLE} or
     *          {@link GAIA.Transport#BR_EDR BR/EDR}.
     */
    public FindMeGaiaManager(FindMeGaiaManagerListener myListener, @GAIA.Transport int transport) {
        super(transport);
        this.mListener = myListener;
    }


    // ====== PUBLIC METHODS =======================================================================

    /**
     * <p>To send a {@link GAIA#COMMAND_FIND_MY_REMOTE COMMAND_FIND_MY_REMOTE} packet to the device to set up the alert
     * level of its connected remote control.</p>
     *
     * @param level
     *          The level of alert to set up for the remote. It should be one of the values from
     *          {@link Levels Levels}: {@link Levels#NO_ALERT NO_ALERT (0x00)},
     *          {@link Levels#MILD_ALERT MILD_ALERT (0x01)} or {@link Levels#HIGH_ALERT HIGH_ALERT (0x02)}.
     */
    public void setFindMyRemoteAlertLevel(byte level) {
        final int PAYLOAD_LENGTH = 1;
        final int LEVEL_OFFSET = 0;
        byte[] payload = new byte[PAYLOAD_LENGTH];
        payload[LEVEL_OFFSET] = level;
        createRequest(createPacket(GAIA.COMMAND_FIND_MY_REMOTE, payload));
    }


    // ====== PROTECTED METHODS ====================================================================

    @Override // extends GaiaManager
    protected void receiveSuccessfulAcknowledgement(GaiaPacket packet) {
    }

    @Override // extends GaiaManager
    protected void receiveUnsuccessfulAcknowledgement(GaiaPacket packet) {
        if (packet.getCommand() == GAIA.COMMAND_FIND_MY_REMOTE) {
            mListener.onFindMyRemoteNotSupported();
        }
    }

    @Override // extends GaiaManager
    protected void hasNotReceivedAcknowledgementPacket(GaiaPacket packet) {
    }

    @Override // extends GaiaManager
    protected void onSendingFailed(GaiaPacket packet) {
    }

    @Override // extends GaiaManager
    protected boolean manageReceivedPacket(GaiaPacket packet) {
        return false;
    }

    @Override // extends GaiaManager
    protected boolean sendGAIAPacket(byte[] packet) {
        return mListener.sendGAIAPacket(packet);
    }


    // ====== PRIVATE METHODS - RECEIVING =============================================================



    // ====== PRIVATE METHODS =============================================================



    // ====== INTERFACES ===========================================================================

    /**
     * <p>This interface allows this manager to dispatch messages or events to a listener.</p>
     */
    public interface FindMeGaiaManagerListener {

        /**
         * <p>To send over a communication channel the bytes of a GAIA packet using the GAIA protocol.</p>
         *
         * @param packet
         *          The byte array to send to a device.
         * @return
         *          true if the sending could be done.
         */
        boolean sendGAIAPacket(byte[] packet);

        /**
         * <p>This method informs that the Find My remote command is not supported by the device.</p>
         * <p>A requested item of information is considered as not supported by the device if the acknowledgement of the
         * request is not successful.</p>
         */
        void onFindMyRemoteNotSupported();
    }


    // ====== INNER CLASSES ===========================================================================

    /**
     * <p>Represents all possible values for the alert level.</p>
     */
    public static class Levels {
        /**
         * <p>The "no alert" level.</p>
         */
        public static final byte NO_ALERT = 0x00;
        /**
         * <p>The "mild alert" level.</p>
         */
        public static final byte MILD_ALERT = 0x01;
        /**
         * <p>The "high alert" level.</p>
         */
        public static final byte HIGH_ALERT = 0x02;
    }

}
