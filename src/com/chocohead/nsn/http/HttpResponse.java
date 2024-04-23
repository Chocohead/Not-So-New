package com.chocohead.nsn.http;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableSet;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.entity.ContentType;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;

import com.chocohead.nsn.http.HttpResponse.ResponseInfo;

public interface HttpResponse<T> {
	HttpRequest request();

	HttpHeaders headers();

	int statusCode();

	T body();

	interface ResponseInfo {
		int statusCode();

		HttpHeaders headers();

		default HttpEntity getResponse() {
			throw new UnsupportedOperationException("TODO: " + getClass().getName());
		}
	}

	interface BodyHandler<T> {
		BodySubscriber<T> apply(ResponseInfo info);
	}

	class BodyHandlers {
		public static BodyHandler<Void> discarding() {
			return response -> BodySubscribers.discarding();
		}

		public static <U> BodyHandler<U> replacing(U value) {
			return response -> BodySubscribers.replacing(value);
		}

		public static BodyHandler<InputStream> ofInputStream() {
			return response -> BodySubscribers.ofInputStream();
		}

		public static BodyHandler<Void> ofByteArrayConsumer(Consumer<Optional<byte[]>> consumer) {
			return response -> BodySubscribers.ofByteArrayConsumer(consumer);
		}

		public static BodyHandler<byte[]> ofByteArray() {
			return response -> BodySubscribers.ofByteArray();
		}

		private static Charset responseCharset(HttpEntity entity) {
			try {
				ContentType contentType = ContentType.get(entity);

				if (contentType != null) {
					return contentType.getCharset();
				}
			} catch (ParseException | UnsupportedCharsetException e) {
			}
			return StandardCharsets.UTF_8;
		}

		public static BodyHandler<String> ofString() {
			return response -> BodySubscribers.ofString(responseCharset(response.getResponse()));
		}

		public static BodyHandler<String> ofString(Charset charset) {
			return response -> BodySubscribers.ofString(charset);
		}

		public static BodyHandler<Stream<String>> ofLines() {
			return response -> BodySubscribers.ofLines(responseCharset(response.getResponse()));
		}

		public static BodyHandler<Path> ofFile(Path file) {
			return response -> BodySubscribers.ofFile(file);
		}

		public static BodyHandler<Path> ofFile(Path file, OpenOption... options) {
			return response -> BodySubscribers.ofFile(file, options);
		}

		public static BodyHandler<Path> ofFileDownload(Path directory, OpenOption... options) {
			if (Files.notExists(directory)) {
				throw new IllegalArgumentException(directory + " does not exist");
			}
			if (!Files.isDirectory(directory)) {
				throw new IllegalArgumentException(directory + " is not a directory");
			}
			if (!Files.isWritable(directory)) {
				throw new IllegalArgumentException("Can't write to " + directory);
			}

			return response -> {
				if (!(response instanceof ResponseInfoImpl)) throw new UnsupportedOperationException("TODO");
				String header = ((ResponseInfoImpl) response).getFirstHeader("Content-Disposition");
				if (header == null) throw new UncheckedIOException(new IOException("No Content-Disposition header in " + response.headers()));

				Matcher match = Pattern.compile("^attachment;.*?filename\\s*=\\s*(?:\"(?:[^\\x00-\\x1F]*/)?(?:[^/\\x00-\\x1F]*\\\\\\\\)?([^/\\x00-\\x1F]*?)(?<!\\\\)\"|(?!\")([!#-'*+\\-.0-9A-Z^-z|~]+)[ \t]*(?:;|$))", Pattern.CASE_INSENSITIVE).matcher(header);
				if (!match.matches()) throw new UncheckedIOException(new IOException("Invalid Content-Disposition header: " + header));

				String filename = match.group(2);
				if (filename == null) {
					filename = StringUtils.remove(match.group(1), '\\');
					assert filename != null: header;
					filename = filename.trim();
				} else {
					assert filename == filename.trim(): header;
				}

				if (ImmutableSet.of(".", "..", "", "~" , "|").contains(filename)) {
					throw new UncheckedIOException(new IOException("Invalid Content-Disposition filename: " + filename + " (from " + header + ')'));
				}

				Path file = Paths.get(directory.toString(), filename);
				if (!file.startsWith(directory)) {
					throw new SecurityException("Download destination file " + file + " is outside of original directory?");
				}
				return BodySubscribers.ofFile(file, options);
			};
		}

		public static <T> BodyHandler<T> buffering(BodyHandler<T> downstream, int bufferSize) {
			return response -> BodySubscribers.buffering(downstream.apply(response), bufferSize);
		}
	}

	interface BodySubscriber<T> {
		CompletionStage<T> getBody();

		default void receive(HttpEntity entity) {
			throw new UnsupportedOperationException("TODO: " + getClass().getName());
		}
	}

	class BodySubscribers {
		public static BodySubscriber<Void> discarding() {
			return replacing(null);
		}

		public static <T> BodySubscriber<T> replacing(T value) {
			return new BodySubscriber<T>() {
				private final CompletableFuture<T> result = new CompletableFuture<>();

				@Override
				public void receive(HttpEntity entity) {
					try {
						EntityUtils.consume(entity);
						result.complete(value);
					} catch (IOException e) {
						result.completeExceptionally(e);
					}
				}

				@Override
				public CompletionStage<T> getBody() {
					return result;
				}
			};
		}

