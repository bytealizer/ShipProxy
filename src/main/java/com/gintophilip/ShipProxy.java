package com.gintophilip;

import com.gintophilip.models.BrowserClientRequest;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ShipProxy {

    private int proxyServerPort;
    private String offShoreProxyAddress;
    private int offShoreProxyPort;
    BlockingQueue<BrowserClientRequest> requestQueue = new LinkedBlockingQueue<>();

    public void listenAndServe(int proxyServerPort) throws IOException {
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

        OutputStream finalOffShoreProxyOutputStream = offShoreProxyOutputStream;
        InputStream finalOffShoreProxyInputStream = offShoreProxyInputStream;
        new Thread(() -> {
            while (true) {
                try {
                    BrowserClientRequest req = requestQueue.take();
                    System.out.println(req.getRequestId());

                    // Send request to offshore proxy
                    finalOffShoreProxyOutputStream.write(req.getRequest().getBytes(StandardCharsets.UTF_8));
                    finalOffShoreProxyOutputStream.flush();
                    // Read response from offshore proxy
                    OutputStream browserClientOut = req.getBrowserClient().getOutputStream();
                    byte[] response = getResponseFromOffShoreProxy(finalOffShoreProxyInputStream);
                    // Send response back to client
                    browserClientOut.write(response);
                    browserClientOut.flush();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();


        ServerSocket shipProxyServerSocket = null;
        try {
            shipProxyServerSocket = new ServerSocket(this.proxyServerPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        start(shipProxyServerSocket, offShoreProxyInputStream, offShoreProxyOutputStream);

    }


    public void start(ServerSocket shipProxyServerSocket, InputStream offShoreProxyInputStream,
                      OutputStream offShoreProxyOutputStream) throws IOException {
        while (true) {
            Socket browserClient = shipProxyServerSocket.accept();
            new Thread(() -> handleClientRequest(browserClient, offShoreProxyInputStream, offShoreProxyOutputStream)).start();
        }
    }

    private void handleClientRequest(Socket browserClient, InputStream offShoreProxyInputStream,
                                     OutputStream offShoreProxyOutputStream) {
        try  {
            BufferedReader reader = new BufferedReader(new InputStreamReader(browserClient.getInputStream()));
            String request = buildRequest(reader);
            sendRequestToQueue(new BrowserClientRequest(request,browserClient));
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

    private void sendRequestToQueue(BrowserClientRequest browserClientRequest) {
        requestQueue.add(browserClientRequest);
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
    private byte[] getResponseFromOffShoreProxy(InputStream offShoreProxyInputStream) throws IOException {
        ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        int contentLength = -1;

        // Step 1: Read headers
        int prev = -1, curr;
        boolean foundHeaderEnd = false;

        while (!foundHeaderEnd && (curr = offShoreProxyInputStream.read()) != -1) {
            headerBuffer.write(curr);
            responseBytes.write(curr);

            // Check for end of headers (\r\n\r\n)
            if (prev == '\r' && curr == '\n') {
                byte[] temp = headerBuffer.toByteArray();
                int len = temp.length;
                if (len >= 4 &&
                        temp[len - 4] == '\r' &&
                        temp[len - 3] == '\n' &&
                        temp[len - 2] == '\r' &&
                        temp[len - 1] == '\n') {
                    foundHeaderEnd = true;
                }
            }
            prev = curr;
        }

        // Parse content-length from headers if present
        String headers = headerBuffer.toString(StandardCharsets.ISO_8859_1);
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    contentLength = Integer.parseInt(line.split(":", 2)[1].trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        // Step 2: Read body
        byte[] buffer = new byte[8192];
        if (contentLength > 0) {
            int totalRead = 0;
            while (totalRead < contentLength) {
                int bytesToRead = Math.min(buffer.length, contentLength - totalRead);
                int bytesRead = offShoreProxyInputStream.read(buffer, 0, bytesToRead);
                if (bytesRead == -1) break; // Unexpected EOF
                responseBytes.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
        } else {
            // No Content-Length or unreliable, read until EOF
            int bytesRead;
            while ((bytesRead = offShoreProxyInputStream.read(buffer)) != -1) {
                responseBytes.write(buffer, 0, bytesRead);
            }
        }

        return responseBytes.toByteArray();
    }

    private String getResponseFromOffShoreProxy1(InputStream offShoreProxyInputStream) throws IOException {
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
                //To Do
                ///  fix content length mismatch
//                throw new IOException("Incomplete body read from offshore proxy");
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
