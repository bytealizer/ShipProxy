package com.gintophilip;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ShipProxy {

    private int proxyServerPort;
    private String offShoreProxyAddress;
    private int offShoreProxyPort;
    BlockingQueue<BrowserClientRequest> requestQueue = new LinkedBlockingQueue<>();

    public void listenAndServe(int proxyServerPort) {
        this.proxyServerPort = proxyServerPort;

        OutputStream offShoreProxyOutputStream = null;
        InputStream offShoreProxyInputStream = null;
        Socket socketToOffshoreProxy = null;
        try  {
             socketToOffshoreProxy = new Socket(offShoreProxyAddress, offShoreProxyPort);
            System.out.println("hjj");
            offShoreProxyOutputStream = socketToOffshoreProxy.getOutputStream();
            offShoreProxyInputStream = socketToOffshoreProxy.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ServerSocket shipProxyServerSocket = null;
        try {
            shipProxyServerSocket = new ServerSocket(this.proxyServerPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        start(shipProxyServerSocket, offShoreProxyInputStream, offShoreProxyOutputStream);

    }


    public void start(ServerSocket shipProxyServerSocket, InputStream offShoreProxyInputStream,
                      OutputStream offShoreProxyOutputStream) {
        while (true) {
            try {
                Socket browserClient = shipProxyServerSocket.accept();
                new Thread(() -> handleClientRequest(browserClient, offShoreProxyInputStream, offShoreProxyOutputStream)).start();
            } catch (IOException e) {
                System.err.println("Failed to accept connection: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handleClientRequest(Socket browserClient, InputStream offShoreProxyInputStream,
                                     OutputStream offShoreProxyOutputStream) {
        try  {
            BufferedReader reader = new BufferedReader(new InputStreamReader(browserClient.getInputStream()));
            PrintWriter out = new PrintWriter(browserClient.getOutputStream(), true);

            String request = buildRequest(reader);
            // Send request to offshore proxy
            offShoreProxyOutputStream.write(request.getBytes(StandardCharsets.UTF_8));
            offShoreProxyOutputStream.flush();
            // Read response from offshore proxy
            String response = getResponseFromOffShoreProxy(offShoreProxyInputStream);
            // Send response back to client
            out.print(response);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error handling client request: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
//                browserClient.close();
                System.out.println("kk");
            } catch (Exception e) {
                System.err.println("Failed to close browser client socket: " + e.getMessage());
            }
        }
    }

    private String buildRequest(BufferedReader reader) throws IOException {
        StringBuilder requestBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            requestBuilder.append(line).append("\r\n");
        }
        requestBuilder.append("\r\n"); // End of headers
        return requestBuilder.toString();
    }

    private String getResponseFromOffShoreProxy(InputStream offShoreProxyInputStream) throws IOException {
        BufferedReader proxyReader = new BufferedReader(new InputStreamReader(offShoreProxyInputStream));
        StringBuilder responseBuilder = new StringBuilder();
        String line;
        int contentLength = -1;
        // Get response headers
        while ((line = proxyReader.readLine()) != null && !line.isEmpty()) {
            responseBuilder.append(line).append("\r\n");
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            }
        }
        responseBuilder.append("\r\n");
        // Read body if present
        if (contentLength > 0) {
            char[] body = new char[contentLength];
            int read = proxyReader.read(body, 0, contentLength);
            if (read != contentLength) {
                throw new IOException("Incomplete body read from offshore proxy");
            }
            responseBuilder.append(body);
        }
        return responseBuilder.toString();
    }

    public void setOffShoreProxyAndPort(String address, int port) {
        this.offShoreProxyAddress = address;
        this.offShoreProxyPort = port;
    }
}
