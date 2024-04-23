package com.chocohead.nsn.http;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.HeaderGroup;

public abstract class HttpRequest {
	public interface BodyPublisher {
		long contentLength();

		default HttpEntity asHttpEntity() {
			throw new UnsupportedOperationException("TODO: " + getClass().getName());
		}
	}

	public static class BodyPublishers {
		private static BodyPublisher wrapHttpEntity(HttpEntity entity) {
			return new BodyPublisher() {
				@Override
				public HttpEntity asHttpEntity() {
					return entity;
				}

				@Override
				public long contentLength() {
					return entity == null ? 0 : entity.getContentLength();
				}
			};
		}

		public static BodyPublisher ofString(String body) {
			return ofString(body, StandardCharsets.UTF_8);
		}

		public static BodyPublisher ofString(String body, Charset charset) {
			return wrapHttpEntity(new StringEntity(body, charset));
		}

		public static BodyPublisher ofInputStream(Supplier<? extends InputStream> streamSource) {
			return wrapHttpEntity(new RepeatableInputStreamEntity(streamSource));
		}

		public static BodyPublisher ofByteArray(byte[] buf) {
			return wrapHttpEntity(new ByteArrayEntity(buf));
		}

		public static BodyPublisher ofByteArray(byte[] buf, int offset, int length) {
			return wrapHttpEntity(new ByteArrayEntity(buf, offset, length));
		}

		public static BodyPublisher ofFile(Path path) throws FileNotFoundException {
			if (path.getFileSystem() == FileSystems.getDefault()) {
				return wrapHttpEntity(new FileEntity(path.toFile()));
			} else {
				return wrapHttpEntity(new PathEntity(path));
			}
		}

		public static BodyPublisher ofByteArrays(Iterable<byte[]> iter) {
			return wrapHttpEntity(new IterableByteArrayEntity(iter));
		}

		public static BodyPublisher noBody() {
			return wrapHttpEntity(null);
		}
	}

	public interface Builder {
		Builder uri(URI uri);

		Builder header(String name, String value);

		Builder headers(String... headers);

		Builder setHeader(String name, String value);

		Builder expectContinue(boolean enable);

		Builder GET();

		Builder POST(BodyPublisher body);

		Builder PUT(BodyPublisher body);

		Builder DELETE();

		Builder method(String method, BodyPublisher body);

		Builder timeout(Duration timeout);

		HttpRequest build();

		Builder copy();
	}

	protected HttpRequest() {
	}

	public static Builder newBuilder() {
		return new HttpRequestImpl.BuilderImpl();
	}

	public static Builder newBuilder(URI uri) {
		return newBuilder().uri(uri);
	}

	public abstract HttpHeaders headers();

	public abstract boolean expectContinue();

	public abstract String method();

	public abstract URI uri();

	public abstract Optional<BodyPublisher> bodyPublisher();

	public abstract Optional<Duration> timeout();

	@Override
	public final boolean equals(Object obj) {
		if (!(obj instanceof HttpRequest)) return false;
		HttpRequest that = (HttpRequest) obj;
		return that.method().equals(method()) && that.uri().equals(uri()) && that.headers().equals(headers());
	}

	@Override
	public final int hashCode() {
		return method().hashCode() + uri().hashCode() + headers().hashCode();
	}
}

class HttpRequestImpl extends HttpRequest {
	static class BuilderImpl implements Builder {
		private static final Pattern VALID_METHODS = Pattern.compile("^[!#-'*+\\-.0-9A-Z^-z|~]+$");
		private final HeaderGroup headers = new HeaderGroup();
		private boolean expectContinue;
		private String method = "GET";
		private URI uri;
		private BodyPublisher body;
		private Duration timeout;

		@Override
		public Builder uri(URI uri) {
			String scheme = uri.getScheme();
			if (scheme == null) {
				throw new IllegalArgumentException("URI with undefined scheme");
			}
			scheme = scheme.toLowerCase(Locale.ENGLISH);
			if (!scheme.equals("https") && !scheme.equals("http")) {
				throw new IllegalArgumentException("Invalid URI scheme: " + scheme);
			}
			if (uri.getHost() == null) {
				throw new IllegalArgumentException("Unsupported URI: " + uri);
			}
			this.uri = uri;
			return this;
		}

		@Override
		public Builder header(String name, String value) {
			headers.addHeader(new BasicHeader(name, value));
			return this;
		}

		@Override
		public Builder headers(String... headers) {
			if (headers.length % 2 != 0 || headers.length == 0) {
				throw new IllegalArgumentException("Expected even number of header name-value pairs");
			}
			for (int i = 0; i < headers.length; i += 2) {
				header(headers[i], headers[i + 1]);
			}
			return this;
		}

		@Override
		public Builder setHeader(String name, String value) {
			headers.updateHeader(new BasicHeader(name, value));
			return this;
		}

		@Override
		public Builder expectContinue(boolean enable) {
			expectContinue = enable;
			return this;
		}

		@Override
		public Builder GET() {
			return setMethod("GET", null);
		}

		@Override
		public Builder POST(BodyPublisher body) {
			return setMethod("POST", body);
		}

		@Override
		public Builder PUT(BodyPublisher body) {
			return setMethod("PUT", body);
		}

		@Override
		public Builder DELETE() {
			return setMethod("DELETE", null);
		}

		@Override
		public Builder method(String method, BodyPublisher body) {
			if (!VALID_METHODS.matcher(Objects.requireNonNull(method)).matches()) {
				throw new IllegalArgumentException("Invalid method: \"" + method + '"');
			}
			return setMethod(method, Objects.requireNonNull(body));
		}

		private Builder setMethod(String method, BodyPublisher body) {
			this.method = method;
			this.body = body;
			return this;
		}

		@Override
		public Builder timeout(Duration timeout) {
			if (timeout == null || timeout.isNegative() || timeout.isZero()) {
				throw new IllegalArgumentException("Invalid timeout duration: " + timeout);
			}
			this.timeout = timeout;
			return this;
		}

		@Override
		public HttpRequest build() {
			return new HttpRequestImpl(headers, expectContinue, method, uri, body, timeout);
		}

		@Override
		public Builder copy() {
			BuilderImpl out = new BuilderImpl();
			out.headers.setHeaders(headers.getAllHeaders());
			out.expectContinue = expectContinue;
			out.method = method;
			out.uri = uri;
			out.body = body;
			out.timeout = timeout;
			return out;
		}
	}
	private final HeaderGroup headers;
	private boolean expectContinue;
	private final String method;
	private final URI uri;
	private final BodyPublisher body;
	private final Duration timeout;

	HttpRequestImpl(HeaderGroup headers, boolean expectContinue, String method, URI uri, BodyPublisher body, Duration timeout) {
		this.headers = headers;
		this.expectContinue = expectContinue;
		this.method = method;
		this.uri = uri;
		this.body = body.asHttpEntity() != null ? body : null;
		this.timeout = timeout;
	}

	@Override
	public HttpHeaders headers() {
		return new HttpHeaders(headers);
	}

	@Override
	public boolean expectContinue() {
		return expectContinue;
	}

	@Override
	public String method() {
		return method;
	}

	@Override
	public URI uri() {
		return uri;
	}

	@Override
	public Optional<BodyPublisher> bodyPublisher() {
		return Optional.ofNullable(body);
	}

	@Override
	public Optional<Duration> timeout() {
		return Optional.ofNullable(timeout);
	}
}