package com.gintophilip;

import java.io.IOException;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {

        ShipProxy shipProxy = new ShipProxy();
        String address = "localhost";
        int port = 8081;
        shipProxy.setOffShoreProxyAndPort(address, port);
        shipProxy.listenAndServe(8080);

    }
}