package com.gintophilip.models;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class ClientDetails {
    private final String ipAddress;
    private final int port;
    private final String hostName;
    private final String canonicalHostName;
    private final boolean isLoopback;
    private final boolean isSiteLocal;
    private final boolean isReachable;
    public ClientDetails(Socket socket) {
        InetAddress inetAddress = socket.getInetAddress();

        this.ipAddress = inetAddress.getHostAddress();
        this.port = socket.getPort();
        this.hostName = inetAddress.getHostName();
        this.canonicalHostName = inetAddress.getCanonicalHostName();
        this.isLoopback = inetAddress.isLoopbackAddress();
        this.isSiteLocal = inetAddress.isSiteLocalAddress();

        boolean reachable = false;
        try {
            reachable = inetAddress.isReachable(1000); // 1-second timeout
        } catch (IOException ignored) {
        }
        this.isReachable = reachable;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public String getHostName() {
        return hostName;
    }

    public String getCanonicalHostName() {
        return canonicalHostName;
    }

    public boolean isLoopback() {
        return isLoopback;
    }

    public boolean isSiteLocal() {
        return isSiteLocal;
    }

    public boolean isReachable() {
        return isReachable;
    }

    @Override
    public String toString() {
        return "ClientDetails {\n" +
                "  IP Address          : " + ipAddress + "\n" +
                "  Port                : " + port + "\n" +
                "  Host Name           : " + hostName + "\n" +
                "  Canonical Host Name : " + canonicalHostName + "\n" +
                "  Is Loopback         : " + isLoopback + "\n" +
                "  Is Site Local       : " + isSiteLocal + "\n" +
                "  Is Reachable        : " + isReachable + "\n" +
                '}';
    }
}
