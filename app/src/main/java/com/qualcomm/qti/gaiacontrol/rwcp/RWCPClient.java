/* ************************************************************************************************
 * Copyright 2017 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/

package com.qualcomm.qti.gaiacontrol.rwcp;

import android.os.Handler;
import android.util.Log;

import com.qualcomm.qti.gaiacontrol.Utils;

import java.util.LinkedList;

/**
 * <p>This class implements the Reliable Write Command Protocol - RWCP. It manages RWCP segments and the
 * encapsulation of data into a RWCP segment.</p>
 * <p>For any byte array known as a potential RWCP segment, it is passed to this client using
 * {@link #onReceiveRWCPSegment(byte[]) onReceiveRWCPSegment}. This method will then analyze the array in order to
 * get a {@link Segment Segment}. Then depending on the {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode opration code}
 * and the current {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.State state} it handles the received segment.</p>
 * <p>This client provides a {@link #sendData(byte[]) sendData} method in order to send a byte array to a connected
 * RWCP Server using RWCP. If a session is already running, this method queues up the messages.</p>
 * <p>The transfer should be cancelled using {@link #cancelTransfer() cancelTransfer} when the Server is
 * disconnected.</p>
 *
 * @since 3.3.0
 */
public class RWCPClient {

    // ====== FIELDS ====================================================================

    /**
     * <p>The tag to display for logs.</p>
     */
    private final String TAG = "RWCPClient";
    /**
     * <p>The listener to communicate with the application and send segments.</p>
     */
    private final RWCPListener mListener;
    /**
     * The sequence number of the last sequence which had been acknowledged by the Server.
     */
    private int mLastAckSequence = -1;
    /**
     * The next sequence number which will be send.
     */
    private int mNextSequence = 0;
    /**
     * The window represents the maximum number of segments which can be sent simultaneously.
     */
    private int mWindow = RWCP.WINDOW_DEFAULT;
    /**
     * The credit number represents the number of segments which can still be send to fill the current window.
     */
    private int mCredits = mWindow;
    /**
     * When receiving a GAP or when an operation is timed out, this client resends the unacknowledged data and stops
     * any other running operation.
     */
    private boolean mIsResendingSegments = false;
    /**
     * The state of the Client.
     */
    private @RWCP.State int mState = RWCP.State.LISTEN;
    /**
     * The queue of data which are waiting to be sent.
     */
    private final LinkedList<byte[]> mPendingData = new LinkedList<>();
    /**
     * The queue of segments which have been sent but have not been acknowledged yet.
     */
    private final LinkedList<Segment> mUnacknowledgedSegments = new LinkedList<>();
    /**
     * The runnable used to time out the segments which have been sent but not acknowledged.
     */
    private final TimeOutRunnable mTimeOutRunnable = new TimeOutRunnable();
    /**
     * To know if a time out is running.
     */
    private boolean isTimeOutRunning = false;
    /**
     * <p>The main handler to run tasks.</p>>
     */
    private final Handler mHandler = new Handler();
    /**
     * The time used to time out the DATA segments.
     */
    private int mDataTimeOutMs = RWCP.DATA_TIMEOUT_MS_DEFAULT;
    /**
     * <p>To show the debug logs indicating when a method had been reached.</p>
     */
    private boolean mShowDebugLogs = false;
    /**
     * To know the number of segments which had been acknowledged in a row with DATA_ACK.
     */
    private int mAcknowledgedSegments = 0;


    // ====== CONSTRUCTORS ====================================================================

    /**
     * <p>Main constructor of this class which allows initialisation of a listener to send messages to a Server or
     * dispatch any received RWCP messages.</p>
     *
     * @param listener
     *            An object which implements the {@link RWCPListener} interface.
     */
    public RWCPClient(RWCPListener listener) {
        mListener = listener;
    }


    // ====== PUBLIC METHODS ====================================================================

    /**
     * <p>To know if the Client is currently processing a data transfer: this client might be initiating a transfer,
     * processing the transfer or closing it.</p>
     *
     * @return True if a session is currently running, false otherwise.
     */
    public boolean isRunningASession() {
        return mState != RWCP.State.LISTEN;
    }

    /**
     * <p>To allow the display of the debug logs.</p>
     * <p>They give complementary information on any call of a method.
     * They can indicate that a method is reached but also some action the method does.</p>
     *
     * @param show
     *          True to show the debug logs, false otherwise.
     */
    public void showDebugLogs(@SuppressWarnings("SameParameterValue") boolean show) {
        mShowDebugLogs = show;
        Log.i(TAG, "Debug logs are now " + (show ? "activated" : "deactivated") + ".");
    }

