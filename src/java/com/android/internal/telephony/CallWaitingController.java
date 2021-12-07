/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.telephony.CarrierConfigManager.ImsSs.CALL_WAITING_SYNC_NONE;
import static android.telephony.CarrierConfigManager.ImsSs.KEY_TERMINAL_BASED_CALL_WAITING_DEFAULT_ENABLED_BOOL;
import static android.telephony.CarrierConfigManager.ImsSs.KEY_TERMINAL_BASED_CALL_WAITING_SYNC_TYPE_INT;
import static android.telephony.CarrierConfigManager.ImsSs.KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CW;

import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_NONE;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VOICE;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.telephony.Rlog;

/**
 * Controls the change of the user setting of the call waiting service
 *
 * {@hide}
 */
public class CallWaitingController {

    public static final String LOG_TAG = "CallWaiting";
    private static final boolean DBG = false; /* STOPSHIP if true */

    // Terminal-based call waiting is not supported. */
    public static final int TERMINAL_BASED_NOT_SUPPORTED = -1;
    // Terminal-based call waiting is supported but not activated. */
    public static final int TERMINAL_BASED_NOT_ACTIVATED = 0;
    // Terminal-based call waiting is supported and activated. */
    public static final int TERMINAL_BASED_ACTIVATED = 1;

    @VisibleForTesting
    public static final String PREFERENCE_TBCW = "terminal_based_call_waiting";
    @VisibleForTesting
    public static final String KEY_SUB_ID = "subId";
    @VisibleForTesting
    public static final String KEY_STATE = "state";
    @VisibleForTesting
    public static final String KEY_CS_SYNC = "cs_sync";

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                Bundle bundle = intent.getExtras();
                if (bundle == null) {
                    return;
                }
                int slotId = bundle.getInt(CarrierConfigManager.EXTRA_SLOT_INDEX,
                        SubscriptionManager.INVALID_PHONE_INDEX);

