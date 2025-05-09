/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * bpf_existence_test.cpp - checks that the device has expected BPF programs and maps
 */

#include <cstdint>
#include <set>
#include <string>

#include <android-base/properties.h>
#include <android/api-level.h>
#include <bpf/BpfUtils.h>

#include <gtest/gtest.h>

using std::find;
using std::set;
using std::string;

using android::bpf::isAtLeastKernelVersion;
using android::bpf::isAtLeastR;
using android::bpf::isAtLeastS;
using android::bpf::isAtLeastT;
using android::bpf::isAtLeastU;
using android::bpf::isAtLeastV;
using android::bpf::isAtLeast25Q2;

#define PLATFORM "/sys/fs/bpf/"
#define TETHERING "/sys/fs/bpf/tethering/"
#define PRIVATE "/sys/fs/bpf/net_private/"
#define SHARED "/sys/fs/bpf/net_shared/"
#define NETD_RO "/sys/fs/bpf/netd_readonly/"
#define NETD "/sys/fs/bpf/netd_shared/"

class BpfExistenceTest : public ::testing::Test {
};

// Part of Android R platform (for 4.9+), but mainlined in S
static const set<string> PLATFORM_ONLY_IN_R = {
    PLATFORM "map_offload_tether_ingress_map",
    PLATFORM "map_offload_tether_limit_map",
    PLATFORM "map_offload_tether_stats_map",
    PLATFORM "prog_offload_schedcls_ingress_tether_ether",
    PLATFORM "prog_offload_schedcls_ingress_tether_rawip",
};

// Provided by *current* mainline module for S+ devices
static const set<string> MAINLINE_FOR_S_PLUS = {
    TETHERING "map_offload_tether_dev_map",
    TETHERING "map_offload_tether_downstream4_map",
    TETHERING "map_offload_tether_downstream64_map",
    TETHERING "map_offload_tether_downstream6_map",
    TETHERING "map_offload_tether_error_map",
    TETHERING "map_offload_tether_limit_map",
    TETHERING "map_offload_tether_stats_map",
    TETHERING "map_offload_tether_upstream4_map",
    TETHERING "map_offload_tether_upstream6_map",
    TETHERING "map_test_bitmap",
    TETHERING "map_test_tether_downstream6_map",
    TETHERING "map_test_tether2_downstream6_map",
    TETHERING "map_test_tether3_downstream6_map",
    TETHERING "prog_offload_schedcls_tether_downstream4_ether",
    TETHERING "prog_offload_schedcls_tether_downstream4_rawip",
    TETHERING "prog_offload_schedcls_tether_downstream6_ether",
    TETHERING "prog_offload_schedcls_tether_downstream6_rawip",
    TETHERING "prog_offload_schedcls_tether_upstream4_ether",
    TETHERING "prog_offload_schedcls_tether_upstream4_rawip",
    TETHERING "prog_offload_schedcls_tether_upstream6_ether",
    TETHERING "prog_offload_schedcls_tether_upstream6_rawip",
};

// Provided by *current* mainline module for T+ devices
static const set<string> MAINLINE_FOR_T_PLUS = {
    SHARED "map_clatd_clat_egress4_map",
    SHARED "map_clatd_clat_ingress6_map",
    SHARED "map_dscpPolicy_ipv4_dscp_policies_map",
    SHARED "map_dscpPolicy_ipv6_dscp_policies_map",
    SHARED "map_dscpPolicy_socket_policy_cache_map",
    NETD "map_netd_app_uid_stats_map",
    NETD "map_netd_blocked_ports_map",
    NETD "map_netd_configuration_map",
    NETD "map_netd_cookie_tag_map",
    NETD "map_netd_data_saver_enabled_map",
    NETD "map_netd_iface_index_name_map",
    NETD "map_netd_iface_stats_map",
    NETD "map_netd_ingress_discard_map",
    NETD "map_netd_stats_map_A",
    NETD "map_netd_stats_map_B",
    NETD "map_netd_uid_counterset_map",
    NETD "map_netd_uid_owner_map",
    NETD "map_netd_uid_permission_map",
    SHARED "prog_clatd_schedcls_egress4_clat_rawip",
    SHARED "prog_clatd_schedcls_ingress6_clat_ether",
    SHARED "prog_clatd_schedcls_ingress6_clat_rawip",
    NETD "prog_netd_cgroupskb_egress_stats",
    NETD "prog_netd_cgroupskb_ingress_stats",
    NETD "prog_netd_schedact_ingress_account",
    NETD "prog_netd_skfilter_allowlist_xtbpf",
    NETD "prog_netd_skfilter_denylist_xtbpf",
    NETD "prog_netd_skfilter_egress_xtbpf",
    NETD "prog_netd_skfilter_ingress_xtbpf",
};

// Provided by *current* mainline module for T+ devices with 4.14+ kernels
static const set<string> MAINLINE_FOR_T_4_14_PLUS = {
    NETD "prog_netd_cgroupsock_inet_create",
};

// Provided by *current* mainline module for T+ devices with 5.4+ kernels
static const set<string> MAINLINE_FOR_T_4_19_PLUS = {
    NETD "prog_netd_bind4_inet4_bind",
    NETD "prog_netd_bind6_inet6_bind",
};

// Provided by *current* mainline module for T+ devices with 5.10+ kernels
static const set<string> MAINLINE_FOR_T_5_10_PLUS = {
    NETD "prog_netd_cgroupsockrelease_inet_release",
};

// Provided by *current* mainline module for T+ devices with 5.15+ kernels
static const set<string> MAINLINE_FOR_T_5_15_PLUS = {
    SHARED "prog_dscpPolicy_schedcls_set_dscp_ether",
};

