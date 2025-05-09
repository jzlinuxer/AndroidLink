/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net;

import static org.junit.Assert.assertEquals;

import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class CaptivePortalTest {
    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final String TEST_PACKAGE_NAME = "com.google.android.test";

    private final class MyCaptivePortalImpl extends ICaptivePortal.Stub {
        int mCode = -1;
        String mPackageName = null;

        @Override
        public void appResponse(final int response) throws RemoteException {
            mCode = response;
        }

        @Override
        public void appRequest(final int request) throws RemoteException {
            mCode = request;
        }

        @Override
        public void setDelegateUid(int uid, IBinder binder, IIntResultListener listener) {
        }

        // This is only @Override on R-
        public void logEvent(int eventId, String packageName) throws RemoteException {
            mCode = eventId;
            mPackageName = packageName;
        }
    }

    private interface TestFunctor {
        void useCaptivePortal(CaptivePortal o);
    }

    private MyCaptivePortalImpl runCaptivePortalTest(TestFunctor f) {
        final MyCaptivePortalImpl cp = new MyCaptivePortalImpl();
        f.useCaptivePortal(new CaptivePortal(cp.asBinder()));
        return cp;
    }

    @Test
    public void testReportCaptivePortalDismissed() {
        final MyCaptivePortalImpl result =
                runCaptivePortalTest(c -> c.reportCaptivePortalDismissed());
        assertEquals(result.mCode, CaptivePortal.APP_RETURN_DISMISSED);
    }

    @Test
    public void testIgnoreNetwork() {
        final MyCaptivePortalImpl result = runCaptivePortalTest(c -> c.ignoreNetwork());
        assertEquals(result.mCode, CaptivePortal.APP_RETURN_UNWANTED);
    }

    @Test
    public void testUseNetwork() {
        final MyCaptivePortalImpl result = runCaptivePortalTest(c -> c.useNetwork());
        assertEquals(result.mCode, CaptivePortal.APP_RETURN_WANTED_AS_IS);
    }

    @IgnoreUpTo(Build.VERSION_CODES.Q)
    @Test
    public void testReevaluateNetwork() {
        final MyCaptivePortalImpl result = runCaptivePortalTest(c -> c.reevaluateNetwork());
        assertEquals(result.mCode, CaptivePortal.APP_REQUEST_REEVALUATION_REQUIRED);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testLogEvent() {
        /**
        * From S testLogEvent is expected to do nothing but shouldn't crash (the API
        * logEvent has been deprecated).
        */
        final MyCaptivePortalImpl result = runCaptivePortalTest(c -> c.logEvent(
                0,
                TEST_PACKAGE_NAME));
    }

    @IgnoreAfter(Build.VERSION_CODES.R)
    @Test
    public void testLogEvent_UntilR() {
        final MyCaptivePortalImpl result = runCaptivePortalTest(c -> c.logEvent(
                42, TEST_PACKAGE_NAME));
        assertEquals(result.mCode, 42);
        assertEquals(result.mPackageName, TEST_PACKAGE_NAME);
    }
}