                if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                    Rlog.e(LOG_TAG, "onReceive ACTION_CARRIER_CONFIG_CHANGED invalid slotId "
                            + slotId);
                    return;
                }

                if (slotId == mPhone.getPhoneId()) {
                    onCarrierConfigChanged();
                }
            }
        }
    };

    private boolean mSupportedByImsService = false;
    private boolean mValidSubscription = false;

    // The user's last setting of terminal-based call waiting
    private int mCallWaitingState = TERMINAL_BASED_NOT_SUPPORTED;

    private int mSyncPreference = CALL_WAITING_SYNC_NONE;
    private int mLastSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    private GsmCdmaPhone mPhone;
    private Context mContext;

    // Constructors
    public CallWaitingController(GsmCdmaPhone phone) {
        mPhone = phone;
        mContext = phone.getContext();
    }

    private void initialize() {
        mContext.registerReceiver(mReceiver, new IntentFilter(
                CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));

        int phoneId = mPhone.getPhoneId();
        int subId = mPhone.getSubId();
        SharedPreferences sp =
                mContext.getSharedPreferences(PREFERENCE_TBCW, Context.MODE_PRIVATE);
        mLastSubId = sp.getInt(KEY_SUB_ID + phoneId, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        mCallWaitingState = sp.getInt(KEY_STATE + subId, TERMINAL_BASED_NOT_SUPPORTED);
        mSyncPreference = sp.getInt(KEY_CS_SYNC + phoneId, CALL_WAITING_SYNC_NONE);

        Rlog.i(LOG_TAG, "initialize phoneId=" + phoneId
                + ", lastSubId=" + mLastSubId + ", subId=" + subId
                + ", state=" + mCallWaitingState + ", sync=" + mSyncPreference);
    }

    /**
     * Returns the cached user setting.
     *
     * Possible values are
     * {@link #TERMINAL_BASED_NOT_SUPPORTED},
     * {@link #TERMINAL_BASED_NOT_ACTIVATED}, and
     * {@link #TERMINAL_BASED_ACTIVATED}.
     */
    @VisibleForTesting
    public int getTerminalBasedCallWaitingState() {
        if (!mValidSubscription) return TERMINAL_BASED_NOT_SUPPORTED;
        return mCallWaitingState;
    }

    /**
     * Serves the user's requests to interrogate the call waiting service
     *
     * @return true when terminal-based call waiting is supported, otherwise false
     */
    @VisibleForTesting
    public boolean getCallWaiting(@Nullable Message onComplete) {
        if (mCallWaitingState == TERMINAL_BASED_NOT_SUPPORTED) return false;

        Rlog.i(LOG_TAG, "getCallWaiting " + mCallWaitingState);

        if (mSyncPreference == CALL_WAITING_SYNC_NONE) {
            sendGetCallWaitingResponse(onComplete);
            return true;
        }

        return false;
    }

    /**
     * Serves the user's requests to set the call waiting service
     *
     * @param serviceClass the target service class. Values are CommandsInterface.SERVICE_CLASS_*.
     * @return true when terminal-based call waiting is supported, otherwise false
     */
    @VisibleForTesting
    public boolean setCallWaiting(boolean enable, int serviceClass, @Nullable Message onComplete) {
        if (mCallWaitingState == TERMINAL_BASED_NOT_SUPPORTED) return false;

        if ((serviceClass & SERVICE_CLASS_VOICE) != SERVICE_CLASS_VOICE) return false;

        Rlog.i(LOG_TAG, "setCallWaiting enable=" + enable + ", service=" + serviceClass);

        if (mSyncPreference == CALL_WAITING_SYNC_NONE) {
            updateState(
                    enable ? TERMINAL_BASED_ACTIVATED : TERMINAL_BASED_NOT_ACTIVATED);

            sendToTarget(onComplete, null, null);
            return true;
        }

        return false;
    }

    private void sendToTarget(Message onComplete, Object result, Throwable exception) {
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, result, exception);
            onComplete.sendToTarget();
        }
    }

    private void sendGetCallWaitingResponse(Message onComplete) {
        if (onComplete != null) {
            int serviceClass = SERVICE_CLASS_NONE;
            if (mCallWaitingState == TERMINAL_BASED_ACTIVATED) {
                serviceClass = SERVICE_CLASS_VOICE;
            }
            sendToTarget(onComplete, new int[] { mCallWaitingState, serviceClass }, null);
        }
    }

    private void onCarrierConfigChanged() {
        int subId = mPhone.getSubId();
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Rlog.i(LOG_TAG, "onCarrierConfigChanged invalid subId=" + subId);

            mValidSubscription = false;
            return;
        }

        CarrierConfigManager configManager = mContext.getSystemService(CarrierConfigManager.class);
        PersistableBundle b = configManager.getConfigForSubId(subId);

        updateCarrierConfig(subId, b, false);
    }

    /**
     * @param enforced only used for test
     */
    @VisibleForTesting
    public void updateCarrierConfig(int subId, PersistableBundle b, boolean enforced) {
        mValidSubscription = true;

        if (b == null) return;

        boolean supportsTerminalBased = false;
        int[] services = b.getIntArray(KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY);
        if (services != null) {
            for (int service : services) {
                if (service == SUPPLEMENTARY_SERVICE_CW) {
                    supportsTerminalBased = true;
                }
            }
        }
        int syncPreference = b.getInt(KEY_TERMINAL_BASED_CALL_WAITING_SYNC_TYPE_INT,
                CALL_WAITING_SYNC_NONE);
        boolean activated = b.getBoolean(KEY_TERMINAL_BASED_CALL_WAITING_DEFAULT_ENABLED_BOOL);
        int defaultState = supportsTerminalBased
                ? (activated ? TERMINAL_BASED_ACTIVATED : TERMINAL_BASED_NOT_ACTIVATED)
                : TERMINAL_BASED_NOT_SUPPORTED;
        int savedState = getSavedState(subId);

        if (DBG) {
            Rlog.d(LOG_TAG, "updateCarrierConfig phoneId=" + mPhone.getPhoneId()
                    + ", subId=" + subId + ", support=" + supportsTerminalBased
                    + ", sync=" + syncPreference + ", default=" + defaultState
                    + ", savedState=" + savedState);
        }

        int desiredState = savedState;

        if (enforced) {
            desiredState = defaultState;
        } else {
            if (defaultState == TERMINAL_BASED_NOT_SUPPORTED) {
                desiredState = TERMINAL_BASED_NOT_SUPPORTED;
            } else if (savedState == TERMINAL_BASED_NOT_SUPPORTED) {
                desiredState = defaultState;
            }
        }

        updateState(desiredState, syncPreference, enforced);
    }

    private void updateState(int state) {
        updateState(state, mSyncPreference, false);
    }

    private void updateState(int state, int syncPreference, boolean enforced) {
        int subId = mPhone.getSubId();

        if (mLastSubId == subId
                && mCallWaitingState == state
                && mSyncPreference == syncPreference
                && (!enforced)) {
            return;
        }

        int phoneId = mPhone.getPhoneId();

        Rlog.i(LOG_TAG, "updateState phoneId=" + phoneId
                + ", subId=" + subId + ", state=" + state
                + ", sync=" + syncPreference + ", enforced=" + enforced);

        SharedPreferences sp =
                mContext.getSharedPreferences(PREFERENCE_TBCW, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(KEY_SUB_ID + phoneId, subId);
        editor.putInt(KEY_STATE + subId, state);
        editor.putInt(KEY_CS_SYNC + phoneId, syncPreference);
        editor.apply();

        mCallWaitingState = state;
        mLastSubId = subId;
        mSyncPreference = syncPreference;

        mPhone.setTerminalBasedCallWaitingStatus(mCallWaitingState);
    }

    private int getSavedState(int subId) {
        SharedPreferences sp =
                mContext.getSharedPreferences(PREFERENCE_TBCW, Context.MODE_PRIVATE);
        int state = sp.getInt(KEY_STATE + subId, TERMINAL_BASED_NOT_SUPPORTED);

        Rlog.i(LOG_TAG, "getSavedState subId=" + subId + ", state=" + state);

        return state;
    }
    /**
     * Sets whether the device supports the terminal-based call waiting.
     * Only for test
     */
    @VisibleForTesting
    public void setTerminalBasedCallWaitingSupported(boolean supported) {
        if (mSupportedByImsService == supported) return;

        Rlog.i(LOG_TAG, "setTerminalBasedCallWaitingSupported " + supported);

        mSupportedByImsService = supported;

        if (supported) {
            initialize();
            onCarrierConfigChanged();
        } else {
            mContext.unregisterReceiver(mReceiver);
            updateState(TERMINAL_BASED_NOT_SUPPORTED);
        }
    }
}
