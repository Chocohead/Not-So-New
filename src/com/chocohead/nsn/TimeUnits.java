package com.chocohead.nsn;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TimeUnits {
	public static long convert(TimeUnit self, Duration duration) {
        long seconds = duration.getSeconds();
        int nano = duration.getNano();

        if (seconds < 0 && nano > 0) {
        	seconds++; //Less than a second, but more than zero
            nano -= 1_000_000_000;
        }

        long ratio;
        switch (self) {
        case DAYS:
        case HOURS:
        case MINUTES: //Always getting smaller, won't overflow
        	return self.convert(seconds, TimeUnit.SECONDS);

        case SECONDS:
        	return seconds;

        case MILLISECONDS:
        	nano /= 1_000_000;
        	ratio = 1_000;
        	break;

        case MICROSECONDS:
        	nano /= 1_000;
        	ratio = 1_000_000;
        	break;

        case NANOSECONDS:
        	ratio = 1_000_000_000;
        	break;

        default:
        	throw new AssertionError(self);
        }

        long out = seconds * ratio + nano;
        long overflow = Long.MAX_VALUE / ratio;
        if ((seconds < overflow && seconds > -overflow) || (seconds == overflow && out > 0) || (seconds == -overflow && out < 0)) {
        	return out;
        } else {
        	return seconds > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }
}