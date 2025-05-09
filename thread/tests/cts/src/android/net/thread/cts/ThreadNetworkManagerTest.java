/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net.thread.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.thread.ThreadNetworkManager;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ThreadNetworkManager}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ThreadNetworkManagerTest {
    private static final String THREAD_NETWORK_FEATURE = "android.hardware.thread_network";

    @Rule public DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final PackageManager mPackageManager = mContext.getPackageManager();

    private ThreadNetworkManager mManager;

    @Before
    public void setUp() {
        mManager = mContext.getSystemService(ThreadNetworkManager.class);
    }

    @Test
    @IgnoreAfter(Build.VERSION_CODES.TIRAMISU)
    public void getManager_onTOrLower_returnsNull() {
        assertThat(mManager).isNull();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void getManager_hasThreadFeatureOnVOrHigher_returnsNonNull() {
        assumeTrue(mPackageManager.hasSystemFeature(THREAD_NETWORK_FEATURE));

        assertThat(mManager).isNotNull();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void getManager_onUButNotTv_returnsNull() {
        assumeFalse(mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK));

        assertThat(mManager).isNull();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void getManager_onUAndTvWithThreadFeature_returnsNonNull() {
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK));
        assumeTrue(mPackageManager.hasSystemFeature(THREAD_NETWORK_FEATURE));

        assertThat(mManager).isNotNull();
    }
}
