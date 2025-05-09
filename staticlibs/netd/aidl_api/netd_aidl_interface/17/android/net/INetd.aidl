/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package android.net;
/* @hide */
interface INetd {
  boolean isAlive();
  /**
   * @deprecated unimplemented on T+.
   */
  boolean firewallReplaceUidChain(in @utf8InCpp String chainName, boolean isAllowlist, in int[] uids);
  boolean bandwidthEnableDataSaver(boolean enable);
  /**
   * @deprecated use networkCreate() instead.
   */
  void networkCreatePhysical(int netId, int permission);
  /**
   * @deprecated use networkCreate() instead.
   */
  void networkCreateVpn(int netId, boolean secure);
  void networkDestroy(int netId);
  void networkAddInterface(int netId, in @utf8InCpp String iface);
  void networkRemoveInterface(int netId, in @utf8InCpp String iface);
  void networkAddUidRanges(int netId, in android.net.UidRangeParcel[] uidRanges);
  void networkRemoveUidRanges(int netId, in android.net.UidRangeParcel[] uidRanges);
  void networkRejectNonSecureVpn(boolean add, in android.net.UidRangeParcel[] uidRanges);
  void socketDestroy(in android.net.UidRangeParcel[] uidRanges, in int[] exemptUids);
  boolean tetherApplyDnsInterfaces();
  android.net.TetherStatsParcel[] tetherGetStats();
  void interfaceAddAddress(in @utf8InCpp String ifName, in @utf8InCpp String addrString, int prefixLength);
  void interfaceDelAddress(in @utf8InCpp String ifName, in @utf8InCpp String addrString, int prefixLength);
  @utf8InCpp String getProcSysNet(int ipversion, int which, in @utf8InCpp String ifname, in @utf8InCpp String parameter);
  void setProcSysNet(int ipversion, int which, in @utf8InCpp String ifname, in @utf8InCpp String parameter, in @utf8InCpp String value);
  void ipSecSetEncapSocketOwner(in ParcelFileDescriptor socket, int newUid);
  int ipSecAllocateSpi(int transformId, in @utf8InCpp String sourceAddress, in @utf8InCpp String destinationAddress, int spi);
  void ipSecAddSecurityAssociation(int transformId, int mode, in @utf8InCpp String sourceAddress, in @utf8InCpp String destinationAddress, int underlyingNetId, int spi, int markValue, int markMask, in @utf8InCpp String authAlgo, in byte[] authKey, in int authTruncBits, in @utf8InCpp String cryptAlgo, in byte[] cryptKey, in int cryptTruncBits, in @utf8InCpp String aeadAlgo, in byte[] aeadKey, in int aeadIcvBits, int encapType, int encapLocalPort, int encapRemotePort, int interfaceId);
  void ipSecDeleteSecurityAssociation(int transformId, in @utf8InCpp String sourceAddress, in @utf8InCpp String destinationAddress, int spi, int markValue, int markMask, int interfaceId);
  void ipSecApplyTransportModeTransform(in ParcelFileDescriptor socket, int transformId, int direction, in @utf8InCpp String sourceAddress, in @utf8InCpp String destinationAddress, int spi);
  void ipSecRemoveTransportModeTransform(in ParcelFileDescriptor socket);
  void ipSecAddSecurityPolicy(int transformId, int selAddrFamily, int direction, in @utf8InCpp String tmplSrcAddress, in @utf8InCpp String tmplDstAddress, int spi, int markValue, int markMask, int interfaceId);
  void ipSecUpdateSecurityPolicy(int transformId, int selAddrFamily, int direction, in @utf8InCpp String tmplSrcAddress, in @utf8InCpp String tmplDstAddress, int spi, int markValue, int markMask, int interfaceId);
  void ipSecDeleteSecurityPolicy(int transformId, int selAddrFamily, int direction, int markValue, int markMask, int interfaceId);
  void ipSecAddTunnelInterface(in @utf8InCpp String deviceName, in @utf8InCpp String localAddress, in @utf8InCpp String remoteAddress, int iKey, int oKey, int interfaceId);
  void ipSecUpdateTunnelInterface(in @utf8InCpp String deviceName, in @utf8InCpp String localAddress, in @utf8InCpp String remoteAddress, int iKey, int oKey, int interfaceId);
  void ipSecRemoveTunnelInterface(in @utf8InCpp String deviceName);
  void wakeupAddInterface(in @utf8InCpp String ifName, in @utf8InCpp String prefix, int mark, int mask);
  void wakeupDelInterface(in @utf8InCpp String ifName, in @utf8InCpp String prefix, int mark, int mask);
  void setIPv6AddrGenMode(in @utf8InCpp String ifName, int mode);
  void idletimerAddInterface(in @utf8InCpp String ifName, int timeout, in @utf8InCpp String classLabel);
  void idletimerRemoveInterface(in @utf8InCpp String ifName, int timeout, in @utf8InCpp String classLabel);
  void strictUidCleartextPenalty(int uid, int policyPenalty);
  /**
   * @deprecated This method has no effect and throws UnsupportedOperationException. The clatd control plane moved to the mainline module starting in T. See ClatCoordinator.
   */
  @utf8InCpp String clatdStart(in @utf8InCpp String ifName, in @utf8InCpp String nat64Prefix);
  /**
   * @deprecated This method has no effect and throws UnsupportedOperationException. The clatd control plane moved to the mainline module starting in T. See ClatCoordinator.
   */
  void clatdStop(in @utf8InCpp String ifName);
  boolean ipfwdEnabled();
  @utf8InCpp String[] ipfwdGetRequesterList();
  void ipfwdEnableForwarding(in @utf8InCpp String requester);
  void ipfwdDisableForwarding(in @utf8InCpp String requester);
  void ipfwdAddInterfaceForward(in @utf8InCpp String fromIface, in @utf8InCpp String toIface);
  void ipfwdRemoveInterfaceForward(in @utf8InCpp String fromIface, in @utf8InCpp String toIface);
  void bandwidthSetInterfaceQuota(in @utf8InCpp String ifName, long bytes);
  void bandwidthRemoveInterfaceQuota(in @utf8InCpp String ifName);
  void bandwidthSetInterfaceAlert(in @utf8InCpp String ifName, long bytes);
  void bandwidthRemoveInterfaceAlert(in @utf8InCpp String ifName);
  void bandwidthSetGlobalAlert(long bytes);
  /**
   * @deprecated unimplemented on T+.
   */
  void bandwidthAddNaughtyApp(int uid);
  /**
   * @deprecated unimplemented on T+.
   */
  void bandwidthRemoveNaughtyApp(int uid);
  /**
   * @deprecated unimplemented on T+.
   */
  void bandwidthAddNiceApp(int uid);
  /**
   * @deprecated unimplemented on T+.
   */
  void bandwidthRemoveNiceApp(int uid);
  void tetherStart(in @utf8InCpp String[] dhcpRanges);
  void tetherStop();
  boolean tetherIsEnabled();
  void tetherInterfaceAdd(in @utf8InCpp String ifName);
  void tetherInterfaceRemove(in @utf8InCpp String ifName);
  @utf8InCpp String[] tetherInterfaceList();
  void tetherDnsSet(int netId, in @utf8InCpp String[] dnsAddrs);
  @utf8InCpp String[] tetherDnsList();
  void networkAddRoute(int netId, in @utf8InCpp String ifName, in @utf8InCpp String destination, in @utf8InCpp String nextHop);
  void networkRemoveRoute(int netId, in @utf8InCpp String ifName, in @utf8InCpp String destination, in @utf8InCpp String nextHop);
  void networkAddLegacyRoute(int netId, in @utf8InCpp String ifName, in @utf8InCpp String destination, in @utf8InCpp String nextHop, int uid);
  void networkRemoveLegacyRoute(int netId, in @utf8InCpp String ifName, in @utf8InCpp String destination, in @utf8InCpp String nextHop, int uid);
  int networkGetDefault();
  void networkSetDefault(int netId);
  void networkClearDefault();
  void networkSetPermissionForNetwork(int netId, int permission);
  void networkSetPermissionForUser(int permission, in int[] uids);
  void networkClearPermissionForUser(in int[] uids);
  /**
   * @deprecated unimplemented on T+.
   */
  void trafficSetNetPermForUids(int permission, in int[] uids);
  void networkSetProtectAllow(int uid);
  void networkSetProtectDeny(int uid);
  boolean networkCanProtect(int uid);
  void firewallSetFirewallType(int firewalltype);
  void firewallSetInterfaceRule(in @utf8InCpp String ifName, int firewallRule);
  /**
   * @deprecated unimplemented on T+.
   */
  void firewallSetUidRule(int childChain, int uid, int firewallRule);
  /**
   * @deprecated unimplemented on T+.
   */
  void firewallEnableChildChain(int childChain, boolean enable);
  @utf8InCpp String[] interfaceGetList();
  android.net.InterfaceConfigurationParcel interfaceGetCfg(in @utf8InCpp String ifName);
  void interfaceSetCfg(in android.net.InterfaceConfigurationParcel cfg);
  void interfaceSetIPv6PrivacyExtensions(in @utf8InCpp String ifName, boolean enable);
  void interfaceClearAddrs(in @utf8InCpp String ifName);
  void interfaceSetEnableIPv6(in @utf8InCpp String ifName, boolean enable);
  void interfaceSetMtu(in @utf8InCpp String ifName, int mtu);
  void tetherAddForward(in @utf8InCpp String intIface, in @utf8InCpp String extIface);
  void tetherRemoveForward(in @utf8InCpp String intIface, in @utf8InCpp String extIface);
  void setTcpRWmemorySize(in @utf8InCpp String rmemValues, in @utf8InCpp String wmemValues);
  void registerUnsolicitedEventListener(android.net.INetdUnsolicitedEventListener listener);
  /**
   * @deprecated unimplemented on T+.
   */
  void firewallAddUidInterfaceRules(in @utf8InCpp String ifName, in int[] uids);
  /**
   * @deprecated unimplemented on T+.
   */
  void firewallRemoveUidInterfaceRules(in int[] uids);
  /**
   * @deprecated unimplemented on T+.
   */
  void trafficSwapActiveStatsMap();
  IBinder getOemNetd();
  void tetherStartWithConfiguration(in android.net.TetherConfigParcel config);
  android.net.MarkMaskParcel getFwmarkForNetwork(int netId);
  void networkAddRouteParcel(int netId, in android.net.RouteInfoParcel routeInfo);
  void networkUpdateRouteParcel(int netId, in android.net.RouteInfoParcel routeInfo);
  void networkRemoveRouteParcel(int netId, in android.net.RouteInfoParcel routeInfo);
  /**
   * @deprecated This method has no effect and throws UnsupportedOperationException. The mainline module accesses the BPF map directly starting in S. See BpfCoordinator.
   */
  void tetherOffloadRuleAdd(in android.net.TetherOffloadRuleParcel rule);
  /**
   * @deprecated This method has no effect and throws UnsupportedOperationException. The mainline module accesses the BPF map directly starting in S. See BpfCoordinator.
   */
  void tetherOffloadRuleRemove(in android.net.TetherOffloadRuleParcel rule);
  /**
   * @deprecated This method has no effect and throws UnsupportedOperationException. The mainline module accesses the BPF map directly starting in S. See BpfCoordinator.
   */
  android.net.TetherStatsParcel[] tetherOffloadGetStats();
  /**
   * @deprecated This method has no effect and throws UnsupportedOperationException. The mainline module accesses the BPF map directly starting in S. See BpfCoordinator.
   */
  void tetherOffloadSetInterfaceQuota(int ifIndex, long quotaBytes);
  /**
   * @deprecated This method has no effect and throws UnsupportedOperationException. The mainline module accesses the BPF map directly starting in S. See BpfCoordinator.
   */
  android.net.TetherStatsParcel tetherOffloadGetAndClearStats(int ifIndex);
  void networkCreate(in android.net.NativeNetworkConfig config);
  void networkAddUidRangesParcel(in android.net.netd.aidl.NativeUidRangeConfig uidRangesConfig);
  void networkRemoveUidRangesParcel(in android.net.netd.aidl.NativeUidRangeConfig uidRangesConfig);
  void ipSecMigrate(in android.net.IpSecMigrateInfoParcel migrateInfo);
  void setNetworkAllowlist(in android.net.netd.aidl.NativeUidRangeConfig[] allowedNetworks);
  void networkAllowBypassVpnOnNetwork(boolean allow, int uid, int netId);
  const int IPV4 = 4;
  const int IPV6 = 6;
  const int CONF = 1;
  const int NEIGH = 2;
  const String IPSEC_INTERFACE_PREFIX = "ipsec";
  const int IPV6_ADDR_GEN_MODE_EUI64 = 0;
  const int IPV6_ADDR_GEN_MODE_NONE = 1;
  const int IPV6_ADDR_GEN_MODE_STABLE_PRIVACY = 2;
  const int IPV6_ADDR_GEN_MODE_RANDOM = 3;
  const int IPV6_ADDR_GEN_MODE_DEFAULT = 0;
  const int PENALTY_POLICY_ACCEPT = 1;
  const int PENALTY_POLICY_LOG = 2;
  const int PENALTY_POLICY_REJECT = 3;
  const int CLAT_MARK = 0xdeadc1a7;
  const int LOCAL_NET_ID = 99;
  const int DUMMY_NET_ID = 51;
  const int UNREACHABLE_NET_ID = 52;
  const String NEXTHOP_NONE = "";
  const String NEXTHOP_UNREACHABLE = "unreachable";
  const String NEXTHOP_THROW = "throw";
  const int PERMISSION_NONE = 0;
  const int PERMISSION_NETWORK = 1;
  const int PERMISSION_SYSTEM = 2;
  /**
   * @deprecated usage is internal to module.
   */
  const int NO_PERMISSIONS = 0;
  /**
   * @deprecated usage is internal to module.
   */
  const int PERMISSION_INTERNET = 4;
  /**
   * @deprecated usage is internal to module.
   */
  const int PERMISSION_UPDATE_DEVICE_STATS = 8;
  /**
   * @deprecated usage is internal to module.
   */
  const int PERMISSION_UNINSTALLED = (-1) /* -1 */;
  /**
   * @deprecated use FIREWALL_ALLOWLIST.
   */
  const int FIREWALL_WHITELIST = 0;
  const int FIREWALL_ALLOWLIST = 0;
  /**
   * @deprecated use FIREWALL_DENYLIST.
   */
  const int FIREWALL_BLACKLIST = 1;
  const int FIREWALL_DENYLIST = 1;
  const int FIREWALL_RULE_ALLOW = 1;
  const int FIREWALL_RULE_DENY = 2;
  const int FIREWALL_CHAIN_NONE = 0;
  const int FIREWALL_CHAIN_DOZABLE = 1;
  const int FIREWALL_CHAIN_STANDBY = 2;
  const int FIREWALL_CHAIN_POWERSAVE = 3;
  const int FIREWALL_CHAIN_RESTRICTED = 4;
  const String IF_STATE_UP = "up";
  const String IF_STATE_DOWN = "down";
  const String IF_FLAG_BROADCAST = "broadcast";
  const String IF_FLAG_LOOPBACK = "loopback";
  const String IF_FLAG_POINTOPOINT = "point-to-point";
  const String IF_FLAG_RUNNING = "running";
  const String IF_FLAG_MULTICAST = "multicast";
  const int IPSEC_DIRECTION_IN = 0;
  const int IPSEC_DIRECTION_OUT = 1;
}
