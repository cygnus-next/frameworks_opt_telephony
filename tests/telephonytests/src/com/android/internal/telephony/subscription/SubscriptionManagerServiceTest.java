/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.internal.telephony.subscription;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_ICCID1;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.content.pm.PackageManager;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.SubscriptionProvider;
import com.android.internal.telephony.subscription.SubscriptionManagerService.SubscriptionManagerServiceCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SubscriptionManagerServiceTest extends TelephonyTest {

    private SubscriptionManagerService mSubscriptionManagerServiceUT;

    private final SubscriptionProvider mSubscriptionProvider = new SubscriptionProvider();

    // mocked
    private SubscriptionManagerServiceCallback mMockedSubscriptionManagerServiceCallback;

    @Before
    public void setUp() throws Exception {
        logd("SubscriptionManagerServiceTest +Setup!");
        super.setUp(getClass().getSimpleName());

        mMockedSubscriptionManagerServiceCallback = Mockito.mock(
                SubscriptionManagerServiceCallback.class);
        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mSubscriptionProvider);
        mSubscriptionManagerServiceUT = new SubscriptionManagerService(mContext, Looper.myLooper());

        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mMockedSubscriptionManagerServiceCallback).invokeFromExecutor(any(Runnable.class));

        mSubscriptionManagerServiceUT.registerCallback(mMockedSubscriptionManagerServiceCallback);
        // Database loading is on a different thread. Need to wait a bit.
        waitForMs(100);
        processAllFutureMessages();

        logd("SubscriptionManagerServiceTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testAddSubInfo() {
        doReturn(true).when(mPackageManager).hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE);

        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1,
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
    }
}