// Provided by *current* mainline module for U+ devices
static const set<string> MAINLINE_FOR_U_PLUS = {
    NETD "map_netd_packet_trace_enabled_map",
};

// Provided by *current* mainline module for U+ devices with 5.10+ kernels
static const set<string> MAINLINE_FOR_U_5_10_PLUS = {
    NETD "map_netd_packet_trace_ringbuf",
};

// Provided by *current* mainline module for V+ devices
static const set<string> MAINLINE_FOR_V_PLUS = {
    NETD "prog_netd_connect4_inet4_connect",
    NETD "prog_netd_connect6_inet6_connect",
    NETD "prog_netd_recvmsg4_udp4_recvmsg",
    NETD "prog_netd_recvmsg6_udp6_recvmsg",
    NETD "prog_netd_sendmsg4_udp4_sendmsg",
    NETD "prog_netd_sendmsg6_udp6_sendmsg",
};

// Provided by *current* mainline module for V+ devices with 5.4+ kernels
static const set<string> MAINLINE_FOR_V_5_4_PLUS = {
    NETD "prog_netd_getsockopt_prog",
    NETD "prog_netd_setsockopt_prog",
};

// Provided by *current* mainline module for 25Q2+ devices
static const set<string> MAINLINE_FOR_25Q2_PLUS = {
    NETD "map_netd_local_net_access_map",
    NETD "map_netd_local_net_blocked_uid_map",
};

static void addAll(set<string>& a, const set<string>& b) {
    a.insert(b.begin(), b.end());
}

#define DO_EXPECT(B, V) addAll((B) ? mustExist : mustNotExist, (V))

TEST_F(BpfExistenceTest, TestPrograms) {
    // Only unconfined root is guaranteed to be able to access everything in /sys/fs/bpf.
    ASSERT_EQ(0, getuid()) << "This test must run as root.";

    set<string> mustExist;
    set<string> mustNotExist;

    // We do not actually check the platform P/Q (netd) and Q (clatd) things
    // and only verify the mainline module relevant R+ offload maps & progs.
    //
    // The goal of this test is to verify compatibility with the tethering mainline module,
    // and not to test the platform itself, which may have been modified by vendor or oems,
    // so we should only test for the removal of stuff that was mainline'd,
    // and for the presence of mainline stuff.

    // Note: Q is no longer supported by mainline
    ASSERT_TRUE(isAtLeastR);

    // R can potentially run on pre-4.9 kernel non-eBPF capable devices.
    DO_EXPECT(isAtLeastR && !isAtLeastS && isAtLeastKernelVersion(4, 9, 0), PLATFORM_ONLY_IN_R);

    // S requires Linux Kernel 4.9+ and thus requires eBPF support.
    if (isAtLeastS) ASSERT_TRUE(isAtLeastKernelVersion(4, 9, 0));

    // on S without a new enough DnsResolver apex, NetBpfLoad doesn't get triggered,
    // and thus no mainline programs get loaded.
    bool mainlineBpfCapableResolve = !access("/apex/com.android.resolv/NetBpfLoad-S.flag", F_OK);
    bool mainlineNetBpfLoad = isAtLeastT || mainlineBpfCapableResolve;
    DO_EXPECT(isAtLeastS && mainlineNetBpfLoad, MAINLINE_FOR_S_PLUS);

    // Nothing added or removed in SCv2.

    // T still only requires Linux Kernel 4.9+.
    DO_EXPECT(isAtLeastT, MAINLINE_FOR_T_PLUS);
    DO_EXPECT(isAtLeastT && isAtLeastKernelVersion(4, 14, 0), MAINLINE_FOR_T_4_14_PLUS);
    DO_EXPECT(isAtLeastT && isAtLeastKernelVersion(4, 19, 0), MAINLINE_FOR_T_4_19_PLUS);
    DO_EXPECT(isAtLeastT && isAtLeastKernelVersion(5, 10, 0), MAINLINE_FOR_T_5_10_PLUS);
    DO_EXPECT(isAtLeastT && isAtLeastKernelVersion(5, 15, 0), MAINLINE_FOR_T_5_15_PLUS);

    // U requires Linux Kernel 4.14+, but nothing (as yet) added or removed in U.
    if (isAtLeastU) ASSERT_TRUE(isAtLeastKernelVersion(4, 14, 0));
    DO_EXPECT(isAtLeastU, MAINLINE_FOR_U_PLUS);
    DO_EXPECT(isAtLeastU && isAtLeastKernelVersion(5, 10, 0), MAINLINE_FOR_U_5_10_PLUS);

    // V requires Linux Kernel 4.19+, but nothing (as yet) added or removed in V.
    if (isAtLeastV) ASSERT_TRUE(isAtLeastKernelVersion(4, 19, 0));
    DO_EXPECT(isAtLeastV, MAINLINE_FOR_V_PLUS);
    DO_EXPECT(isAtLeastV && isAtLeastKernelVersion(5, 4, 0), MAINLINE_FOR_V_5_4_PLUS);

    if (isAtLeast25Q2) ASSERT_TRUE(isAtLeastKernelVersion(5, 4, 0));
    DO_EXPECT(isAtLeast25Q2, MAINLINE_FOR_25Q2_PLUS);

    for (const auto& file : mustExist) {
        EXPECT_EQ(0, access(file.c_str(), R_OK)) << file << " does not exist";
    }
    for (const auto& file : mustNotExist) {
        int ret = access(file.c_str(), R_OK);
        int err = errno;
        EXPECT_EQ(-1, ret) << file << " unexpectedly exists";
        if (ret == -1) {
            EXPECT_EQ(ENOENT, err) << " accessing " << file << " failed with errno " << err;
        }
    }
}
