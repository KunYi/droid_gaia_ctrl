/* ************************************************************************************************
 * Copyright 2017 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/

package com.qualcomm.qti.gaiacontrol.rwcp;

import android.util.Log;

import com.qualcomm.qti.gaiacontrol.Utils;
import com.qualcomm.qti.libraries.vmupgrade.VMUUtils;

/**
 * <p>This class represents the data structure of the messages sent over RWCP. These messages are
 * called segments and their structure is as follows:</p>
 * <blockquote><pre>
 * 0 byte     1         ...         n
 * +----------+----------+----------+
 * |  HEADER  |       PAYLOAD       |
 * +----------+----------+----------+
 * </pre></blockquote>
 * <p>The header of a RWCP segment contains the information to identify the segment: a sequence number and an
 * operation code. The header is contained in one byte for which the bits are allocated as follows:</p>
 * <blockquote><pre>
 * 0 bit     ...         6          7          8
 * +----------+----------+----------+----------+
 * |   SEQUENCE NUMBER   |   OPERATION CODE    |
 * +----------+----------+----------+----------+
 * </pre></blockquote>
 *
 * @since 3.3.0
 */
class Segment {

    // ====== FIELDS ====================================================================

    /**
     * <p>The tag to display for logs.</p>
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final String TAG = "Segment";
    /**
     * <p>The operation code which defines the type of segment.</p>
     */
    private final int mOperationCode;
    /**
     * <p>The sequence number which defines the segment in a RWCP session.</p>
     */
    private final int mSequenceNumber;
    /**
     * The value of the header built on a combination of the operation code and the sequence number.
     */
    private final byte mHeader;
    /**
     * The payload contains the data of the segment which is transferred using RWCP.
     */
    private final byte[] mPayload;
    /**
     * The bytes array which contains this segment.
     */
    private byte[] mBytes;


    // ====== CONSTRUCTORS ====================================================================

    /**
     * <p>To build a segment with its operation code, its sequence number and payload.</p>
     *
     * @param operationCode
     *          The code which defines the type of segment.
     * @param sequenceNumber
     *          The sequence number which identifies this segment in a RWCP session.
     * @param payload
     *          The data which is transferred using this segment.
     */
    Segment(int operationCode, int sequenceNumber, byte[] payload) {
        mOperationCode = operationCode;
        mSequenceNumber = sequenceNumber;
        mPayload = payload;
        mHeader = (byte) ((operationCode << RWCP.Segment.Header.SEQUENCE_NUMBER_BITS_LENGTH) | sequenceNumber);
    }

    /**
     * <p>To build a segment with an empty payload.</p>
     *
     * @param operationCode
     *          The code which defines the type of segment.
     * @param sequenceNumber
     *          The sequence number which identifies this segment in a RWCP session.
     */
    Segment(int operationCode, int sequenceNumber) {
        this(operationCode, sequenceNumber, new byte[0]);
    }

    /**
     * <p>To build a segment from a byte array.</p>
     * <p>If a segment couldn't be identified, this segment will have the following values:</p>
     * <ul>
     *     <li>operation code: {@link com.qualcomm.qti.gaiacontrol.rwcp.RWCP.OpCode#NONE RWCP.OpCode.NONE},</li>
     *     <li>sequence number: <code>-1</code>,</li>
     *     <li>header: <code>-1</code>,</li>
     *     <li>payload: the given bytes.</li>
     * </ul>
     *
     * @param bytes
     *          The byte array which corresponds to a segment.
     */
    Segment(byte[] bytes) {
        mBytes = bytes;

        if (bytes == null || bytes.length < RWCP.Segment.REQUIRED_INFORMATION_LENGTH) {
            Log.w(TAG, "Building of RWCP Segment failed: the byte array does not contain the minimum " +
                    "required information.\nbytes: "
                    + ((bytes != null) ? VMUUtils.getHexadecimalStringFromBytes (bytes) : "null"));
            mOperationCode = RWCP.OpCode.NONE;
            mSequenceNumber = -1;
            mHeader = -1;
            mPayload = bytes;
        }
        else {
            mHeader = bytes[RWCP.Segment.HEADER_OFFSET];
            mOperationCode = getBits(mHeader, RWCP.Segment.Header.OPERATION_CODE_BIT_OFFSET, RWCP.Segment
                    .Header.OPERATION_CODE_BITS_LENGTH);
            mSequenceNumber = getBits(mHeader, RWCP.Segment.Header.SEQUENCE_NUMBER_BIT_OFFSET, RWCP.Segment
                    .Header.SEQUENCE_NUMBER_BITS_LENGTH);
            mPayload = new byte[bytes.length - RWCP.Segment.HEADER_LENGTH];
            System.arraycopy(bytes, RWCP.Segment.PAYLOAD_OFFSET, mPayload, 0, mPayload.length);
        }
    }


