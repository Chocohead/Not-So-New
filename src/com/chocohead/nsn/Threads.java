package com.chocohead.nsn;

import java.lang.management.ThreadInfo;

import org.apache.commons.lang3.ThreadUtils;

public class Threads {
	public static boolean isDaemon(ThreadInfo self) {
		Thread thread = ThreadUtils.findThreadById(self.getThreadId());
		if (thread == null) throw new UnsupportedOperationException("Couldn't find thread " + self.getThreadName());
		return thread.isDaemon();
	}
}