    /**
     * <p>To send a message to a Server using RWCP.</p>
     * <p>This method queues up the data and depending on the presence of a current session it starts a session or
     * requests the sending of the data if there is no activity at this moment.</p>
     *
     * @param bytes The array byte to send in a RWCP segment.
     *
     * @return True if the Client could start the session or initiate the sending of the given data. This method
     * returns false if any of the previous fails.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean sendData(byte[] bytes) {
        mPendingData.add(bytes);

        if (mState == RWCP.State.LISTEN) {
            return startSession();
        }
        else if (mState == RWCP.State.ESTABLISHED && !isTimeOutRunning) {
            sendDataSegment();
            return true;
        }

        return true;
    }

    /**
     * <p>To cancel any ongoing session.</p>
     * <p>This method should be called if the system cannot communicate with the Server anymore, if the transfer had
     * been aborted, etc.</p>
     */
    public void cancelTransfer() {
        logState("cancelTransfer");

        if (mState == RWCP.State.LISTEN) {
            Log.i(TAG, "cancelTransfer: no ongoing transfer to cancel.");
            return;
        }

        reset(true);

        if (!sendRSTSegment()) {
            Log.w(TAG, "Sending of RST segment has failed, terminating session.");
            terminateSession();
        }
    }

    /**
     * <p>This method is called by the application when it receives a possible RWCP segment for this client to
     * handle it.</p>
     * <p>This method will act depending on this client current state and the segment operation code.</p>
     *
     * @param bytes The bytes which correspond to a potential RWCP segment.
     *
     * @return True if the bytes had successfully handled as an expected RWCP segment.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean onReceiveRWCPSegment(byte[] bytes) {
        if (bytes == null) {
            Log.w(TAG, "onReceiveRWCPSegment called with a null bytes array.");
            return false;
        }

        if (bytes.length < RWCP.Segment.REQUIRED_INFORMATION_LENGTH) {
            String message = "Analyse of RWCP Segment failed: the byte array does not contain the minimum " +
                    "required information.";
            if (mShowDebugLogs) {
                message += "\n\tbytes=" + Utils.getStringFromBytes(bytes);
            }
            Log.w(TAG, message);
            return false;
        }

        // getting the segment information from the bytes
        Segment segment = new Segment(bytes);
        int code = segment.getOperationCode();
        if (code == RWCP.OpCode.NONE) {
            Log.w(TAG, "onReceivedRWCPSegment failed to get a RWCP segment from given bytes: "
                    + Utils.getStringFromBytes(bytes));
            return false;
        }

        // handling of a segment depends on the operation code.
        switch (code) {
            case RWCP.OpCode.Server.SYN_ACK:
                return receiveSynAck(segment);
            case RWCP.OpCode.Server.DATA_ACK:
                return receiveDataAck(segment);
            case RWCP.OpCode.Server.RST:
            /*case RWCP.OpCode.Server.RST_ACK:*/
                return receiveRST(segment);
            case RWCP.OpCode.Server.GAP:
                return receiveGAP(segment);
            default:
                Log.w(TAG, "Received unknown operation code: " + code);
                return false;
        }
    }


    // ====== PRIVATE METHODS ====================================================================

    /**
     * <p>This method starts a transfer session by sending a RST segment and a SYn segment. Then this client waits
     * for these to be timed out or acknowledged by the Server.</p>
     *
     * @return True if the start of the session had successfully been initiated.
     */
    private boolean startSession() {
        logState("startSession");

        if (mState != RWCP.State.LISTEN) {
            Log.w(TAG, "Start RWCP session failed: already an ongoing session.");
            return false;
        }

        // it is recommended to send a RST and then a SYN to make sure the Server side is in the right state.
        // This client first sends a RST segment, waits to get a RST_ACK segment and sends the SYN segment.
        // The sending of the SYN happens if there is some pending data waiting to be sent.
        if (sendRSTSegment()) {
            return true;
            // wait for receiveRST to be called.
        }
        else {
            Log.w(TAG, "Start RWCP session failed: sending of RST segment failed.");
            terminateSession();
            return false;
        }
    }

    /**
     * <p>To reset this client when a session is ending following a transfer fail.</p>
     */
    private void terminateSession() {
        logState("terminateSession");
        reset(true);
    }

    /**
     * <p>This method is called by {@link #onReceiveRWCPSegment(byte[]) onReceiveRWCPSegment} when it has received a
     * {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#SYN_ACK SYN_ACK} segment.</p>
     * <p>A SYN_ACK segment can be expected on the following cases:
     * <ul>
     *     <li>state {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.State#SYN_SENT SYN_SENT}: default behaviour, the
     *     Client can start the data transfer.</li>
     *     <li>state {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.State#ESTABLISHED ESTABLISHED}: the Server has not
     *     received any data yet and is expecting some. This client resent any unacknowledged data and the
     *     following segments if there is any credit.</li>
     * </ul></p>
     *
     * @param segment
     *          The received segment with the operation code
     *          {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#SYN_ACK SYN_ACK}.
     *
     * @return True if the segment has successfully been handled.
     */
    private boolean receiveSynAck(Segment segment) {
        if (mShowDebugLogs) {
            Log.d(TAG, "Receive SYN_ACK for sequence " + segment.getSequenceNumber());
        }

        switch (mState) {

            case RWCP.State.SYN_SENT:
                // expected behavior: start to send the data
                cancelTimeOut();
                int validated = validateAckSequence(RWCP.OpCode.Client.SYN, segment.getSequenceNumber());
                if (validated >= 0) {
                    mState = RWCP.State.ESTABLISHED;
                    if (mPendingData.size() > 0) {
                        sendDataSegment();
                    }
                }
                else {
                    Log.w(TAG, "Receive SYN_ACK with unexpected sequence number: " + segment.getSequenceNumber());
                    terminateSession();
                    mListener.onTransferFailed();
                    sendRSTSegment();
                }
                return true;

            case RWCP.State.ESTABLISHED:
                // DATA might have been lost, resending them
                cancelTimeOut();
                if (mUnacknowledgedSegments.size() > 0) {
                    resendDataSegment();
                }
                return true;

            case RWCP.State.CLOSING:
            case RWCP.State.LISTEN:
            default:
                Log.w(TAG, "Received unexpected SYN_ACK segment with header " + segment.getHeader()
                        + " while in state " + RWCP.getStateLabel(mState));
                return false;
        }
    }

    /**
     * <p>This method is called by {@link #onReceiveRWCPSegment(byte[]) onReceiveRWCPSegment} when it has received a
     * {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#DATA_ACK DATA_ACK} segment.</p>
     * <p>A DATA_ACK segment can be expected on the following cases:
     * <ul>
     *     <li>state {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.State#ESTABLISHED ESTABLISHED}: the Server acknowledges
     *     the data it has received. This client then validates the acknowledged data and send more if it has free
     *     credits.</li>
     *     <li>state {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.State#CLOSING CLOSING}: when this client has sent a
     *     {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Client#RST RST} segment but it has not been fetched yet by the
     *     Server.</li>
     * </ul></p>
     *
     * @param segment
     *          The received segment with the operation code
     *          {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#DATA_ACK DATA_ACK}.
     *
     * @return True if the segment has successfully been handled.
     */
    private boolean receiveDataAck(Segment segment) {
        if (mShowDebugLogs) {
            Log.d(TAG, "Receive DATA_ACK for sequence " + segment.getSequenceNumber());
        }

        switch (mState) {
            case RWCP.State.ESTABLISHED:
                cancelTimeOut();
                int sequence = segment.getSequenceNumber();
                int validated = validateAckSequence(RWCP.OpCode.Client.DATA, sequence);
                if (validated >= 0) {
                    if (mCredits > 0 && !mPendingData.isEmpty()) {
                        sendDataSegment();
                    }
                    else if (mPendingData.isEmpty() && mUnacknowledgedSegments.isEmpty()) {
                        // no more data to send: close session
                        sendRSTSegment();
                    }
                    else if (mPendingData.isEmpty() && !mUnacknowledgedSegments.isEmpty()
                            || mCredits == 0 && !mPendingData.isEmpty()) {
                        // no more data to send but still some waiting to be acknowledged
                        // or no credits and still some data to send
                        startTimeOut(mDataTimeOutMs);
                    }
                    mListener.onTransferProgress(validated);
                }
                return true;

            case RWCP.State.CLOSING:
                // RST had been sent, wait for the RST time out or RST ACK
                if (mShowDebugLogs) {
                    Log.i(TAG, "Received DATA_ACK(" + segment.getSequenceNumber()
                            + ") segment while in state CLOSING: segment discarded.");
                }
                return true;

            case RWCP.State.SYN_SENT:
            case RWCP.State.LISTEN:
            default:
                Log.w(TAG, "Received unexpected DATA_ACK segment with sequence " + segment.getSequenceNumber()
                        + " while in state " + RWCP.getStateLabel(mState));
                return false;
        }
    }

    /**
     * <p>This method is called by {@link #onReceiveRWCPSegment(byte[]) onReceiveRWCPSegment} when it has received a
     * {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#RST RST} or a
     * {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#RST_ACK RST_ACK} segment.</p>
     * <p>A RST segment can be expected on the following cases:
     * <ul>
     *     <li>state {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.State#ESTABLISHED ESTABLISHED}: the Server requests a
     *     reset of the session, this cancels the transfer.</li>
     *     <li>state {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.State#CLOSING CLOSING}: This might be a
     *     {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#RST_ACK RST_ACK} - the
     *     Server acknowledges a RST sent by this client. The session is ended.</li>
     *     <li>state {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.State#SYN_SENT SYN_SENT}: This might be a
     *     {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#RST_ACK RST_ACK} - the
     *     Server acknowledges a RST sent by this client. A RST message is always sent before a SYN message. There
     *     is nothing to do here.</li>
     * </ul></p>
     *
     * @param segment
     *          The received segment with the operation code
     *          {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#RST RST} or
     *          {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#RST_ACK RST_ACK}.
     *
     * @return True if the segment has successfully been handled.
     */
    private boolean receiveRST(Segment segment) {
        if (mShowDebugLogs) {
            Log.d(TAG, "Receive RST or RST_ACK for sequence " + segment.getSequenceNumber());
        }

        switch (mState) {
            case RWCP.State.SYN_SENT:
                Log.i(TAG, "Received RST (sequence " + segment.getSequenceNumber() + ") in SYN_SENT state, ignoring " +
                        "segment.");
                return true;
            
            case RWCP.State.ESTABLISHED:
                // received RST
                Log.w(TAG, "Received RST (sequence " + segment.getSequenceNumber() + ") in ESTABLISHED state, " +
                        "terminating session, transfer failed.");
                terminateSession();
                mListener.onTransferFailed();
                return true;


            case RWCP.State.CLOSING:
                // received RST_ACK
                cancelTimeOut();
                validateAckSequence(RWCP.OpCode.Client.RST, segment.getSequenceNumber());
                reset(false);
                if (!mPendingData.isEmpty()) {
                    // expected when starting a session: RST sent prior SYN, sending SYN to start the session
                    if (!sendSYNSegment()) {
                        Log.w(TAG, "Start session of RWCP data transfer failed: sending of SYN failed.");
                        terminateSession();
                        mListener.onTransferFailed();
                    }
                }
                else {
                    // RST is acknowledged: transfer is finished
                    mListener.onTransferFinished();
                }
                return true;

            case RWCP.State.LISTEN:
            default:
                Log.w(TAG, "Received unexpected RST segment with sequence=" + segment.getSequenceNumber()
                        + " while in state " + RWCP.getStateLabel(mState));
                return false;
        }
    }

    /**
     * <p>This method is called by {@link #onReceiveRWCPSegment(byte[]) onReceiveRWCPSegment} when it has received a
     * {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#GAP GAP} segment.</p>
     * <p>A DATA_ACK segment can be expected on the following cases:
     * <ul>
     *     <li>state {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.State#ESTABLISHED ESTABLISHED}: the Server acknowledges
     *     the data it has received and that it misses segments after. This client then validates the acknowledged
     *     data and resends the next ones.</li>
     *     <li>state {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.State#CLOSING CLOSING}: when this client has sent a
     *     {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Client#RST RST} segment but it has not been fetched yet by the
     *     Server.</li>
     * </ul></p>
     *
     * @param segment
     *          The received segment with the operation code
     *          {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Server#GAP GAP}.
     *
     * @return True if the segment has successfully been handled.
     */
    private boolean receiveGAP(Segment segment) {
        if (mShowDebugLogs) {
            Log.d(TAG, "Receive GAP for sequence " + segment.getSequenceNumber());
        }

        switch (mState) {
            case RWCP.State.ESTABLISHED:
                if (mLastAckSequence > segment.getSequenceNumber()) {
                    Log.i(TAG, "Ignoring GAP (" + segment.getSequenceNumber() + ") as last ack sequence is "
                            + mLastAckSequence + ".");
                    return true;
                }
                if (mLastAckSequence <= segment.getSequenceNumber()) {
                    // Sequence number in GAP implies lost DATA_ACKs
                    // adjust window
                    decreaseWindow();
                    // validate the acknowledged segments if not known.
                    validateAckSequence(RWCP.OpCode.Client.DATA, segment.getSequenceNumber());
                }

                cancelTimeOut();
                resendDataSegment();
                return true;


            case RWCP.State.CLOSING:
                // RST had been sent, wait for the RST time out or RST ACK
                if (mShowDebugLogs) {
                    Log.i(TAG, "Received GAP(" + segment.getSequenceNumber()
                            + ") segment while in state CLOSING: segment discarded.");
                }
                return true;

            case RWCP.State.SYN_SENT:
            case RWCP.State.LISTEN:
            default:
                Log.w(TAG, "Received unexpected GAP segment with header " + segment.getHeader()
                        + " while in state " + RWCP.getStateLabel(mState));
                return false;
        }
    }

    /**
     * <p>This method is called when segments are timed out: this client didn't receive any acknowledgement for
     * them.</p>
     * <p>This client resends the segments from the last known acknowledged sequence number.</p>
     */
    private void onTimeOut() {
        if (isTimeOutRunning) {
            isTimeOutRunning = false;
            mIsResendingSegments = true;
            mAcknowledgedSegments = 0;

            if (mShowDebugLogs) {
                Log.i(TAG, "TIME OUT > re sending segments");
            }

            if (mState == RWCP.State.ESTABLISHED) {
                // Timed out segments are DATA segments: increasing data time out value
                mDataTimeOutMs *= 2;
                if (mDataTimeOutMs > RWCP.DATA_TIMEOUT_MS_MAX) {
                    mDataTimeOutMs = RWCP.DATA_TIMEOUT_MS_MAX;
                }

                resendDataSegment();
            }
            else {
                // SYN or RST segments are timed out
                resendSegment();
            }
        }
    }

    /**
     * <p>This method validates the segments which had been acknowledged by the Server and returns the number of
     * segments which had been acknowledged.</p>
     * <p>If this method couldn't validate the sequence it returns <code>-1</code>.</p>
     *
     * @param sequence
     *          The sequence number which acknowledges the corresponding segment and previous ones.
     * @param code
     *          The operation code which is expected to be found with the given sequence.
     *
     * @return The number of segments acknowledged by the given segment or -1 if it couldn't validate the given
     * sequence.
     */
    private int validateAckSequence(final int code, final int sequence) {
        final int NOT_VALIDATED = -1;

        if (sequence < 0) {
            Log.w(TAG, "Received ACK sequence (" + sequence + ") is less than 0.");
            return NOT_VALIDATED;
        }

        if (sequence > RWCP.SEQUENCE_NUMBER_MAX) {
            Log.w(TAG, "Received ACK sequence (" + sequence + ") is bigger than its maximum value ("
                    + RWCP.SEQUENCE_NUMBER_MAX + ").");
            return NOT_VALIDATED;
        }

        if (mLastAckSequence<mNextSequence && (sequence<mLastAckSequence || sequence>mNextSequence)) {
            Log.w(TAG, "Received ACK sequence (" + sequence + ") is out of interval: last received is " +
                    mLastAckSequence + " and next will be " + mNextSequence);
            return NOT_VALIDATED;
        }

        if (mLastAckSequence>mNextSequence && sequence<mLastAckSequence && sequence>mNextSequence) {
            Log.w(TAG, "Received ACK sequence (" + sequence + ") is out of interval: last received is " +
                    mLastAckSequence + " and next will be " + mNextSequence);
            return NOT_VALIDATED;
        }

        int acknowledged = 0;
        int nextAckSequence = mLastAckSequence;

        synchronized (mUnacknowledgedSegments) {
            while (nextAckSequence != sequence) {
                nextAckSequence = increaseSequenceNumber(nextAckSequence);
                if (removeSegmentFromQueue(code, nextAckSequence)) {
                    mLastAckSequence = nextAckSequence;
                    if (mCredits < mWindow) {
                        mCredits++;
                    }
                    acknowledged++;
                }
                else {
                    Log.w(TAG, "Error validating sequence " + nextAckSequence + ": no corresponding segment in " +
                            "pending segments.");
                }
            }
        }

        logState(acknowledged + " segment(s) validated with ACK sequence(code=" + code +  ", seq=" + sequence + ")");

        // increase the window size if qualified.
        increaseWindow(acknowledged);

        return acknowledged;
    }

    /**
     * <p>Builds and sends a RST segment. This method also add the segment to the list of unacknowledged segments.</p>
     *
     * @return True if the segment had been sent.
     */
    private boolean sendRSTSegment() {
        if (mState == RWCP.State.CLOSING) {
            // RST already sent waiting to be acknowledged
            return true;
        }

        boolean done;
        reset(false);
        synchronized (mUnacknowledgedSegments) {
            mState = RWCP.State.CLOSING;
            Segment segment = new Segment(RWCP.OpCode.Client.RST, mNextSequence);
            done = sendSegment(segment, RWCP.RST_TIMEOUT_MS);
            if (done) {
                mUnacknowledgedSegments.add(segment);
                mNextSequence = increaseSequenceNumber(mNextSequence);
                mCredits--;
                logState("send RST segment");
            }
        }
        return done;
    }

    /**
     * <p>This method log the current state of the following information:
     * <ul>
     *     <li>window size,</li>
     *     <li>number of credits,</li>
     *     <li>last sequence acknowledged by the Server,</li>
     *     <li>next sequence to send to the Server,</li>
     *     <li>number of segments waiting to be acknowledged,</li>
     *     <li>number of data packets waiting to be sent.</li>
     * </ul></p>
     * <p>The logged message should look as follows where <code>label</code> is the given label:</p>
     * <blockquote><pre>label
     window  = a 		last = c 		PSegments = e
     credits = b 		next = d 		PData     = f</pre></blockquote>
     * <p>This method is usually called after an event which changes the states of the displayed information. For
     * instance when a segment is sent, or when some are resents.</p>
     *
     *
     * @param label
     *          The label to identify the logging.
     */
    private void logState(String label) {
        if (mShowDebugLogs) {
            String message = label + "\t\t\tstate=" + RWCP.getStateLabel(mState)
                    + "\n\twindow  = " + mWindow + " \t\tlast = " + mLastAckSequence
                    + " \t\tPSegments = " + mUnacknowledgedSegments.size()
                    + "\n\tcredits = " + mCredits + " \t\tnext = " + mNextSequence
                    + " \t\tPData     = " + mPendingData.size();
            Log.d(TAG, message);
        }
    }

    /**
     * <p>Builds and sends a SYN segment. This method also adds the segment to the list of unacknowledged segments.</p>
     *
     * @return True if the segment had been sent.
     */
    private boolean sendSYNSegment() {
        boolean done;
        synchronized (mUnacknowledgedSegments) {
            mState = RWCP.State.SYN_SENT;
            Segment segment = new Segment(RWCP.OpCode.Client.SYN, mNextSequence);
            done = sendSegment(segment, RWCP.SYN_TIMEOUT_MS);
            if (done) {
                mUnacknowledgedSegments.add(segment);
                mNextSequence = increaseSequenceNumber(mNextSequence);
                mCredits--;
                logState("send SYN segment");
            }
        }
        return done;
    }

    /**
     * <p>Sends the pending DATA segments depending on available credits. If this client needs to resend
     * unacknowledged segments, this method stops its process.</p>
     */
    private void sendDataSegment() {
        while (mCredits > 0 && !mPendingData.isEmpty() && !mIsResendingSegments && mState == RWCP.State.ESTABLISHED) {
            synchronized (mUnacknowledgedSegments) {
                byte[] data = mPendingData.poll();
                Segment segment = new Segment(RWCP.OpCode.Client.DATA, mNextSequence, data);
                sendSegment(segment, mDataTimeOutMs);
                mUnacknowledgedSegments.add(segment);
                mNextSequence = increaseSequenceNumber(mNextSequence);
                mCredits--;
            }
        }
        logState("send DATA segments");
    }

    /**
     * <p>This method increases the given sequence number by 1. As a sequence number is always from 0 to
     * {@link RWCP#SEQUENCE_NUMBER_MAX SEQUENCE_NUMBER_MAX}, the method restarts to 0 if the new value is greater than
     * {@link RWCP#SEQUENCE_NUMBER_MAX SEQUENCE_NUMBER_MAX}.</p>
     *
     * @param sequence
     *          The sequence number to increase.
     *
     * @return The increased value of the sequence number.
     */
    private int increaseSequenceNumber(int sequence) {
        return (sequence+1) % (RWCP.SEQUENCE_NUMBER_MAX+1);
    }

    /**
     * <p>This method decreases the given sequence number by the given number. As a sequence number is always from 0 to
     * {@link RWCP#SEQUENCE_NUMBER_MAX SEQUENCE_NUMBER_MAX}, the method restarts to 63 if the new value is less than
     * {@link RWCP#SEQUENCE_NUMBER_MAX SEQUENCE_NUMBER_MAX}.</p>
     *
     * @param sequence
     *          The sequence number to increase.
     * @param decrease
     *          The number to decrease the sequence of.
     *
     * @return The decreased value of the sequence number.
     */
    private int decreaseSequenceNumber(int sequence, int decrease) {
        return (sequence - decrease + RWCP.SEQUENCE_NUMBER_MAX + 1) % (RWCP.SEQUENCE_NUMBER_MAX+1);
    }

    /**
     * <p>This method resends the segments which are still unacknowledged and are not DATA segments.</p>
     */
    private void resendSegment() {
        if (mState == RWCP.State.ESTABLISHED) {
            Log.w(TAG, "Trying to resend non data segment while in ESTABLISHED state.");
            return;
        }

        mIsResendingSegments = true;
        mCredits = mWindow;

        synchronized (mUnacknowledgedSegments) {
            // resend the unacknowledged segments corresponding to the window
            for (Segment segment : mUnacknowledgedSegments) {
                int delay = (segment.getOperationCode() == RWCP.OpCode.Client.SYN) ? RWCP.SYN_TIMEOUT_MS :
                        (segment.getOperationCode() == RWCP.OpCode.Client.RST) ? RWCP.RST_TIMEOUT_MS : mDataTimeOutMs;
                sendSegment(segment, delay);
                mCredits--;
            }

        }
        logState("resend segments");

        mIsResendingSegments = false;
    }

    /**
     * <p>This method resends the DATA segments which are still unacknowledged.</p>
     */
    private void resendDataSegment() {
        if (mState != RWCP.State.ESTABLISHED) {
            Log.w(TAG, "Trying to resend data segment while not in ESTABLISHED state.");
            return;
        }

        mIsResendingSegments = true;
        mCredits = mWindow;
        logState("reset credits");

        synchronized (mUnacknowledgedSegments) {
            // if they are more unacknowledged segments than available credits, these extra segments are not anymore
            // unacknowledged but pending
            int moved = 0;
            while (mUnacknowledgedSegments.size() > mCredits) {
                Segment segment = mUnacknowledgedSegments.getLast();
                if (segment.getOperationCode() == RWCP.OpCode.Client.DATA) {
                    removeSegmentFromQueue(segment);
                    mPendingData.addFirst(segment.getPayload());
                    moved++;
                }
                else {
                    Log.w(TAG, "Segment " + segment.toString() + " in pending segments but not a DATA segment.");
                    break;
                }
            }

            // if some segments have been moved to the pending state, the next sequence number has changed.
            mNextSequence = decreaseSequenceNumber(mNextSequence, moved);

            // resend the unacknowledged segments corresponding to the window
            for (Segment segment : mUnacknowledgedSegments) {
                sendSegment(segment, mDataTimeOutMs);
                mCredits--;
            }
        }

        logState("Resend DATA segments");

        mIsResendingSegments = false;

        if (mCredits > 0) {
            sendDataSegment();
        }
    }

    /**
     * <p>This method transmits the bytes of a segment to a listener in order to send them to the Server.</p>
     * <p>This method also starts the time out for the segment.</p>
     *
     * @param segment
     *          The segment to send to a Server.
     * @param timeout
     *          The timeout in ms to use for the sending of this segment.
     *
     * @return True if the segment could be sent to the Server.
     */
    private boolean sendSegment(Segment segment, int timeout) {
        byte[] bytes = segment.getBytes();
        if (mListener.sendRWCPSegment(bytes)) {
            startTimeOut(timeout);
            return true;
        }

        return false;
    }

    /**
     * <p>Remove the segment which corresponds to the given code and sequence from the pending segments queue which
     * contains the unacknowledged and sent segments.</p>
     *
     * @return True if a corresponding segment could be found, false otherwise.
     */
    private boolean removeSegmentFromQueue(int code, int sequence) {
        synchronized (mUnacknowledgedSegments) {
            for (Segment segment : mUnacknowledgedSegments) {
                if (segment.getOperationCode() == code && segment.getSequenceNumber() == sequence) {
                    mUnacknowledgedSegments.remove(segment);
                    return true;
                }
            }
        }
        Log.w(TAG, "Pending segments does not contain acknowledged segment: code=" + code + " \tsequence=" + sequence);
        return false;
    }

    /**
     * <p>Remove the given segment from the pending segments queue which contains the unacknowledged and sent
     * segments.</p>
     *
     * @return True if a corresponding segment could be found, false otherwise.
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean removeSegmentFromQueue(Segment segment) {
        synchronized (mUnacknowledgedSegments) {
            if (mUnacknowledgedSegments.remove(segment)) {
                return true;
            }
        }
        Log.w(TAG, "Pending unack segments does not contain segment (code=" + segment.getOperationCode()
                + ", seq=" + segment.getSequenceNumber() + ")");
        return false;
    }

    /**
     * <p>To reset all fields at their default state for a new session. Setting <code>complete</code> to True empty the
     * queue of pending data.</p>
     *
     * @param complete True to completely reset this client. False to only reset a session.
     */
    private void reset(boolean complete) {
        synchronized (mUnacknowledgedSegments) {
            mLastAckSequence = -1;
            mNextSequence = 0;
            mState = RWCP.State.LISTEN;
            mUnacknowledgedSegments.clear();
            mWindow = RWCP.WINDOW_DEFAULT;
            mAcknowledgedSegments = 0;
            mCredits = mWindow;
            cancelTimeOut();
        }

        if (complete) {
            mPendingData.clear();
        }

        logState("reset");
    }

    /**
     * <p>This method keeps a record of how many data segments had been acknowledged successfully in a row. If this
     * number is greater than the current window this method increases the window size.</p>
     *
     * @param acknowledged the number of segments which had been acknowledged by a received acknowledgement.
     */
    private void increaseWindow(int acknowledged) {
        mAcknowledgedSegments += acknowledged;
        if (mAcknowledgedSegments > mWindow && mWindow < RWCP.WINDOW_MAX) {
            mAcknowledgedSegments = 0;
            mWindow++;
            mCredits++;
            logState("increase window to " + mWindow);
        }
    }

    /**
     * <p>To decrease the window size if the current one is too big and leads to the reception of GAP segments.</p>
     */
    private void decreaseWindow() {
        mWindow = ((mWindow - 1) / 2) + 1;
        if (mWindow > RWCP.WINDOW_MAX || mWindow < 1) {
            mWindow = 1;
        }

        mAcknowledgedSegments = 0;
        mCredits = mWindow;

        logState("decrease window to " + mWindow);
    }


    // ====== TIMEOUT PROCESS ===============================================================

    /**
     * <p>To start a Runnable which will be thrown after the given delay. This Runnable deals with segments which
     * had not been acknowledged anf might have not been received.</p>
     */
    private void startTimeOut(long delay) {
        if (isTimeOutRunning) {
            mHandler.removeCallbacks(mTimeOutRunnable);
        }

        isTimeOutRunning = true;
        mHandler.postDelayed(mTimeOutRunnable, delay);
    }

    /**
     * <p>To cancel the time out by cancelling the Runnable waiting to be thrown when its delay had passed.</p>
     */
    private void cancelTimeOut() {
        if (isTimeOutRunning) {
            mHandler.removeCallbacks(mTimeOutRunnable);
            isTimeOutRunning = false;
        }
    }


    // ====== INNER CLASSES ====================================================================

    /**
     * <p>This interface allows this client to dispatch messages or events to a listener.</p>
     */
    public interface RWCPListener {

        /**
         * <p>To send the bytes of a RWCP segment to a connected Server.</p>
         *
         * @param bytes
         *          The bytes to send.
         *
         * @return True if the sending could be handled.
         */
        boolean sendRWCPSegment(byte[] bytes);

        /**
         * <p>Called when the transfer with RWCP has failed. The transfer fails in the following cases:
         * <ul>
         *     <li>The sending of a segment fails at the transport layer.</li>
         *     <li>The Server sent a {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode.Client#RST RST} segment.</li>
         * </ul></p>
         */
        void onTransferFailed();

        /**
         * <p>Called when the transfer of all the data given to this client had been successfully sent and
         * acknowledged.</p>
         */
        void onTransferFinished();

        /**
         * <p>Called when some new segments had been acknowledged to inform the listener.</p>
         *
         * @param acknowledged
         *              The number of segments which had been acknowledged.
         */
        void onTransferProgress(int acknowledged);
    }

    /**
     * <p>A Runnable to define what should be done when a segment is timed out.</p>
     * <p>A segment is considered as being timed out if this client has not received a corresponding acknowledgement
     * when this runnable is triggered.</p>
     * <p>RWCP uses unreliable messages leading to some segments which might have not been received by a connected
     * Server. This Runnable helps to deal with these messages by calling {@link #onTimeOut() onTimeOut}.</p>
     */
    private class TimeOutRunnable implements Runnable {
        @Override
        public void run() {
            onTimeOut();
        }
    }
}
