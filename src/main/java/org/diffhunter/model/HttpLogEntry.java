package org.diffhunter.model;

import burp.api.montoya.http.HttpService;

import java.util.Date;

/**
 * Represents an HTTP log entry with request and response data.
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
    private final String requestStr;
    private final String responseStr;
    private final byte[] requestBytes;
    private final HttpService httpService;
    private volatile boolean marked;
    private volatile RowDiffType rowDiffType;

    /**
     * Creates a new HTTP log entry with all request and response data.
     */
    public HttpLogEntry(int number, Date timestamp, String tool, String method, String host,
                        String path, String query, int statusCode, int length, long responseTime,
                        String requestStr, String responseStr, byte[] requestBytes, HttpService httpService) {
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
        this.requestStr = requestStr;
        this.responseStr = responseStr;
        this.requestBytes = requestBytes;
        this.httpService = httpService;
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

    /** Returns the full request as a string. */
    public String getRequestStr() { return requestStr; }

    /** Returns the full response as a string. */
    public String getResponseStr() { return responseStr; }

    /** Returns the raw request bytes. */
    public byte[] getRequestBytes() { return requestBytes; }

    /** Returns the HTTP service information. */
    public HttpService getHttpService() { return httpService; }

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
}
