package com.chocohead.nsn.http;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.IOUtils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;

import com.chocohead.nsn.CompletableFutures;
import com.chocohead.nsn.http.HttpResponse.BodyHandler;
import com.chocohead.nsn.http.HttpResponse.BodySubscriber;

public abstract class HttpClient {
	public interface Builder {
		Builder version(Version version);

		Builder followRedirects(Redirect policy);

		HttpClient build();
	}

	public enum Version {
		HTTP_1_1, HTTP_2;
	}

	public enum Redirect {
		NEVER, ALWAYS, NORMAL;
	}

	protected HttpClient() {
	}

	public static HttpClient newHttpClient() {
		return newBuilder().build();
	}

	public static Builder newBuilder() {
		return new HttpClientImpl.BuilderImpl();
	}

	public abstract Version version();

	public abstract Redirect followRedirects();

	public abstract <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler) throws IOException, InterruptedException;

	public abstract <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> handler);
}

class HttpClientImpl extends HttpClient {
	static class BuilderImpl implements Builder {
		private Version version = Version.HTTP_2;
		private Redirect redirect = Redirect.NEVER;

		@Override
		public Builder version(Version version) {
			this.version = version;
			return this;
		}

		@Override
		public Builder followRedirects(Redirect policy) {
			redirect = policy;
			return this;
		}

		@Override
		public HttpClient build() {
			return new HttpClientImpl(version, redirect);
		}
	}
	private final Version version;
	private final Redirect redirect;

	HttpClientImpl(Version version, Redirect redirect) {
		this.version = version;
		this.redirect = redirect;
	}

	@Override
	public Version version() {
		return version;
	}

	@Override
	public Redirect followRedirects() {
		return redirect;
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
		HttpClientBuilder clientBuilder = HttpClients.custom();
		if (redirect == Redirect.NEVER) {
			clientBuilder.disableRedirectHandling();
		} else {
			clientBuilder.setRedirectStrategy(new DefaultRedirectStrategy() {
				@Override
				public boolean isRedirected(org.apache.http.HttpRequest request, org.apache.http.HttpResponse response, HttpContext context) throws ProtocolException {
			        switch (response.getStatusLine().getStatusCode()) {
			        case HttpStatus.SC_MOVED_TEMPORARILY:
			        case HttpStatus.SC_MOVED_PERMANENTLY:
			        case HttpStatus.SC_TEMPORARY_REDIRECT:
			        case 308: //Permanent redirect
			        case HttpStatus.SC_SEE_OTHER: {
			        	if (redirect == Redirect.ALWAYS) return true;

		        		Object previousRedirects = context.removeAttribute(HttpClientContext.REDIRECT_LOCATIONS);
		        		try {
		        			URI redirectURI = getLocationURI(request, response, context);
			        		URI requestURI = new URI(request.getRequestLine().getUri());
			        		return requestURI.getScheme().equalsIgnoreCase(redirectURI.getScheme()) || "https".equalsIgnoreCase(redirectURI.getScheme());
		        		} catch (URISyntaxException e) {
		        			throw new ProtocolException(e.getMessage(), e);
		        		} finally {
		        			context.setAttribute(HttpClientContext.REDIRECT_LOCATIONS, previousRedirects);
		        		}
			        }
			        default:
			            return false;
			        }
				}

				@Override
				public HttpUriRequest getRedirect(org.apache.http.HttpRequest request, org.apache.http.HttpResponse response, HttpContext context) throws ProtocolException {
					URI redirectURI = getLocationURI(request, response, context);

					switch (response.getStatusLine().getStatusCode()) {
					case HttpStatus.SC_MOVED_TEMPORARILY:
			        case HttpStatus.SC_MOVED_PERMANENTLY:
			        	if (!HttpPost.METHOD_NAME.equalsIgnoreCase(request.getRequestLine().getMethod())) break;
			        case HttpStatus.SC_SEE_OTHER:
						return HttpHead.METHOD_NAME.equalsIgnoreCase(request.getRequestLine().getMethod()) ? new HttpHead(redirectURI) : new HttpGet(redirectURI);
					}
					return RequestBuilder.copy(request).setUri(redirectURI).build();
				}
			});
		}
		CloseableHttpClient client = clientBuilder.build();
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
				config.setConnectTimeout(millis).setSocketTimeout(millis);
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