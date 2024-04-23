package com.chocohead.nsn.http;

public class HttpConnectTimeoutException extends HttpTimeoutException {
	private static final long serialVersionUID = 332L;

	public HttpConnectTimeoutException(String message) {
		super(message);
	}

	HttpConnectTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}
}