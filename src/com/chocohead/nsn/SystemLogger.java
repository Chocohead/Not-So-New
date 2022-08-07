package com.chocohead.nsn;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface SystemLogger {
	enum Level {
		ALL(Integer.MIN_VALUE, org.apache.logging.log4j.Level.ALL),
		TRACE(400, org.apache.logging.log4j.Level.TRACE),
		DEBUG(500, org.apache.logging.log4j.Level.DEBUG),
		INFO(800, org.apache.logging.log4j.Level.INFO),
		WARNING(900, org.apache.logging.log4j.Level.WARN),
		ERROR(1000, org.apache.logging.log4j.Level.ERROR),
		OFF(Integer.MAX_VALUE, org.apache.logging.log4j.Level.OFF);

		private final int severity;
		public final org.apache.logging.log4j.Level level;

		Level(int value, org.apache.logging.log4j.Level level) {
			severity = value;
			this.level = level;
		}

		public String getName() {
			return name();
		}

		public int getSeverity() {
			return severity;
		}
	}
	Map<String, SystemLogger> LOGGERS = new HashMap<>();

	static SystemLogger getLogger(String name) {
		return LOGGERS.computeIfAbsent(name, k -> {
			return new SystemLogger() {
				private final Logger log = LogManager.getFormatterLogger(k);

				@Override
				public String getName() {
					return k;
				}

				@Override
				public boolean isLoggable(Level level) {
					return log.isEnabled(Objects.requireNonNull(level, "level").level);
				}

				@Override
				public void log(Level level, String message) {
					log.log(Objects.requireNonNull(level, "level").level, message);
				}

				@Override
				public void log(Level level, Supplier<String> message) {
					log.log(Objects.requireNonNull(level, "level").level, message);
				}

				@Override
				public void log(Level level, Object value) {
					log.log(Objects.requireNonNull(level, "level").level, value);
				}

				@Override
				public void log(Level level, String message, Throwable throwable) {
					log.log(Objects.requireNonNull(level, "level").level, message, throwable);
				}

				@Override
				public void log(Level level, Supplier<String> message, Throwable throwable) {
					log.log(Objects.requireNonNull(level, "level").level, message, throwable);
				}

				@Override
				public void log(Level level, String message, Object... values) {
					log.log(Objects.requireNonNull(level, "level").level, message, values);
				}

				@Override
				public void log(Level level, ResourceBundle bundle, String message, Throwable throwable) {
					if (bundle == null) {
						log(level, message, throwable);
					} else {
						throw new UnsupportedOperationException("Tried to log at " + level.getName() + ": " + message + " with exception " + throwable + " using " + bundle);
					}
				}

				@Override
				public void log(Level level, ResourceBundle bundle, String message, Object... values) {
					if (bundle == null) {
						log(level, message, values);
					} else {
						throw new UnsupportedOperationException("Tried to log at " + level.getName() + ": " + MessageFormat.format(message, values) + " using " + bundle);
					}
				}
			};
		});
	}

	String getName();

	boolean isLoggable(Level level);

	void log(Level level, String message);

	void log(Level level, Supplier<String> message);

	void log(Level level, Object value);

	void log(Level level, String message, Throwable throwable);

	void log(Level level, Supplier<String> message, Throwable throwable);

	void log(Level level, String message, Object... values);

	void log(Level level, ResourceBundle bundle, String message, Throwable throwable);

	void log(Level level, ResourceBundle bundle, String message, Object... values);
}