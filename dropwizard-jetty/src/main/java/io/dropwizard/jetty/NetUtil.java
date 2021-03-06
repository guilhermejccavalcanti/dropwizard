/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.dropwizard.jetty;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is taken from the Netty project, and all credit goes to them.
 * It has been modified, to remove dependencies on other classes, and to convert to methods, rather than a
 * static value.
 */
public class NetUtil {

    public static final int DEFAULT_TCP_BACKLOG_WINDOWS = 200;

    public static final int DEFAULT_TCP_BACKLOG_LINUX = 128;

    public static final String TCP_BACKLOG_SETTING_LOCATION = "/proc/sys/net/core/somaxconn";

    private static final AtomicReference<LocalIpFilter> localIpFilter = new AtomicReference<LocalIpFilter>(( nif,  adr) -> (adr != null) && !adr.isLoopbackAddress() && (nif.isPointToPoint() || !adr.isLinkLocalAddress()));

    /**
     * The SOMAXCONN value of the current machine.  If failed to get the value,  {@code 200}  is used as a
     * default value for Windows or {@code 128} for others.
     */
    public static int getTcpBacklog() {
        return getTcpBacklog(getDefaultTcpBacklog());
    }

    /**
     * The SOMAXCONN value of the current machine.  If failed to get the value, <code>defaultBacklog</code> argument is
     * used
     */
    public static int getTcpBacklog(int tcpBacklog) {
        return AccessController.doPrivileged(new PrivilegedAction<Integer>() {

            @Override
            public Integer run() {
                try {
                    String setting = Files.toString(new File(TCP_BACKLOG_SETTING_LOCATION), StandardCharsets.UTF_8);
                    return Integer.parseInt(setting.trim());
                } catch (SecurityExceptionIOException | NumberFormatException | NullPointerException |  e) {
                    return tcpBacklog;
                }
            }
        });
    }

    public static boolean isWindows() {
        final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.US).contains("win");
        return windows;
    }

    public static int getDefaultTcpBacklog() {
        return isWindows() ? DEFAULT_TCP_BACKLOG_WINDOWS : DEFAULT_TCP_BACKLOG_LINUX;
    }

    /**
     * Replace the default local ip filter used by {@link #getAllLocalIPs()}
     *
     * @param newLocalIpFilter the new local ip filter
     */
    public static void setLocalIpFilter(LocalIpFilter newLocalIpFilter) {
        localIpFilter.set(newLocalIpFilter);
    }

    /**
     * Return the current local ip filter used by {@link #getAllLocalIPs()}
     *
     * @return ip filter
     */
    public static LocalIpFilter getLocalIpFilter() {
        return localIpFilter.get();
    }

    /**
     * based on http://pastebin.com/5X073pUc
     * <p>
     *
     * Returns all available IP addresses.
     * <p>
     * In error case or if no network connection is established, we return
     * an empty list here.
     * <p>
     * Loopback addresses are excluded - so 127.0.0.1 will not be never
     * returned.
     * <p>
     * The "primary" IP might not be the first one in the returned list.
     *
     * @return  Returns all IP addresses (can be an empty list in error case
     *          or if network connection is missing).
     * @see http://pastebin.com/5X073pUc
     * @see https://github.com/apache/curator/blob/master/curator-x-discovery/src/main/java/org/apache/curator/x/discovery/ServiceInstanceBuilder.java
     * @throws SocketException errors
     */
    public static Collection<InetAddress> getAllLocalIPs() throws SocketException {
        final List<InetAddress> listAdr = new ArrayList<>();
        final Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
        if (nifs == null) {
            return listAdr;
        }
        while (nifs.hasMoreElements()) {
            final NetworkInterface nif = nifs.nextElement();
            // We ignore subinterfaces - as not yet needed.
            final Enumeration<InetAddress> adrs = nif.getInetAddresses();
            while (adrs.hasMoreElements()) {
                final InetAddress adr = adrs.nextElement();
                if (localIpFilter.get().use(nif, adr)) {
                    listAdr.add(adr);
                }
            }
        }
        return listAdr;
    }
}
