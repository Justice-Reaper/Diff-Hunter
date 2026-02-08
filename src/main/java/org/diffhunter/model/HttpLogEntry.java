package org.diffhunter.model;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Represents an HTTP log entry with request and response data.
 * Uses HttpRequestResponse backed by temporary files to minimize memory usage.
 */
public class HttpLogEntry {

    private final int number;
    private final Date timestamp;
    private final String tool;
    private final String method;
    private final String host;
    private final String path;
    private final String query;
    private final int statusCode;
    private final int length;
    private final long responseTime;
    private final HttpRequestResponse httpRequestResponse;
    private volatile boolean marked;
    private volatile RowDiffType rowDiffType;

    /**
     * Creates a new HTTP log entry with HttpRequestResponse backed by temp file.
     */
    public HttpLogEntry(int number, Date timestamp, String tool, String method, String host,
                        String path, String query, int statusCode, int length, long responseTime,
                        HttpRequestResponse httpRequestResponse) {
        this.number = number;
        this.timestamp = timestamp;
        this.tool = tool;
        this.method = method;
        this.host = host;
        this.path = path;
        this.query = query;
        this.statusCode = statusCode;
        this.length = length;
        this.responseTime = responseTime;
        this.httpRequestResponse = httpRequestResponse;
        this.marked = false;
        this.rowDiffType = RowDiffType.NONE;
    }

    /** Returns the sequential request number. */
    public int getNumber() { return number; }

    /** Returns the timestamp when the request was captured. */
    public Date getTimestamp() { return timestamp; }

    /** Returns the Burp tool that generated this request. */
    public String getTool() { return tool; }

    /** Returns the HTTP method (GET, POST, etc.). */
    public String getMethod() { return method; }

    /** Returns the target host. */
    public String getHost() { return host; }

    /** Returns the request path. */
    public String getPath() { return path; }

    /** Returns the query string without the leading question mark. */
    public String getQuery() { return query; }

    /** Returns the HTTP response status code. */
    public int getStatusCode() { return statusCode; }

    /** Returns the response length in bytes. */
    public int getLength() { return length; }

    /** Returns the response time in milliseconds. */
    public long getResponseTime() { return responseTime; }

    /** Returns the full request as a string with normalized line endings. */
    public String getRequestStr() {
        if (httpRequestResponse == null || httpRequestResponse.request() == null) {
            return "";
        }
        return normalizeLineEndings(httpRequestResponse.request().toString());
    }

    /** Returns the full response as a string with normalized line endings. */
    public String getResponseStr() {
        if (httpRequestResponse == null || httpRequestResponse.response() == null) {
            return "";
        }
        return normalizeLineEndings(httpRequestResponse.response().toString());
    }

    /** Returns the raw request bytes. */
    public byte[] getRequestBytes() {
        if (httpRequestResponse == null || httpRequestResponse.request() == null) {
            return new byte[0];
        }
        return httpRequestResponse.request().toByteArray().getBytes();
    }

    /** Returns the raw response bytes. */
    public byte[] getResponseBytes() {
        if (httpRequestResponse == null || httpRequestResponse.response() == null) {
            return new byte[0];
        }
        return httpRequestResponse.response().toByteArray().getBytes();
    }

    /** Returns the HTTP service information. */
    public HttpService getHttpService() {
        if (httpRequestResponse == null || httpRequestResponse.request() == null) {
            return null;
        }
        return httpRequestResponse.request().httpService();
    }

    /** Returns the underlying HttpRequestResponse object. */
    public HttpRequestResponse getHttpRequestResponse() {
        return httpRequestResponse;
    }

    /** Returns true if this entry is marked as a target. */
    public boolean isMarked() { return marked; }

    /** Sets whether this entry is marked as a target. */
    public void setMarked(boolean marked) { this.marked = marked; }

    /** Returns the row difference type for table coloring. */
    public RowDiffType getRowDiffType() { return rowDiffType; }

    /** Sets the row difference type for table coloring. */
    public void setRowDiffType(RowDiffType rowDiffType) { this.rowDiffType = rowDiffType; }

    /** Returns true if this entry has any differences from the target. */
    public boolean isMarkedAsDifferent() { return rowDiffType != RowDiffType.NONE; }

    /** Returns the full endpoint (path + query string). */
    public String getEndpoint() {
        return path + (query.isEmpty() ? "" : "?" + query);
    }

    /**
     * Normalizes line endings to Unix-style (LF only).
     */
    private static String normalizeLineEndings(String text) {
        if (text == null || !text.contains("\r")) return text;
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}
