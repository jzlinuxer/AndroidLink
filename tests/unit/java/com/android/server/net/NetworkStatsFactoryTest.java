/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server.net;

import static android.net.NetworkStats.DEFAULT_NETWORK_ALL;
import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.METERED_ALL;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.METERED_YES;
import static android.net.NetworkStats.ROAMING_ALL;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.SET_ALL;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.SET_FOREGROUND;
import static android.net.NetworkStats.TAG_ALL;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStats.UID_ALL;

import static com.android.server.net.NetworkStatsFactory.CONFIG_PER_UID_TAG_THROTTLING;
import static com.android.server.net.NetworkStatsFactory.CONFIG_PER_UID_TAG_THROTTLING_THRESHOLD;
import static com.android.server.net.NetworkStatsFactory.kernelToTag;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.net.NetworkStats;
import android.net.TrafficStats;
import android.net.UnderlyingNetworkInfo;
import android.os.SystemClock;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.frameworks.tests.net.R;
import com.android.internal.util.ProcFileReader;
import com.android.server.BpfNetMaps;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.com.android.testutils.SetFeatureFlagsRule;
import com.android.testutils.com.android.testutils.SetFeatureFlagsRule.FeatureFlag;

import libcore.io.IoUtils;
import libcore.testing.io.TestIoUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.HashMap;

