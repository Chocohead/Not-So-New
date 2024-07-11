package com.chocohead.nsn;

public class IllegalCallerException extends RuntimeException {
	private static final long serialVersionUID = -2349421918363102232L;

	public IllegalCallerException() {
		super();
	}

	public IllegalCallerException(String message) {
		super(message);
	}

	public IllegalCallerException(Throwable cause) {
		super(cause);
	}

	public IllegalCallerException(String message, Throwable cause) {
		super(message, cause);
	}	
}