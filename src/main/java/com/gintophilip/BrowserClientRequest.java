package com.gintophilip;


import java.net.Socket;
import java.util.UUID;

public class BrowserClientRequest {
    private final Socket browserClient;
    private final String request;
    private final UUID requestId;
    private final ClientDetails clientDetails;

    public BrowserClientRequest(String request, Socket browserClient) {
        this.request = request;
        this.browserClient = browserClient;
        this.clientDetails = new ClientDetails(browserClient);
        this.requestId = UUID.randomUUID();
    }

    public Socket getBrowserClient() {
        return browserClient;
    }

    public String getRequest() {
        return request;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public ClientDetails getClientDetails() {
        return clientDetails;
    }
}

