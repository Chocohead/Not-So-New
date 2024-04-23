package com.chocohead.nsn.http;

import java.io.IOException;

public class HttpTimeoutException extends IOException {
	private static final long serialVersionUID = 981344271622632951L;

	public HttpTimeoutException(String message) {
		super(message);
	}

	HttpTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}
}