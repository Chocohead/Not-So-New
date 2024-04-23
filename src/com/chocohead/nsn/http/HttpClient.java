package com.chocohead.nsn.http;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.IOUtils;

import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.chocohead.nsn.CompletableFutures;
import com.chocohead.nsn.http.HttpResponse.BodyHandler;
import com.chocohead.nsn.http.HttpResponse.BodySubscriber;

public abstract class HttpClient {
	public interface Builder {
		HttpClient build();
	}

	protected HttpClient() {
	}

	public static HttpClient newHttpClient() {
		return newBuilder().build();
	}

	public static Builder newBuilder() {
		return new HttpClientImpl.BuilderImpl();
	}

	public abstract <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler) throws IOException, InterruptedException;

	public abstract <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> handler);
}

class HttpClientImpl extends HttpClient {
	static class BuilderImpl implements Builder {

		@Override
		public HttpClient build() {
			return new HttpClientImpl();
		}
	}

	HttpClientImpl() {
	}

	@Override
	public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler) throws IOException, InterruptedException {
		CompletableFuture<HttpResponse<T>> response = null;
		try {
			response = sendAsync(request, handler);
			return response.get();
		} catch (InterruptedException e) {
			if (response != null) response.cancel(true);
			throw e;
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();

			if (cause instanceof IllegalArgumentException) {
				throw (IllegalArgumentException) cause;
			} else if (cause instanceof SecurityException) {
				throw (SecurityException) cause;
			} else if (cause instanceof HttpConnectTimeoutException) {
				throw (HttpConnectTimeoutException) cause;
			} else if (cause instanceof HttpTimeoutException) {
				throw (HttpTimeoutException) cause;
			} else if (cause instanceof ConnectException) {//Don't think this is included in HttpConnectTimeoutException
				throw (ConnectException) cause;
			} else if (cause instanceof IOException) {
				throw (IOException) cause;
			} else {
				throw new IOException(cause.getMessage(), cause);
			}
		}
	}

	@Override
	public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest outerRequest, BodyHandler<T> handler) {
		CloseableHttpClient client = HttpClients.createDefault();
		return CompletableFuture.completedFuture(outerRequest).thenComposeAsync(request -> {
			RequestBuilder builder = RequestBuilder.create(request.method()).setUri(request.uri());
			RequestConfig.Builder config = RequestConfig.custom().setExpectContinueEnabled(request.expectContinue());

			for (Entry<String, List<String>> entry : request.headers().map().entrySet()) {
				String header = entry.getKey();

				for (String value : entry.getValue()) {
					builder.addHeader(header, value);
				}
			}

			request.bodyPublisher().ifPresent(body -> {
				HttpEntity entity = body.asHttpEntity();
				if (entity == null) return; //Not a real body
				builder.setEntity(entity);
			});

			request.timeout().ifPresent(timeout -> {//Hopefully no one has a timeout beyond 2.1B milliseconds (~24.8 days)
				int millis = Math.toIntExact(timeout.toMillis());
				config.setConnectTimeout(millis).setSocketTimeout(Math.toIntExact(millis));
			});

			builder.setConfig(config.build());
			HttpResponse<T> out;
			try {
				out = client.execute(builder.build(), new ResponseHandler<HttpResponse<T>>() {
					@Override
					public HttpResponse<T> handleResponse(org.apache.http.HttpResponse response) throws IOException {
						ResponseInfoImpl info = new ResponseInfoImpl(response);
						BodySubscriber<T> subscriber = handler.apply(info);
						subscriber.receive(response.getEntity());
						T body = subscriber.getBody().toCompletableFuture().join(); //An end consumer won't notice blocking for the body
						return new HttpResponseImpl<>(request, info, body);
					}
				});
			} catch (SocketTimeoutException e) {
				return CompletableFutures.failedFuture(new HttpTimeoutException(e.getMessage(), e));
			} catch (ConnectTimeoutException e) {
				return CompletableFutures.failedFuture(new HttpConnectTimeoutException(e.getMessage(), e));
			} catch (IOException e) {
				return CompletableFutures.failedFuture(e);
			}
			return CompletableFuture.completedFuture(out);
		}).whenCompleteAsync((response, e) -> IOUtils.closeQuietly(client));
	}
}