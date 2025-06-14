package com.gintophilip.shipproxy.proxy;

import com.gintophilip.shipproxy.models.BrowserClientRequest;

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
            System.out.println("[ship_proxy] ship proxy client established socket "+socketToOffshoreProxy.getInetAddress()+":"+socketToOffshoreProxy.getPort());
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
                    System.out.println("current request queue "+requestQueue.size());
                    BrowserClientRequest req = requestQueue.take();
                    System.out.println("fetched request from queue "+req.getRequest());


                    // Send request to offshore proxy
                    sendToOffshoreProxy(finalOffShoreProxyOutputStream, req);
                    System.out.println("sent request.waiting for response");
                    // Read response from offshore proxy
                    OutputStream browserClientOut = req.getBrowserClient().getOutputStream();
                    getResponseFromOffShoreProxy(finalOffShoreProxyInputStream,browserClientOut,req.getBrowserClient());
                    System.out.println("currrent items in request queue "+requestQueue.size());

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();


        ServerSocket shipProxyServerSocket = null;
        try {
            shipProxyServerSocket = new ServerSocket(this.proxyServerPort);
            System.out.println("[ship_proxy] ship proxy client created "+shipProxyServerSocket.getInetAddress().getHostAddress()+":"+shipProxyServerSocket.getLocalPort());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        start(shipProxyServerSocket, offShoreProxyInputStream, offShoreProxyOutputStream);

    }

    private  void sendToOffshoreProxy(OutputStream finalOffShoreProxyOutputStream, BrowserClientRequest req) throws IOException {
        System.out.println("sending to of shore proxy");
        finalOffShoreProxyOutputStream.write(req.getRequest().getBytes(StandardCharsets.UTF_8));
        finalOffShoreProxyOutputStream.flush();
    }


    public void start(ServerSocket shipProxyServerSocket, InputStream offShoreProxyInputStream,
                      OutputStream offShoreProxyOutputStream) throws IOException {
        while (true) {
            Socket browserClient = shipProxyServerSocket.accept();
            System.out.println("[ship_proxy]connection received from client "+browserClient.getInetAddress().getHostAddress()+":"+browserClient.getPort());
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

        }
    }

    private void sendRequestToQueue(BrowserClientRequest browserClientRequest) {
        System.out.println("sending request to queue");
        System.out.println("\t"+browserClientRequest.getRequest());
        requestQueue.add(browserClientRequest);
        System.out.println("current items in request queue "+requestQueue.size());
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



    private void getResponseFromOffShoreProxy(InputStream offShoreProxyInputStream,
                                              OutputStream browserClientOut,
                                              Socket browserClient) throws IOException {

        ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        int contentLength = -1;

        // Step 1: Read headers
        int prev = -1, curr;
        boolean foundHeaderEnd = false;

        while (!foundHeaderEnd && (curr = offShoreProxyInputStream.read()) != -1) {
            headerBuffer.write(curr);
            responseBytes.write(curr);

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

        String headers = headerBuffer.toString(StandardCharsets.ISO_8859_1);
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    contentLength = Integer.parseInt(line.split(":", 2)[1].trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        // Step 2: Send headers to client
        browserClientOut.write(responseBytes.toByteArray());

        byte[] buffer = new byte[8192];

        // Step 3: Handle response body
        if (contentLength > 0) {
            int totalRead = 0;
            while (totalRead < contentLength) {
                int bytesToRead = Math.min(buffer.length, contentLength - totalRead);
                int bytesRead = offShoreProxyInputStream.read(buffer, 0, bytesToRead);
                if (bytesRead == -1) break;
                browserClientOut.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
        } else {
            boolean isChunked = headers.toLowerCase().contains("transfer-encoding: chunked");

            if (isChunked) {
                System.out.println("received chunked");
                final byte[] END_MARKER = "\r\nEND_OF_RESPONSE_FROM_OFF_SHORE_PROXY\r\n".getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
                int matchIndex = 0;

                int bytesRead;
                while ((bytesRead = offShoreProxyInputStream.read(buffer)) != -1) {
                    for (int i = 0; i < bytesRead; i++) {
                        byte b = buffer[i];
                        responseBuffer.write(b);

                        // Marker detection
                        if (b == END_MARKER[matchIndex]) {
                            matchIndex++;
                            if (matchIndex == END_MARKER.length) {
                                byte[] fullData = responseBuffer.toByteArray();
                                int dataLength = fullData.length - END_MARKER.length;
                                browserClientOut.write(fullData, 0, dataLength);
                                browserClientOut.flush();
                                System.out.println("END_MARKER detected, response flushed.");
//                                browserClient.shutdownOutput(); // Optional
//                                browserClient.close();
                                return;
                            }
                        } else {
                            matchIndex = (b == END_MARKER[0]) ? 1 : 0;
                        }
                    }

                    // stream intermediate chunk to client
//                    browserClientOut.write(buffer, 0, bytesRead);
//                    browserClientOut.flush();
                }
            } else {
                int bytesRead;
                while ((bytesRead = offShoreProxyInputStream.read(buffer)) != -1) {
                    browserClientOut.write(buffer, 0, bytesRead);
                    browserClientOut.flush();
                }
            }
        }

        browserClientOut.flush();
//        browserClient.close();
        System.out.println("sent result to client completed");
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