    // ====== GETTERS ====================================================================

    /**
     * To get the operation code of this segment.
     *
     * @return The operation code.
     */
    int getOperationCode() {
        return mOperationCode;
    }

    /**
     * To get the sequence number of this segment.
     *
     * @return The sequence number.
     */
    int getSequenceNumber() {
        return mSequenceNumber;
    }

    /**
     * To get the payload of this segment.
     *
     * @return The payload.
     */
    byte[] getPayload() {
        return mPayload;
    }

    /**
     * <p>To get the bytes of this segment.</p>
     * <p>If the bytes have not been built yet this method builds the byte array as follows:</p>
     * <blockquote><pre>
     * 0 byte     1         ...         n
     * +----------+----------+----------+
     * |  HEADER  |       PAYLOAD       |
     * +----------+----------+----------+
     * </pre></blockquote>
     *
     * @return The byte array which contains this segment information.
     */
    byte[] getBytes() {
        if (mBytes == null) {
            int payloadLength = (mPayload == null) ? 0 : mPayload.length;
            mBytes = new byte[RWCP.Segment.REQUIRED_INFORMATION_LENGTH + payloadLength];
            mBytes[RWCP.Segment.HEADER_OFFSET] = mHeader;
            // data if exists
            if (payloadLength > 0) {
                System.arraycopy(mPayload, 0, mBytes, RWCP.Segment.PAYLOAD_OFFSET, mPayload.length);
            }
        }

        return mBytes;
    }

    /**
     * <p>To get the header of this segment.</p>
     *
     * @return The header information.
     */
    byte getHeader() {
        return mHeader;
    }


    // ====== STRING METHODS ====================================================================

    @Override // Object
    public String toString() {
        return toString(false);
    }

    /**
     * <p>To get a String representation of this object.</p>
     * <p>This method returns the data displayed as one of the following depending on the parameters:
     * <ol>
     *     <li>[code=0, sequence=0]</li>
     *     <li>[code=0, sequence=0, payload=0x00 0x00 0x00...]</li>
     * </ol></p>
     * <p>Setting up "showPayload" to True gives the String representation of the payload.</p>
     *
     * @param showPayload
     *      True to show the payload, false to only get the operation code and sequence number information.
     *
     * @return A String representation of the object.
     */
    String toString(boolean showPayload) {
        StringBuilder builder = new StringBuilder();
        builder.append("[code=").append(mOperationCode).append(", sequence=").append(mSequenceNumber);
        if (showPayload) {
            builder.append(", payload=").append(Utils.getStringFromBytes(mPayload));
        }
        builder.append("]");

        return builder.toString();
    }


    // ====== STATIC METHODS ====================================================================

    /**
     * <p>To get an information contained in a byte, starting at the given offset bit and of the given bit length.</p>
     *
     * @param value
     *          The 8 bits value.
     * @param offset
     *          The bit offset at which to find the information.
     * @param length
     *          The number of bits representing the information.
     *
     * @return The split value.
     */
    private static int getBits(byte value, int offset, int length) {
        int mask = ((1 << length) - 1) << offset;
        return (value & mask) >>> offset;
    }

}
