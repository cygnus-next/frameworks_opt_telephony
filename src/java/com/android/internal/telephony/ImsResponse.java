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

import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.ims.IRadioImsResponse;

/**
 * Interface declaring response functions to solicited radio requests for IMS APIs.
 */
public class ImsResponse extends IRadioImsResponse.Stub {
    private final RIL mRil;

    public ImsResponse(RIL ril) {
        mRil = ril;
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void setSrvccCallInfoResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void updateImsRegistrationInfoResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void notifyImsTrafficResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void performAcbCheckResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void setAnbrEnabledResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void sendAnbrQueryResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }
}