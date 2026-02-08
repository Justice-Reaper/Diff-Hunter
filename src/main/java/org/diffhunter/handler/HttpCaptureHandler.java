package org.diffhunter.handler;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import org.diffhunter.model.HttpLogEntry;
import org.diffhunter.ui.UIContext;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles HTTP request/response capture from Burp Suite.
 */
public class HttpCaptureHandler implements HttpHandler {

    private final MontoyaApi api;
    private final UIContext context;
    private final ConcurrentHashMap<Integer, Long> requestStartTimes = new ConcurrentHashMap<>();

    /**
     * Creates a new HttpCaptureHandler with the specified API and context.
     */
    public HttpCaptureHandler(MontoyaApi api, UIContext context) {
        this.api = api;
        this.context = context;
    }

    /**
     * Records the start time of an outgoing request for response time calculation.
     */
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        if (requestStartTimes.size() > 1000) {
            long cutoff = System.currentTimeMillis() - 300000;
            requestStartTimes.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
        requestStartTimes.put(requestToBeSent.messageId(), System.currentTimeMillis());
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    /**
     * Captures HTTP responses and creates log entries for in-scope requests.
     */
    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        try {
            if (context.isExtensionUnloading() || !context.isCaptureEnabled()) {
                return ResponseReceivedAction.continueWith(responseReceived);
            }

            HttpRequest request = responseReceived.initiatingRequest();

            String host = request.httpService().host();
            if (host == null || host.isEmpty()) {
                return ResponseReceivedAction.continueWith(responseReceived);
            }

            boolean secure = request.httpService().secure();
            String hostUrl = (secure ? "https://" : "http://") + host;

            if (!api.scope().isInScope(hostUrl)) {
                return ResponseReceivedAction.continueWith(responseReceived);
            }

            String fullPath = request.path();
            String path;
            String query;

            int queryIndex = fullPath.indexOf('?');
            if (queryIndex != -1) {
                path = fullPath.substring(0, queryIndex);
                query = fullPath.substring(queryIndex + 1);
            } else {
                path = fullPath;
                query = "";
            }

            int responseLength = responseReceived.toByteArray().length();

            long responseTime = 0;
            Long startTime = requestStartTimes.remove(responseReceived.messageId());
            if (startTime != null) {
                responseTime = System.currentTimeMillis() - startTime;
            }

            // Create HttpRequestResponse and copy to temp file to minimize memory usage
            HttpRequestResponse httpRequestResponse = HttpRequestResponse
                    .httpRequestResponse(request, responseReceived)
                    .copyToTempFile();

            HttpLogEntry entry;
            synchronized (context.getWriteLock()) {
                entry = new HttpLogEntry(
                        context.incrementAndGetRequestCounter(),
                        new Date(),
                        responseReceived.toolSource().toolType().toolName(),
                        request.method(),
                        request.httpService().host(),
                        path,
                        query,
                        responseReceived.statusCode(),
                        responseLength,
                        responseTime,
                        httpRequestResponse
                );

                context.getLogEntries().add(entry);
                context.getLogEntriesMap().put(entry.getNumber(), entry);
            }

            context.getPendingEntries().add(entry);
        } catch (Exception e) {
            api.logging().logToError("[DiffHunter] Error processing HTTP response: " + e.getMessage());
        }

        return ResponseReceivedAction.continueWith(responseReceived);
    }

    /**
     * Clears all tracked request start times. Called during extension unload.
     */
    public void cleanup() {
        requestStartTimes.clear();
    }
}