		public static BodySubscriber<InputStream> ofInputStream() {
			/*class InputStreamBodySubscriber extends ProxyInputStream implements BodySubscriber<InputStream> {
				private final CountDownLatch latch = new CountDownLatch(1);
				private HttpEntity entity;

				public InputStreamBodySubscriber() {
					super(null);
				}

				@Override
				protected void beforeRead(int n) throws IOException {
					try {
						latch.await();
					} catch (InterruptedException e) {
					}
					if (in == null && entity != null) in = entity.getContent();
				}

				@Override
				public void close() throws IOException {
					super.close();
				}

				@Override
				public void receive(HttpEntity entity) {
					this.entity = entity;
					latch.countDown();
				}

				@Override
				public CompletionStage<InputStream> getBody() {
					return CompletableFuture.completedFuture(this);
				}
			}
			return new InputStreamBodySubscriber();*/
			return mapping(ofByteArray(), ByteArrayInputStream::new); //Currently don't support streaming the body...
		}

		public static BodySubscriber<Void> ofByteArrayConsumer(Consumer<Optional<byte[]>> consumer) {
			return new BodySubscriber<Void>() {
				private final CompletableFuture<Void> result = new CompletableFuture<>();

				@Override
				public void receive(HttpEntity entity) {
					try (InputStream in = entity.getContent()) {
						if (in != null) {
							byte[] buffer = new byte[4096];
							for (int read = in.read(buffer); read != IOUtils.EOF; read = in.read(buffer)) {
								consumer.accept(Optional.of(buffer));
							}
						}

						consumer.accept(Optional.empty());
						result.complete(null);
					} catch (IOException e) {
						result.completeExceptionally(e);
					}
				}

				@Override
				public CompletionStage<Void> getBody() {
					return result;
				}
			};
		}

		public static BodySubscriber<byte[]> ofByteArray() {
			return new BodySubscriber<byte[]>() {
				private final CompletableFuture<byte[]> result = new CompletableFuture<>();

				@Override
				public void receive(HttpEntity entity) {
					try {
						result.complete(EntityUtils.toByteArray(entity));
					} catch (IOException e) {
						result.completeExceptionally(e);
					}
				}

				@Override
				public CompletionStage<byte[]> getBody() {
					return result;
				}
			};
		}

		public static BodySubscriber<String> ofString(Charset charset) {
			return mapping(ofByteArray(), bytes -> new String(bytes, charset));
		}

		public static BodySubscriber<Stream<String>> ofLines(Charset charset) {
			return mapping(ofInputStream(), in -> new BufferedReader(new InputStreamReader(in, charset)).lines().onClose(() -> IOUtils.closeQuietly(in)));
		}

		public static BodySubscriber<Path> ofFile(Path file) {
			return ofFile(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		}

		public static BodySubscriber<Path> ofFile(Path file, OpenOption... options) {
			if (ArrayUtils.contains(options, StandardOpenOption.DELETE_ON_CLOSE) || ArrayUtils.contains(options, StandardOpenOption.READ)) {
				throw new IllegalArgumentException("Invalid options in " + Arrays.toString(options));
			}

			return new BodySubscriber<Path>() {
				private final CompletableFuture<Path> result = new CompletableFuture<>();

				@Override
				public void receive(HttpEntity entity) {
					try (InputStream in = entity.getContent(); OutputStream out = Files.newOutputStream(file, options)) {
						if (in != null) {
							IOUtils.copy(in, out);
						}

						result.complete(file);
					} catch (IOException e) {
						result.completeExceptionally(e);
					}
				}

				@Override
				public CompletionStage<Path> getBody() {
					return result;
				}
			};
		}

		public static <T> BodySubscriber<T> buffering(BodySubscriber<T> downstream, int bufferSize) {
			return new BodySubscriber<T>() {
				@Override
				public void receive(HttpEntity entity) {
					downstream.receive(new BufferedHttpEntity(entity, bufferSize));
				}

				@Override
				public CompletionStage<T> getBody() {
					return downstream.getBody();
				}
			};
		}

		public static <T, B> BodySubscriber<B> mapping(BodySubscriber<T> upstream, Function<? super T, ? extends B> mapper) {
			return new BodySubscriber<B>() {
				@Override
				public void receive(HttpEntity entity) {
					upstream.receive(entity);
				}

				@Override
				public CompletionStage<B> getBody() {
					return upstream.getBody().thenApply(mapper);
				}
			};
		}
	}
}

class ResponseInfoImpl implements ResponseInfo {
	private final org.apache.http.HttpResponse response;

	ResponseInfoImpl(org.apache.http.HttpResponse response) {
		this.response = response;
	}

	@Override
	public int statusCode() {
		return response.getStatusLine().getStatusCode();
	}

	@Override
	public HttpHeaders headers() {
		HeaderGroup headers = new HeaderGroup();
		headers.setHeaders(response.getAllHeaders());
		return new HttpHeaders(headers);
	}

	String getFirstHeader(String name) {
		Header header = response.getFirstHeader(name);
		return header != null ? header.getValue() : null;
	}

	@Override
	public HttpEntity getResponse() {
		return response.getEntity();
	}
}

class HttpResponseImpl<T> implements HttpResponse<T> {
	private final HttpRequest request;
	private final ResponseInfoImpl response;
	private final T body;

	HttpResponseImpl(HttpRequest request, ResponseInfoImpl response, T body) {
		this.request = request;
		this.response = response;
		this.body = body;
	}

	@Override
	public HttpRequest request() {
		return request;
	}

	@Override
	public HttpHeaders headers() {
		return response.headers();
	}

	@Override
	public int statusCode() {
		return response.statusCode();
	}

	@Override
	public T body() {
		return body;
	}
}