/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.RIL_UNSOL_ACCESS_ALLOWED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CONNECTION_SETUP_FAILURE;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_NOTIFY_ANBR;

import android.hardware.radio.ims.IRadioImsIndication;
import android.os.AsyncResult;

/**
 * Interface declaring unsolicited radio indications for IMS APIs.
 */
public class ImsIndication extends IRadioImsIndication.Stub {
    private final RIL mRil;

    public ImsIndication(RIL ril) {
        mRil = ril;
    }

    /**
     * Fired by radio when any IMS traffic is not sent to network due to any failure
     * on cellular networks.
     *
     * @param indicationType Type of radio indication.
     * @param token The token provided by {@link #notifyImsTraffic} or {@link #performACBcheck}.
     * @param info Connection failure information.
     */
    public void onConnectionSetupFailure(int indicationType, int token,
            android.hardware.radio.ims.ConnectionFailureInfo info) {
        mRil.processIndication(RIL.IMS_SERVICE, indicationType);

        int[] response = new int[4];
        response[0] = token;
        response[1] = info.failureReason;
        response[2] = info.causeCode;
        response[3] = info.waitTimeMillis;

        if (RIL.RILJ_LOGD) mRil.unsljLogRet(RIL_UNSOL_CONNECTION_SETUP_FAILURE, response);

        mRil.mConnectionSetupFailureRegistrants.notifyRegistrants(
                new AsyncResult(null, response, null));
    }

    /**
     * Fired by radio in response to {@link #performAcbCheck}
     * if the access class check is allowed for the requested traffic type.
     *
     * @param indicationType Type of radio indication
     * @param token The token provided by {@link #performAcbCheck}
     */
    public void onAccessAllowed(int indicationType, int token) {
        mRil.processIndication(RIL.IMS_SERVICE, indicationType);

        int[] response = new int[1];
        response[0] = token;

        if (RIL.RILJ_LOGD) mRil.unsljLogRet(RIL_UNSOL_ACCESS_ALLOWED, response);

        mRil.mAccessAllowedRegistrants.notifyRegistrants(new AsyncResult(null, response, null));
    }

    /**
     * Fired by radio when ANBR is received form the network.
     *
     * @param indicationType Type of radio indication.
     * @param qosSessionId QoS session ID is used to identify media stream such as audio or video.
     * @param imsdirection Direction of this packet stream (e.g. uplink or downlink).
     * @param bitsPerSecond The recommended bit rate for the UE
     *        for a specific logical channel and a specific direction by the network.
     */
    public void notifyAnbr(int indicationType, int qosSessionId, int imsdirection,
            int bitsPerSecond) {
        mRil.processIndication(RIL.IMS_SERVICE, indicationType);

        int[] response = new int[3];
        response[0] = qosSessionId;
        response[1] = imsdirection;
        response[2] = bitsPerSecond;

        if (RIL.RILJ_LOGD) mRil.unsljLogRet(RIL_UNSOL_NOTIFY_ANBR, response);

        mRil.mNotifyAnbrRegistrants.notifyRegistrants(new AsyncResult(null, response, null));
    }
}
