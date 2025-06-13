package com.gintophilip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class ShipProxy {
    private final int port;

    public ShipProxy(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try (Socket localClient = new Socket("localhost", 8081)) {
            while (true) {
                BufferedReader localClientInput;
                PrintWriter localOutputWriter;
                localClientInput = new BufferedReader(new InputStreamReader(localClient.getInputStream()));
                localOutputWriter = new PrintWriter(localClient.getOutputStream());

                BufferedReader clientInput = new BufferedReader(new InputStreamReader(System.in));

                String input;
                while (true) {
                    System.out.print("You: ");
                    input = clientInput.readLine();

                    if (input == null || input.equalsIgnoreCase("exit")) {
                        break;
                    }
                    localOutputWriter.println(input);
                    localOutputWriter.flush();

                    String response = localClientInput.readLine();
                    System.out.println("Server: " + response);
                }
            }

        }
    }
}
