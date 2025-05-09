/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.net.thread.utils;

import android.annotation.Nullable;
import android.net.InetAddresses;
import android.net.IpPrefix;
import android.os.SystemClock;

import com.android.compatibility.common.util.SystemUtil;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wrapper of the "/system/bin/ot-ctl" which can be used to send CLI commands to ot-daemon to
 * control its behavior.
 *
 * <p>Note that this class takes root privileged to run.
 */
public final class OtDaemonController {
    public static final int DIAG_VENDOR_NAME_TLV_TYPE = 25;
    public static final int DIAG_VENDOR_MODEL_TLV_TYPE = 26;

    private static final String OT_CTL = "/system/bin/ot-ctl";

    /**
     * Factory resets ot-daemon.
     *
     * <p>This will erase all persistent data written into apexdata/com.android.apex/ot-daemon and
     * restart the ot-daemon service.
     */
    public void factoryReset() {
        executeCommand("factoryreset");

        // TODO(b/323164524): ot-ctl is a separate process so that the tests can't depend on the
        // time sequence. Here needs to wait for system server to receive the ot-daemon death
        // signal and take actions.
        // A proper fix is to replace "ot-ctl" with "cmd thread_network ot-ctl" which is
        // synchronized with the system server
        SystemClock.sleep(500);
    }

    /** Returns the output string of the "ot-ctl br state" command. */
    public String getBorderRoutingState() {
        return executeCommandAndParse("br state").getFirst();
    }

    /** Returns the output string of the "ot-ctl srp server state" command. */
    public String getSrpServerState() {
        return executeCommandAndParse("srp server state").getFirst();
    }

    /** Returns the list of IPv6 addresses on ot-daemon. */
    public List<Inet6Address> getAddresses() {
        return executeCommandAndParse("ipaddr").stream()
                .map(addr -> InetAddresses.parseNumericAddress(addr))
                .map(inetAddr -> (Inet6Address) inetAddr)
                .toList();
    }

    /** Returns the OMR address of this device or {@code null} if it doesn't exist. */
    @Nullable
    public Inet6Address getOmrAddress() {
        List<Inet6Address> allAddresses = new ArrayList<>(getAddresses());
        allAddresses.removeAll(getMeshLocalAddresses());

        List<Inet6Address> omrAddresses =
                allAddresses.stream()
                        .filter(addr -> !addr.isLinkLocalAddress())
                        .collect(Collectors.toList());
        if (omrAddresses.isEmpty()) {
            return null;
        } else if (omrAddresses.size() > 1) {
            throw new IllegalStateException();
        }

        return omrAddresses.getFirst();
    }

    /** Returns {@code true} if the Thread interface is up. */
    public boolean isInterfaceUp() {
        String output = executeCommand("ifconfig");
        return output.contains("up");
    }

    /** Returns the ML-EID of the device. */
    public Inet6Address getMlEid() {
        String addressStr = executeCommandAndParse("ipaddr mleid").get(0);
        return (Inet6Address) InetAddresses.parseNumericAddress(addressStr);
    }

    /** Returns the country code on ot-daemon. */
    public String getCountryCode() {
        return executeCommandAndParse("region").get(0);
    }

    /**
     * Returns the list of IPv6 Mesh-Local addresses on ot-daemon.
     *
     * <p>The return List can be empty if no Mesh-Local prefix exists.
     */
    public List<Inet6Address> getMeshLocalAddresses() {
        IpPrefix meshLocalPrefix = getMeshLocalPrefix();
        if (meshLocalPrefix == null) {
            return Collections.emptyList();
        }
        return getAddresses().stream().filter(addr -> meshLocalPrefix.contains(addr)).toList();
    }

    /**
     * Returns the Mesh-Local prefix or {@code null} if none exists (e.g. the Active Dataset is not
     * set).
     */
    @Nullable
    public IpPrefix getMeshLocalPrefix() {
        List<IpPrefix> prefixes =
                executeCommandAndParse("prefix meshlocal").stream()
                        .map(prefix -> new IpPrefix(prefix))
                        .toList();
        return prefixes.isEmpty() ? null : prefixes.get(0);
    }

    /** Enables/Disables NAT64 feature. */
    public void setNat64Enabled(boolean enabled) {
        executeCommand("nat64 " + (enabled ? "enable" : "disable"));
    }

    /** Sets the NAT64 CIDR. */
    public void setNat64Cidr(String cidr) {
        executeCommand("nat64 cidr " + cidr);
    }

    /** Returns whether there's a NAT64 prefix in network data */
    public boolean hasNat64PrefixInNetdata() {
        // Example (in the 'Routes' section):
        // fdb2:bae3:5b59:2:0:0::/96 sn low c000
        List<String> outputLines = executeCommandAndParse("netdata show");
        for (String line : outputLines) {
            if (line.contains(" sn")) {
                return true;
            }
        }
        return false;
    }

    /** Adds a prefix in the Network Data. */
    public void addPrefixInNetworkData(IpPrefix ipPrefix, String flags, String preference) {
        executeCommand("prefix add " + ipPrefix + " " + flags + " " + preference);
        executeCommand("netdata register");
    }

    public int getTrelPort() {
        return Integer.parseInt(executeCommandAndParse("trel port").get(0));
    }

    public String getExtendedAddr() {
        return executeCommandAndParse("extaddr").get(0);
    }

    public String getExtendedPanId() {
        return executeCommandAndParse("extpanid").get(0);
    }

    public boolean isCountryCodeSupported() {
        final String result = executeCommand("region");

        return !result.equals("Error 12: NotImplemented\r\n");
    }

    public String executeCommand(String cmd) {
        return SystemUtil.runShellCommand(OT_CTL + " " + cmd);
    }

    /**
     * Sends DIAG_GET request to the given peer device and returns the parsed result as a dict of
     * the requested TLV values.
     *
     * <p>For example, a request {@code netDiagGet("fdad:3d13:7b11:4049:ed1a:7e87:4770:a345",
     * [DIAG_VENDOR_NAME_TLV_TYPE, DIAG_VENDOR_MODEL_TLV_TYPE])} can return a dict of {@code
     * {"Vendor Name" : "ABC", "Vendor Model" : "Cuttlefish"}}
     */
    public Map<String, String> netDiagGet(InetAddress peerAddr, List<Integer> tlvTypes) {
        String tlvTypeList =
                tlvTypes.stream().map(Object::toString).collect(Collectors.joining(" "));

        List<String> outputs =
                executeCommandAndParse(
                        "networkdiagnostic get " + peerAddr.getHostAddress() + " " + tlvTypeList);
        Map<String, String> result = new HashMap<>();
        for (String line : outputs) {
            if (line.startsWith("DIAG_GET")) {
                continue;
            }
            String[] keyValue = line.split(":");
            if (keyValue.length != 2) {
                throw new IllegalStateException("Unexpected OT output: " + line);
            }
            result.put(keyValue[0].strip(), keyValue[1].strip());
        }
        return result;
    }

    /**
     * Executes a ot-ctl command and parse the output to a list of strings.
     *
     * <p>The trailing "Done" in the command output will be dropped.
     */
    public List<String> executeCommandAndParse(String cmd) {
        return Arrays.asList(executeCommand(cmd).split("\n")).stream()
                .map(String::trim)
                .filter(str -> !str.equals("Done"))
                .toList();
    }
}
