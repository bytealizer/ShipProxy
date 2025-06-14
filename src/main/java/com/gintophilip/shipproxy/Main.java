package com.gintophilip.shipproxy;

import com.gintophilip.shipproxy.proxy.ShipProxy;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Integer listenPort = null;
        String offshoreProxy = null;  // default
        int offshorePort = -1;             // default

        // Parse named arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    if (i + 1 < args.length) {
                        listenPort = Integer.parseInt(args[++i]);
                    } else {
                        System.err.println("Missing value for --port");
                        System.exit(1);
                    }
                    break;

                case "--offshoreProxy":
                    if (i + 1 < args.length) {
                        offshoreProxy = args[++i];
                    } else {
                        System.err.println("Missing value for --offshoreProxy");
                        System.exit(1);
                    }
                    break;

                case "--offshorePort":
                    if (i + 1 < args.length) {
                        offshorePort = Integer.parseInt(args[++i]);
                    } else {
                        System.err.println("Missing value for --offshorePort");
                        System.exit(1);
                    }
                    break;

                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }

        // Validate required argument
        if (listenPort == null) {
            System.err.println("Missing required argument: --port <port>");
            System.exit(1);
        }
        if (offshoreProxy == null) {
            System.err.println("Missing required argument: --offshoreProxy <port>");
            System.exit(1);
        }
        if (offshorePort == -1) {
            System.err.println("Missing required argument: --offshorePort <port>");
            System.exit(1);
        }
        ShipProxy shipProxy = new ShipProxy();
        shipProxy.setOffShoreProxyAndPort(offshoreProxy, offshorePort);
        try {
            shipProxy.listenAndServe(listenPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}