/** Tests for {@link NetworkStatsFactory}. */
@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class NetworkStatsFactoryTest extends NetworkStatsBaseTest {
    private static final String CLAT_PREFIX = "v4-";
    private static final int TEST_TAGS_PER_UID_THRESHOLD = 10;

    private File mTestProc;
    private NetworkStatsFactory mFactory;
    @Mock private Context mContext;
    @Mock private NetworkStatsFactory.Dependencies mDeps;
    @Mock private BpfNetMaps mBpfNetMaps;

    final HashMap<String, Boolean> mFeatureFlags = new HashMap<>();
    // This will set feature flags from @FeatureFlag annotations
    // into the map before setUp() runs.
    @Rule
    public final SetFeatureFlagsRule mSetFeatureFlagsRule =
            new SetFeatureFlagsRule((name, enabled) -> {
                mFeatureFlags.put(name, enabled);
                return null;
            }, (name) -> mFeatureFlags.getOrDefault(name, false));

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestProc = TestIoUtils.createTemporaryDirectory("proc");

        // The libandroid_servers which have the native method is not available to
        // applications. So in order to have a test support native library, the native code
        // related to networkStatsFactory is compiled to a minimal native library and loaded here.
        System.loadLibrary("networkstatsfactorytestjni");
        doReturn(mBpfNetMaps).when(mDeps).createBpfNetMaps(any());
        doAnswer(invocation -> mFeatureFlags.getOrDefault((String) invocation.getArgument(1), true))
            .when(mDeps).isFeatureNotChickenedOut(any(), anyString());
        doReturn(TEST_TAGS_PER_UID_THRESHOLD).when(mDeps)
                .getDeviceConfigPropertyInt(eq(CONFIG_PER_UID_TAG_THROTTLING_THRESHOLD), anyInt());

        mFactory = new NetworkStatsFactory(mContext, mDeps);
        mFactory.updateUnderlyingNetworkInfos(new UnderlyingNetworkInfo[0]);
    }

    @After
    public void tearDown() throws Exception {
        mFactory = null;
    }

    @Test
    public void testNetworkStatsDetail() throws Exception {
        final NetworkStats stats = factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_typical);

        assertEquals(70, stats.size());
        assertStatsEntry(stats, "wlan0", 0, SET_DEFAULT, 0x0, 18621L, 2898L);
        assertStatsEntry(stats, "wlan0", 10011, SET_DEFAULT, 0x0, 35777L, 5718L);
        assertStatsEntry(stats, "wlan0", 10021, SET_DEFAULT, 0x7fffff01, 562386L, 49228L);
        assertStatsEntry(stats, "rmnet1", 10021, SET_DEFAULT, 0x30100000, 219110L, 227423L);
        assertStatsEntry(stats, "rmnet2", 10001, SET_DEFAULT, 0x0, 1125899906842624L, 984L);
    }

    @Test
    public void testVpnRewriteTrafficThroughItself() throws Exception {
        UnderlyingNetworkInfo[] underlyingNetworkInfos =
                new UnderlyingNetworkInfo[] {createVpnInfo(new String[] {TEST_IFACE})};
        mFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);

        // create some traffic (assume 10 bytes of MTU for VPN interface and 1 byte encryption
        // overhead per packet):
        //
        // 1000 bytes (100 packets) were sent, and 2000 bytes (200 packets) were received by UID_RED
        // over VPN.
        // 500 bytes (50 packets) were sent, and 1000 bytes (100 packets) were received by UID_BLUE
        // over VPN.
        //
        // VPN UID rewrites packets read from TUN back to TUN, plus some of its own traffic
        final NetworkStats tunStats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_vpn_rewrite_through_self);

        assertValues(tunStats, TUN_IFACE, UID_RED, SET_ALL, TAG_NONE, METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, 2000L, 200L, 1000L, 100L, 0);
        assertValues(tunStats, TUN_IFACE, UID_BLUE, SET_ALL, TAG_NONE, METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, 1000L, 100L, 500L, 50L, 0);
        assertValues(tunStats, TUN_IFACE, UID_VPN, SET_ALL, TAG_NONE, METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, 0L, 0L, 1600L, 160L, 0);

        assertValues(tunStats, TEST_IFACE, UID_RED, 2000L, 200L, 1000L, 100L);
        assertValues(tunStats, TEST_IFACE, UID_BLUE, 1000L, 100L, 500L, 50L);
        assertValues(tunStats, TEST_IFACE, UID_VPN, 300L, 0L, 260L, 26L);
    }

    @Test
    public void testVpnWithClat() throws Exception {
        final UnderlyingNetworkInfo[] underlyingNetworkInfos = new UnderlyingNetworkInfo[] {
                createVpnInfo(new String[] {CLAT_PREFIX + TEST_IFACE})};
        mFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);
        mFactory.noteStackedIface(CLAT_PREFIX + TEST_IFACE, TEST_IFACE);

        // create some traffic (assume 10 bytes of MTU for VPN interface and 1 byte encryption
        // overhead per packet):
        // 1000 bytes (100 packets) were sent, and 2000 bytes (200 packets) were received by UID_RED
        // over VPN.
        // 500 bytes (50 packets) were sent, and 1000 bytes (100 packets) were received by UID_BLUE
        // over VPN.
        // VPN sent 1650 bytes (150 packets), and received 3300 (300 packets) over v4-WiFi, and clat
        // added 20 bytes per packet of extra overhead
        //
        // For 1650 bytes sent over v4-WiFi, 4650 bytes were actually sent over WiFi, which is
        // expected to be split as follows:
        // UID_RED: 1000 bytes, 100 packets
        // UID_BLUE: 500 bytes, 50 packets
        // UID_VPN: 3150 bytes, 0 packets
        //
        // For 3300 bytes received over v4-WiFi, 9300 bytes were actually sent over WiFi, which is
        // expected to be split as follows:
        // UID_RED: 2000 bytes, 200 packets
        // UID_BLUE: 1000 bytes, 100 packets
        // UID_VPN: 6300 bytes, 0 packets
        final NetworkStats tunStats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_vpn_with_clat);

        assertValues(tunStats, CLAT_PREFIX + TEST_IFACE, UID_RED, 2000L, 200L, 1000, 100L);
        assertValues(tunStats, CLAT_PREFIX + TEST_IFACE, UID_BLUE, 1000L, 100L, 500L, 50L);
        assertValues(tunStats, CLAT_PREFIX + TEST_IFACE, UID_VPN, 6300L, 0L, 3150L, 0L);
    }

    @Test
    public void testVpnWithOneUnderlyingIface() throws Exception {
        final UnderlyingNetworkInfo[] underlyingNetworkInfos =
                new UnderlyingNetworkInfo[] {createVpnInfo(new String[] {TEST_IFACE})};
        mFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);

        // create some traffic (assume 10 bytes of MTU for VPN interface and 1 byte encryption
        // overhead per packet):
        // 1000 bytes (100 packets) were sent, and 2000 bytes (200 packets) were received by UID_RED
        // over VPN.
        // 500 bytes (50 packets) were sent, and 1000 bytes (100 packets) were received by UID_BLUE
        // over VPN.
        // VPN sent 1650 bytes (150 packets), and received 3300 (300 packets) over WiFi.
        // Of 1650 bytes sent over WiFi, expect 1000 bytes attributed to UID_RED, 500 bytes
        // attributed to UID_BLUE, and 150 bytes attributed to UID_VPN.
        // Of 3300 bytes received over WiFi, expect 2000 bytes attributed to UID_RED, 1000 bytes
        // attributed to UID_BLUE, and 300 bytes attributed to UID_VPN.
        final NetworkStats tunStats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_vpn_one_underlying);

        assertValues(tunStats, TEST_IFACE, UID_RED, 2000L, 200L, 1000L, 100L);
        assertValues(tunStats, TEST_IFACE, UID_BLUE, 1000L, 100L, 500L, 50L);
        assertValues(tunStats, TEST_IFACE, UID_VPN, 300L, 0L, 150L, 0L);
    }

    @Test
    public void testVpnWithOneUnderlyingIfaceAndOwnTraffic() throws Exception {
        // WiFi network is connected and VPN is using WiFi (which has TEST_IFACE).
        final UnderlyingNetworkInfo[] underlyingNetworkInfos =
                new UnderlyingNetworkInfo[] {createVpnInfo(new String[] {TEST_IFACE})};
        mFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);

        // create some traffic (assume 10 bytes of MTU for VPN interface and 1 byte encryption
        // overhead per packet):
        // 1000 bytes (100 packets) were sent, and 2000 bytes (200 packets) were received by UID_RED
        // over VPN.
        // 500 bytes (50 packets) were sent, and 1000 bytes (100 packets) were received by UID_BLUE
        // over VPN.
        // Additionally, the VPN sends 6000 bytes (600 packets) of its own traffic into the tun
        // interface (passing that traffic to the VPN endpoint), and receives 5000 bytes (500
        // packets) from it. Including overhead that is 6600/5500 bytes.
        // VPN sent 8250 bytes (750 packets), and received 8800 (800 packets) over WiFi.
        // Of 8250 bytes sent over WiFi, expect 1000 bytes attributed to UID_RED, 500 bytes
        // attributed to UID_BLUE, and 6750 bytes attributed to UID_VPN.
        // Of 8800 bytes received over WiFi, expect 2000 bytes attributed to UID_RED, 1000 bytes
        // attributed to UID_BLUE, and 5800 bytes attributed to UID_VPN.
        final NetworkStats tunStats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_vpn_one_underlying_own_traffic);

        assertValues(tunStats, TEST_IFACE, UID_RED, 2000L, 200L, 1000L, 100L);
        assertValues(tunStats, TEST_IFACE, UID_BLUE, 1000L, 100L, 500L, 50L);
        assertValues(tunStats, TEST_IFACE, UID_VPN, 5800L, 500L, 6750L, 600L);
    }

    @Test
    public void testVpnWithOneUnderlyingIface_withCompression() throws Exception {
        // WiFi network is connected and VPN is using WiFi (which has TEST_IFACE).
        final UnderlyingNetworkInfo[] underlyingNetworkInfos =
                new UnderlyingNetworkInfo[] {createVpnInfo(new String[] {TEST_IFACE})};
        mFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);

        // create some traffic (assume 10 bytes of MTU for VPN interface and 1 byte encryption
        // overhead per packet):
        // 1000 bytes (100 packets) were sent/received by UID_RED over VPN.
        // 3000 bytes (300 packets) were sent/received by UID_BLUE over VPN.
        // VPN sent/received 1000 bytes (100 packets) over WiFi.
        // Of 1000 bytes over WiFi, expect 250 bytes attributed UID_RED and 750 bytes to UID_BLUE,
        // with nothing attributed to UID_VPN for both rx/tx traffic.
        final NetworkStats tunStats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_vpn_one_underlying_compression);

        assertValues(tunStats, TEST_IFACE, UID_RED, 250L, 25L, 250L, 25L);
        assertValues(tunStats, TEST_IFACE, UID_BLUE, 750L, 75L, 750L, 75L);
        assertValues(tunStats, TEST_IFACE, UID_VPN, 0L, 0L, 0L, 0L);
    }

    @Test
    public void testVpnWithTwoUnderlyingIfaces_packetDuplication() throws Exception {
        // WiFi and Cell networks are connected and VPN is using WiFi (which has TEST_IFACE) and
        // Cell (which has TEST_IFACE2) and has declared both of them in its underlying network set.
        // Additionally, VPN is duplicating traffic across both WiFi and Cell.
        final UnderlyingNetworkInfo[] underlyingNetworkInfos =
                new UnderlyingNetworkInfo[] {createVpnInfo(new String[] {TEST_IFACE, TEST_IFACE2})};
        mFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);

        // create some traffic (assume 10 bytes of MTU for VPN interface and 1 byte encryption
        // overhead per packet):
        // 1000 bytes (100 packets) were sent/received by UID_RED and UID_BLUE over VPN.
        // VPN sent/received 4400 bytes (400 packets) over both WiFi and Cell (8800 bytes in total).
        // Of 8800 bytes over WiFi/Cell, expect:
        // - 500 bytes rx/tx each over WiFi/Cell attributed to both UID_RED and UID_BLUE.
        // - 1200 bytes rx/tx each over WiFi/Cell for VPN_UID.
        final NetworkStats tunStats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_vpn_two_underlying_duplication);

        assertValues(tunStats, TEST_IFACE, UID_RED, 500L, 50L, 500L, 50L);
        assertValues(tunStats, TEST_IFACE, UID_BLUE, 500L, 50L, 500L, 50L);
        assertValues(tunStats, TEST_IFACE, UID_VPN, 1200L, 100L, 1200L, 100L);
        assertValues(tunStats, TEST_IFACE2, UID_RED, 500L, 50L, 500L, 50L);
        assertValues(tunStats, TEST_IFACE2, UID_BLUE, 500L, 50L, 500L, 50L);
        assertValues(tunStats, TEST_IFACE2, UID_VPN, 1200L, 100L, 1200L, 100L);
    }

    @Test
    public void testConcurrentVpns() throws Exception {
        // Assume two VPNs are connected on two different network interfaces. VPN1 is using
        // TEST_IFACE and VPN2 is using TEST_IFACE2.
        final UnderlyingNetworkInfo[] underlyingNetworkInfos = new UnderlyingNetworkInfo[] {
                createVpnInfo(TUN_IFACE, new String[] {TEST_IFACE}),
                createVpnInfo(TUN_IFACE2, new String[] {TEST_IFACE2})};
        mFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);

        // create some traffic (assume 10 bytes of MTU for VPN interface and 1 byte encryption
        // overhead per packet):
        // 1000 bytes (100 packets) were sent, and 2000 bytes (200 packets) were received by UID_RED
        // over VPN1.
        // 700 bytes (70 packets) were sent, and 3000 bytes (300 packets) were received by UID_RED
        // over VPN2.
        // 500 bytes (50 packets) were sent, and 1000 bytes (100 packets) were received by UID_BLUE
        // over VPN1.
        // 250 bytes (25 packets) were sent, and 500 bytes (50 packets) were received by UID_BLUE
        // over VPN2.
        // VPN1 sent 1650 bytes (150 packets), and received 3300 (300 packets) over TEST_IFACE.
        // Of 1650 bytes sent over WiFi, expect 1000 bytes attributed to UID_RED, 500 bytes
        // attributed to UID_BLUE, and 150 bytes attributed to UID_VPN.
        // Of 3300 bytes received over WiFi, expect 2000 bytes attributed to UID_RED, 1000 bytes
        // attributed to UID_BLUE, and 300 bytes attributed to UID_VPN.
        // VPN2 sent 1045 bytes (95 packets), and received 3850 (350 packets) over TEST_IFACE2.
        // Of 1045 bytes sent over Cell, expect 700 bytes attributed to UID_RED, 250 bytes
        // attributed to UID_BLUE, and 95 bytes attributed to UID_VPN.
        // Of 3850 bytes received over Cell, expect 3000 bytes attributed to UID_RED, 500 bytes
        // attributed to UID_BLUE, and 350 bytes attributed to UID_VPN.
        final NetworkStats tunStats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_vpn_one_underlying_two_vpn);

        assertValues(tunStats, TEST_IFACE, UID_RED, 2000L, 200L, 1000L, 100L);
        assertValues(tunStats, TEST_IFACE, UID_BLUE, 1000L, 100L, 500L, 50L);
        assertValues(tunStats, TEST_IFACE2, UID_RED, 3000L, 300L, 700L, 70L);
        assertValues(tunStats, TEST_IFACE2, UID_BLUE, 500L, 50L, 250L, 25L);
        assertValues(tunStats, TEST_IFACE, UID_VPN, 300L, 0L, 150L, 0L);
        assertValues(tunStats, TEST_IFACE2, UID_VPN, 350L, 0L, 95L, 0L);
    }

    @Test
    public void testVpnWithTwoUnderlyingIfaces_splitTraffic() throws Exception {
        // WiFi and Cell networks are connected and VPN is using WiFi (which has TEST_IFACE) and
        // Cell (which has TEST_IFACE2) and has declared both of them in its underlying network set.
        // Additionally, VPN is arbitrarily splitting traffic across WiFi and Cell.
        final UnderlyingNetworkInfo[] underlyingNetworkInfos =
                new UnderlyingNetworkInfo[] {createVpnInfo(new String[] {TEST_IFACE, TEST_IFACE2})};
        mFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);

        // create some traffic (assume 10 bytes of MTU for VPN interface and 1 byte encryption
        // overhead per packet):
        // 1000 bytes (100 packets) were sent, and 500 bytes (50 packets) received by UID_RED over
        // VPN.
        // VPN sent 660 bytes (60 packets) over WiFi and 440 bytes (40 packets) over Cell.
        // And, it received 330 bytes (30 packets) over WiFi and 220 bytes (20 packets) over Cell.
        // For UID_RED, expect 600 bytes attributed over WiFi and 400 bytes over Cell for sent (tx)
        // traffic. For received (rx) traffic, expect 300 bytes over WiFi and 200 bytes over Cell.
        //
        // For UID_VPN, expect 60 bytes attributed over WiFi and 40 bytes over Cell for tx traffic.
        // And, 30 bytes over WiFi and 20 bytes over Cell for rx traffic.
        final NetworkStats tunStats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_vpn_two_underlying_split);

        assertValues(tunStats, TEST_IFACE, UID_RED, 300L, 30L, 600L, 60L);
        assertValues(tunStats, TEST_IFACE, UID_VPN, 30L, 0L, 60L, 0L);
        assertValues(tunStats, TEST_IFACE2, UID_RED, 200L, 20L, 400L, 40L);
        assertValues(tunStats, TEST_IFACE2, UID_VPN, 20L, 0L, 40L, 0L);
    }

    @Test
    public void testVpnWithTwoUnderlyingIfaces_splitTrafficWithCompression() throws Exception {
        // WiFi and Cell networks are connected and VPN is using WiFi (which has TEST_IFACE) and
        // Cell (which has TEST_IFACE2) and has declared both of them in its underlying network set.
        // Additionally, VPN is arbitrarily splitting compressed traffic across WiFi and Cell.
        final UnderlyingNetworkInfo[] underlyingNetworkInfos =
                new UnderlyingNetworkInfo[] {createVpnInfo(new String[] {TEST_IFACE, TEST_IFACE2})};
        mFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);

        // create some traffic (assume 10 bytes of MTU for VPN interface:
        // 1000 bytes (100 packets) were sent/received by UID_RED over VPN.
        // VPN sent/received 600 bytes (60 packets) over WiFi and 200 bytes (20 packets) over Cell.
        // For UID_RED, expect 600 bytes attributed over WiFi and 200 bytes over Cell for both
        // rx/tx.
        // UID_VPN gets nothing attributed to it (avoiding negative stats).
        final NetworkStats tunStats =
                factoryReadNetworkStatsDetail(
                        R.raw.xt_qtaguid_vpn_two_underlying_split_compression);

        assertValues(tunStats, TEST_IFACE, UID_RED, 600L, 60L, 600L, 60L);
        assertValues(tunStats, TEST_IFACE, UID_VPN, 0L, 0L, 0L, 0L);
        assertValues(tunStats, TEST_IFACE2, UID_RED, 200L, 20L, 200L, 20L);
        assertValues(tunStats, TEST_IFACE2, UID_VPN, 0L, 0L, 0L, 0L);
    }

    @Test
    public void testVpnWithIncorrectUnderlyingIface() throws Exception {
        // WiFi and Cell networks are connected and VPN is using Cell (which has TEST_IFACE2),
        // but has declared only WiFi (TEST_IFACE) in its underlying network set.
        final UnderlyingNetworkInfo[] underlyingNetworkInfos =
                new UnderlyingNetworkInfo[] {createVpnInfo(new String[] {TEST_IFACE})};
        mFactory.updateUnderlyingNetworkInfos(underlyingNetworkInfos);

        // create some traffic (assume 10 bytes of MTU for VPN interface and 1 byte encryption
        // overhead per packet):
        // 1000 bytes (100 packets) were sent/received by UID_RED over VPN.
        // VPN sent/received 1100 bytes (100 packets) over Cell.
        // Of 1100 bytes over Cell, expect all of it attributed to UID_VPN for both rx/tx traffic.
        final NetworkStats tunStats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_vpn_incorrect_iface);

        assertValues(tunStats, TEST_IFACE, UID_RED, 0L, 0L, 0L, 0L);
        assertValues(tunStats, TEST_IFACE, UID_VPN, 0L, 0L, 0L, 0L);
        assertValues(tunStats, TEST_IFACE2, UID_RED, 0L, 0L, 0L, 0L);
        assertValues(tunStats, TEST_IFACE2, UID_VPN, 1100L, 100L, 1100L, 100L);
    }

    @Test
    public void testKernelTags() throws Exception {
        assertEquals(0, kernelToTag("0x0000000000000000"));
        assertEquals(0x32, kernelToTag("0x0000003200000000"));
        assertEquals(2147483647, kernelToTag("0x7fffffff00000000"));
        assertEquals(0, kernelToTag("0x0000000000000000"));
        assertEquals(2147483136, kernelToTag("0x7FFFFE0000000000"));

        assertEquals(0, kernelToTag("0x0"));
        assertEquals(0, kernelToTag("0xf00d"));
        assertEquals(1, kernelToTag("0x100000000"));
        assertEquals(14438007, kernelToTag("0xdc4e7700000000"));
        assertEquals(TrafficStats.TAG_SYSTEM_DOWNLOAD, kernelToTag("0xffffff0100000000"));
    }

    @Test
    public void testNetworkStatsWithSet() throws Exception {
        final NetworkStats stats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_typical);

        assertEquals(70, stats.size());
        assertStatsEntry(stats, "rmnet1", 10021, SET_DEFAULT, 0x30100000, 219110L, 578L, 227423L,
                676L);
        assertStatsEntry(stats, "rmnet1", 10021, SET_FOREGROUND, 0x30100000, 742L, 3L, 1265L, 3L);
    }

    @Test
    public void testDoubleClatAccountingSimple() throws Exception {
        mFactory.noteStackedIface("v4-wlan0", "wlan0");

        // xt_qtaguid_with_clat_simple is a synthetic file that simulates
        //  - 213 received 464xlat packets of size 200 bytes
        //  - 41 sent 464xlat packets of size 100 bytes
        //  - no other traffic on base interface for root uid.
        final NetworkStats stats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_with_clat_simple);
        assertEquals(3, stats.size());

        assertStatsEntry(stats, "v4-wlan0", 10060, SET_DEFAULT, 0x0, 46860L, 4920L);
        assertStatsEntry(stats, "wlan0", 0, SET_DEFAULT, 0x0, 0L, 0L);
    }

    @Test
    public void testDoubleClatAccounting() throws Exception {
        mFactory.noteStackedIface("v4-wlan0", "wlan0");

        final NetworkStats stats =
                factoryReadNetworkStatsDetail(R.raw.xt_qtaguid_with_clat);
        assertEquals(42, stats.size());

        assertStatsEntry(stats, "v4-wlan0", 0, SET_DEFAULT, 0x0, 356L, 276L);
        assertStatsEntry(stats, "v4-wlan0", 1000, SET_DEFAULT, 0x0, 30812L, 2310L);
        assertStatsEntry(stats, "v4-wlan0", 10102, SET_DEFAULT, 0x0, 10022L, 3330L);
        assertStatsEntry(stats, "v4-wlan0", 10060, SET_DEFAULT, 0x0, 9532772L, 254112L);
        assertStatsEntry(stats, "wlan0", 0, SET_DEFAULT, 0x0, 0L, 0L);
        assertStatsEntry(stats, "wlan0", 1000, SET_DEFAULT, 0x0, 6126L, 2013L);
        assertStatsEntry(stats, "wlan0", 10013, SET_DEFAULT, 0x0, 0L, 144L);
        assertStatsEntry(stats, "wlan0", 10018, SET_DEFAULT, 0x0, 5980263L, 167667L);
        assertStatsEntry(stats, "wlan0", 10060, SET_DEFAULT, 0x0, 134356L, 8705L);
        assertStatsEntry(stats, "wlan0", 10079, SET_DEFAULT, 0x0, 10926L, 1507L);
        assertStatsEntry(stats, "wlan0", 10102, SET_DEFAULT, 0x0, 25038L, 8245L);
        assertStatsEntry(stats, "wlan0", 10103, SET_DEFAULT, 0x0, 0L, 192L);
        assertStatsEntry(stats, "dummy0", 0, SET_DEFAULT, 0x0, 0L, 168L);
        assertStatsEntry(stats, "lo", 0, SET_DEFAULT, 0x0, 1288L, 1288L);

        assertNoStatsEntry(stats, "wlan0", 1029, SET_DEFAULT, 0x0);
    }

    @Test
    public void testRemoveUidsStats() throws Exception {
        final NetworkStats stats = new NetworkStats(SystemClock.elapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 16L, 1L, 16L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE,
                        256L, 16L, 512L, 32L, 0L)
                .insertEntry(TEST_IFACE, UID_GREEN, SET_DEFAULT, TAG_NONE, 64L, 3L, 1024L, 8L, 0L);

        doReturn(stats).when(mDeps).getNetworkStatsDetail();

        final String[] ifaces = new String[]{TEST_IFACE};
        final NetworkStats res = mFactory.readNetworkStatsDetail(UID_ALL, ifaces, TAG_ALL);

        // Verify that the result of the mocked stats are expected.
        assertValues(res, TEST_IFACE, UID_RED, 16L, 1L, 16L, 1L);
        assertValues(res, TEST_IFACE, UID_BLUE, 256L, 16L, 512L, 32L);
        assertValues(res, TEST_IFACE, UID_GREEN, 64L, 3L, 1024L, 8L);

        // Assume the apps were removed.
        final int[] removedUids = new int[]{UID_RED, UID_BLUE};
        mFactory.removeUidsLocked(removedUids);

        // Return empty stats for reading the result of removing uids stats later.
        doReturn(buildEmptyStats()).when(mDeps).getNetworkStatsDetail();

        final NetworkStats removedUidsStats =
                mFactory.readNetworkStatsDetail(UID_ALL, ifaces, TAG_ALL);

        // Verify that the stats of the removed uids were removed.
        assertValues(removedUidsStats, TEST_IFACE, UID_RED, 0L, 0L, 0L, 0L);
        assertValues(removedUidsStats, TEST_IFACE, UID_BLUE, 0L, 0L, 0L, 0L);
        assertValues(removedUidsStats, TEST_IFACE, UID_GREEN, 64L, 3L, 1024L, 8L);
    }

    @FeatureFlag(name = CONFIG_PER_UID_TAG_THROTTLING)
    @Test
    public void testFilterTooManyTags_featureEnabled() throws Exception {
        doTestFilterTooManyTags(true);
    }

    @FeatureFlag(name = CONFIG_PER_UID_TAG_THROTTLING, enabled = false)
    @Test
    public void testFilterTooManyTags_featureDisabled() throws Exception {
        doTestFilterTooManyTags(false);
    }

    private void doTestFilterTooManyTags(boolean supportPerUidTagThrottling) throws Exception {
        // Add entries for UID_RED which reaches the threshold.
        final NetworkStats statsWithManyTags = new NetworkStats(0L, TEST_TAGS_PER_UID_THRESHOLD);
        for (int tag = 1; tag <= TEST_TAGS_PER_UID_THRESHOLD; tag++) {
            statsWithManyTags.combineValues(
                    new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, tag,
                            METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO, 12L, 18L, 14L, 1L, 0L));
        }
        doReturn(statsWithManyTags).when(mDeps).getNetworkStatsDetail();
        final NetworkStats stats1 = mFactory.readNetworkStatsDetail();
        assertEquals(stats1.size(), TEST_TAGS_PER_UID_THRESHOLD);

        // Add 2 new entries with pre-existing tag, verify they can be added no matter what.
        final NetworkStats newDiffWithExistingTag = new NetworkStats(0L, 2);
        // This one should be added as a new entry, as the metered data doesn't exist yet.
        newDiffWithExistingTag.combineValues(
                new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT,
                        TEST_TAGS_PER_UID_THRESHOLD,
                        METERED_YES, ROAMING_NO, DEFAULT_NETWORK_NO, 3L, 5L, 8L, 1L, 1L));
        // This one should be combined into existing entry.
        newDiffWithExistingTag.combineValues(
                new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT,
                        TEST_TAGS_PER_UID_THRESHOLD,
                        METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO, 1L, 2L, 3L, 4L, 5L));

        doReturn(newDiffWithExistingTag).when(mDeps).getNetworkStatsDetail();
        final NetworkStats stats2 = mFactory.readNetworkStatsDetail();
        assertEquals(stats2.size(), TEST_TAGS_PER_UID_THRESHOLD + 1);
        assertValues(stats2, TEST_IFACE, UID_RED, SET_DEFAULT, TEST_TAGS_PER_UID_THRESHOLD,
                METERED_YES, ROAMING_NO, DEFAULT_NETWORK_NO, 3L, 5L, 8L, 1L, 1L);
        assertValues(stats2, TEST_IFACE, UID_RED, SET_DEFAULT, TEST_TAGS_PER_UID_THRESHOLD,
                METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO, 13L, 20L, 17L, 5L, 5L);

        // Add an entry which exceeds the threshold, verify the entry is filtered out.
        final NetworkStats newDiffWithNonExistingTag = new NetworkStats(0L, 1);
        newDiffWithNonExistingTag.combineValues(
                new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT,
                        TEST_TAGS_PER_UID_THRESHOLD + 1,
                        METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO, 12L, 18L, 14L, 1L, 0L));
        doReturn(newDiffWithNonExistingTag).when(mDeps).getNetworkStatsDetail();
        final NetworkStats stats3 = mFactory.readNetworkStatsDetail();
        if (supportPerUidTagThrottling) {
            assertEquals(stats3.size(), TEST_TAGS_PER_UID_THRESHOLD + 1);
            assertNoStatsEntry(stats3, TEST_IFACE, UID_RED, SET_DEFAULT,
                    TEST_TAGS_PER_UID_THRESHOLD + 1);
        } else {
            assertEquals(stats3.size(), TEST_TAGS_PER_UID_THRESHOLD + 2);
            assertValues(stats3, TEST_IFACE, UID_RED, SET_DEFAULT,
                    TEST_TAGS_PER_UID_THRESHOLD + 1,
                    METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO, 12L, 18L, 14L, 1L, 0L);
        }
    }

    private NetworkStats buildEmptyStats() {
        return new NetworkStats(SystemClock.elapsedRealtime(), 0);
    }

    private NetworkStats parseNetworkStatsFromGoldenSample(int resourceId, int initialSize,
            boolean consumeHeader, boolean checkActive, boolean isUidData) throws IOException {
        final NetworkStats stats =
                new NetworkStats(SystemClock.elapsedRealtime(), initialSize);
        final NetworkStats.Entry entry = new NetworkStats.Entry();
        ProcFileReader reader = null;
        int idx = 1;
        int lastIdx = 1;
        try {
            reader = new ProcFileReader(InstrumentationRegistry.getContext().getResources()
                            .openRawResource(resourceId));

            if (consumeHeader) {
                reader.finishLine();
            }

            while (reader.hasMoreData()) {
                if (isUidData) {
                    idx = reader.nextInt();
                    if (idx != lastIdx + 1) {
                        throw new ProtocolException(
                                "inconsistent idx=" + idx + " after lastIdx=" + lastIdx);
                    }
                    lastIdx = idx;
                }

                entry.iface = reader.nextString();
                // Read the uid based information from file. Otherwise, assign with target value.
                entry.tag = isUidData ? kernelToTag(reader.nextString()) : TAG_NONE;
                entry.uid = isUidData ? reader.nextInt() : UID_ALL;
                entry.set = isUidData ? reader.nextInt() : SET_ALL;

                // For fetching active numbers. Dev specific
                final boolean active = checkActive ? reader.nextInt() != 0 : false;

                // Always include snapshot values
                entry.rxBytes = reader.nextLong();
                entry.rxPackets = reader.nextLong();
                entry.txBytes = reader.nextLong();
                entry.txPackets = reader.nextLong();

                // Fold in active numbers, but only when active
                if (active) {
                    entry.rxBytes += reader.nextLong();
                    entry.rxPackets += reader.nextLong();
                    entry.txBytes += reader.nextLong();
                    entry.txPackets += reader.nextLong();
                }

                stats.insertEntry(entry);
                reader.finishLine();
            }
        } catch (NullPointerException | NumberFormatException e) {
            final String errMsg = isUidData
                    ? "problem parsing idx " + idx : "problem parsing stats";
            final ProtocolException pe = new ProtocolException(errMsg);
            pe.initCause(e);
            throw pe;
        } finally {
            IoUtils.closeQuietly(reader);
        }
        return stats;
    }

    private NetworkStats factoryReadNetworkStatsDetail(int resourceId) throws Exception {
        // Choose a general detail stats sample size from the experiences to prevent from
        // frequently allocating buckets.
        final NetworkStats statsFromResource = parseNetworkStatsFromGoldenSample(resourceId,
                24 /* initialSize */, true /* consumeHeader */, false /* checkActive */,
                true /* isUidData */);
        doReturn(statsFromResource).when(mDeps).getNetworkStatsDetail();
        return mFactory.readNetworkStatsDetail();
    }

    private static void assertStatsEntry(NetworkStats stats, String iface, int uid, int set,
            int tag, long rxBytes, long txBytes) {
        final int i = stats.findIndex(iface, uid, set, tag, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_NO);
        if (i < 0) {
            fail(String.format("no NetworkStats for (iface: %s, uid: %d, set: %d, tag: %d)",
                    iface, uid, set, tag));
        }
        final NetworkStats.Entry entry = stats.getValues(i, null);
        assertEquals("unexpected rxBytes", rxBytes, entry.rxBytes);
        assertEquals("unexpected txBytes", txBytes, entry.txBytes);
    }

    private static void assertNoStatsEntry(NetworkStats stats, String iface, int uid, int set,
            int tag) {
        final int i = stats.findIndex(iface, uid, set, tag, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_NO);
        if (i >= 0) {
            fail("unexpected NetworkStats entry at " + i);
        }
    }

    private static void assertStatsEntry(NetworkStats stats, String iface, int uid, int set,
            int tag, long rxBytes, long rxPackets, long txBytes, long txPackets) {
        assertStatsEntry(stats, iface, uid, set, tag, METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO,
                rxBytes, rxPackets, txBytes, txPackets);
    }

    private static void assertStatsEntry(NetworkStats stats, String iface, int uid, int set,
            int tag, int metered, int roaming, int defaultNetwork, long rxBytes, long rxPackets,
            long txBytes, long txPackets) {
        final int i = stats.findIndex(iface, uid, set, tag, metered, roaming, defaultNetwork);

        if (i < 0) {
            fail(String.format("no NetworkStats for (iface: %s, uid: %d, set: %d, tag: %d, metered:"
                    + " %d, roaming: %d, defaultNetwork: %d)",
                    iface, uid, set, tag, metered, roaming, defaultNetwork));
        }
        final NetworkStats.Entry entry = stats.getValues(i, null);
        assertEquals("unexpected rxBytes", rxBytes, entry.rxBytes);
        assertEquals("unexpected rxPackets", rxPackets, entry.rxPackets);
        assertEquals("unexpected txBytes", txBytes, entry.txBytes);
        assertEquals("unexpected txPackets", txPackets, entry.txPackets);
    }
}
