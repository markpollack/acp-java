/*
 * Copyright 2025-2025 the original author or authors.
 */

package org.acp.spec;

import io.modelcontextprotocol.json.TypeRef;
import org.acp.util.Assert;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Default implementation of the ACP (Agent Client Protocol) client session that manages
 * bidirectional JSON-RPC communication between clients and agents. This implementation
 * follows the ACP specification for message exchange and transport handling.
 *
 * <p>
 * The session manages:
 * <ul>
 * <li>Request/response handling with unique message IDs</li>
 * <li>Notification processing</li>
 * <li>Message timeout management</li>
 * <li>Transport layer abstraction</li>
 * </ul>
 *
 * <p>
 * This is the client-side session that sends requests to an agent (initialize,
 * newSession, prompt, etc.) and handles incoming requests from the agent (readTextFile,
 * writeTextFile, requestPermission, etc.)
 * </p>
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 */
public class AcpClientSession implements AcpSession {

	private static final Logger logger = LoggerFactory.getLogger(AcpClientSession.class);

	/** Duration to wait for request responses before timing out */
	private final Duration requestTimeout;

	/** Transport layer implementation for message exchange */
	private final AcpClientTransport transport;

	/** Map of pending responses keyed by request ID */
	private final ConcurrentHashMap<Object, MonoSink<AcpSchema.JSONRPCResponse>> pendingResponses = new ConcurrentHashMap<>();

	/** Map of request handlers keyed by method name */
	private final ConcurrentHashMap<String, RequestHandler<?>> requestHandlers = new ConcurrentHashMap<>();

	/** Map of notification handlers keyed by method name */
	private final ConcurrentHashMap<String, NotificationHandler> notificationHandlers = new ConcurrentHashMap<>();

	/** Session-specific prefix for request IDs */
	private final String sessionPrefix = UUID.randomUUID().toString().substring(0, 8);

	/** Atomic counter for generating unique request IDs */
	private final AtomicLong requestCounter = new AtomicLong(0);

	/**
	 * Functional interface for handling incoming JSON-RPC requests. Implementations
	 * should process the request parameters and return a response.
	 *
	 * @param <T> Response type
	 */
	@FunctionalInterface
	public interface RequestHandler<T> {

		/**
		 * Handles an incoming request with the given parameters.
		 * @param params The request parameters
		 * @return A Mono containing the response object
		 */
		Mono<T> handle(Object params);

	}

	/**
	 * Functional interface for handling incoming JSON-RPC notifications. Implementations
	 * should process the notification parameters without returning a response.
	 */
	@FunctionalInterface
	public interface NotificationHandler {

		/**
		 * Handles an incoming notification with the given parameters.
		 * @param params The notification parameters
		 * @return A Mono that completes when the notification is processed
		 */
		Mono<Void> handle(Object params);

	}

	/**
	 * Creates a new AcpClientSession with the specified configuration and handlers.
	 * @param requestTimeout Duration to wait for responses
	 * @param transport Transport implementation for message exchange
	 * @param requestHandlers Map of method names to request handlers
	 * @param notificationHandlers Map of method names to notification handlers
	 * @deprecated Use
	 * {@link #AcpClientSession(Duration, AcpClientTransport, Map, Map, Function)}
	 */
	@Deprecated
	public AcpClientSession(Duration requestTimeout, AcpClientTransport transport,
			Map<String, RequestHandler<?>> requestHandlers, Map<String, NotificationHandler> notificationHandlers) {
		this(requestTimeout, transport, requestHandlers, notificationHandlers, Function.identity());
	}

	/**
	 * Creates a new AcpClientSession with the specified configuration and handlers.
	 * @param requestTimeout Duration to wait for responses
	 * @param transport Transport implementation for message exchange
	 * @param requestHandlers Map of method names to request handlers
	 * @param notificationHandlers Map of method names to notification handlers
	 * @param connectHook Hook that allows transforming the connection Publisher prior to
	 * subscribing
	 */
	public AcpClientSession(Duration requestTimeout, AcpClientTransport transport,
			Map<String, RequestHandler<?>> requestHandlers, Map<String, NotificationHandler> notificationHandlers,
			Function<? super Mono<Void>, ? extends Publisher<Void>> connectHook) {

		Assert.notNull(requestTimeout, "The requestTimeout can not be null");
		Assert.notNull(transport, "The transport can not be null");
		Assert.notNull(requestHandlers, "The requestHandlers can not be null");
		Assert.notNull(notificationHandlers, "The notificationHandlers can not be null");

		this.requestTimeout = requestTimeout;
		this.transport = transport;
		this.requestHandlers.putAll(requestHandlers);
		this.notificationHandlers.putAll(notificationHandlers);

		this.transport.connect(mono -> mono.doOnNext(this::handle)).transform(connectHook).subscribe();
	}

	private void dismissPendingResponses() {
		this.pendingResponses.forEach((id, sink) -> {
			logger.warn("Abruptly terminating exchange for request {}", id);
			sink.error(new RuntimeException("ACP session with agent terminated"));
		});
		this.pendingResponses.clear();
	}

	private void handle(AcpSchema.JSONRPCMessage message) {
		if (message instanceof AcpSchema.JSONRPCResponse response) {
			logger.debug("Received response: {}", response);
			if (response.id() != null) {
				var sink = pendingResponses.remove(response.id());
				if (sink == null) {
					logger.warn("Unexpected response for unknown id {}", response.id());
				}
				else {
					sink.success(response);
				}
			}
			else {
				logger.error("Discarded ACP request response without session id. "
						+ "This is an indication of a bug in the request sender code that can lead to memory "
						+ "leaks as pending requests will never be completed.");
			}
		}
		else if (message instanceof AcpSchema.JSONRPCRequest request) {
			logger.debug("Received request: {}", request);
			handleIncomingRequest(request).onErrorResume(error -> {
				var errorResponse = new AcpSchema.JSONRPCResponse(AcpSchema.JSONRPC_VERSION, request.id(), null,
						new AcpSchema.JSONRPCError(-32603, error.getMessage(), null));
				return Mono.just(errorResponse);
			}).flatMap(this.transport::sendMessage).onErrorComplete(t -> {
				logger.warn("Issue sending response to the agent, ", t);
				return true;
			}).subscribe();
		}
		else if (message instanceof AcpSchema.JSONRPCNotification notification) {
			logger.debug("Received notification: {}", notification);
			handleIncomingNotification(notification).onErrorComplete(t -> {
				logger.error("Error handling notification: {}", t.getMessage());
				return true;
			}).subscribe();
		}
		else {
			logger.warn("Received unknown message type: {}", message);
		}
	}

	/**
	 * Handles an incoming JSON-RPC request by routing it to the appropriate handler.
	 * @param request The incoming JSON-RPC request
	 * @return A Mono containing the JSON-RPC response
	 */
	private Mono<AcpSchema.JSONRPCResponse> handleIncomingRequest(AcpSchema.JSONRPCRequest request) {
		return Mono.defer(() -> {
			var handler = this.requestHandlers.get(request.method());
			if (handler == null) {
				MethodNotFoundError error = getMethodNotFoundError(request.method());
				return Mono.just(new AcpSchema.JSONRPCResponse(AcpSchema.JSONRPC_VERSION, request.id(), null,
						new AcpSchema.JSONRPCError(-32601, error.message(), error.data())));
			}

			return handler.handle(request.params())
				.map(result -> new AcpSchema.JSONRPCResponse(AcpSchema.JSONRPC_VERSION, request.id(), result, null));
		});
	}

	record MethodNotFoundError(String method, String message, Object data) {
	}

	private MethodNotFoundError getMethodNotFoundError(String method) {
		// ACP-specific error messages for unsupported client methods
		switch (method) {
			case AcpSchema.METHOD_FS_READ_TEXT_FILE:
				return new MethodNotFoundError(method, "File system read not supported",
						Map.of("reason", "Client does not have fs.readTextFile capability"));
			case AcpSchema.METHOD_FS_WRITE_TEXT_FILE:
				return new MethodNotFoundError(method, "File system write not supported",
						Map.of("reason", "Client does not have fs.writeTextFile capability"));
			case AcpSchema.METHOD_TERMINAL_CREATE:
			case AcpSchema.METHOD_TERMINAL_OUTPUT:
			case AcpSchema.METHOD_TERMINAL_RELEASE:
			case AcpSchema.METHOD_TERMINAL_WAIT_FOR_EXIT:
			case AcpSchema.METHOD_TERMINAL_KILL:
				return new MethodNotFoundError(method, "Terminal not supported",
						Map.of("reason", "Client does not have terminal capability"));
			default:
				return new MethodNotFoundError(method, "Method not found: " + method, null);
		}
	}

	/**
	 * Handles an incoming JSON-RPC notification by routing it to the appropriate handler.
	 * @param notification The incoming JSON-RPC notification
	 * @return A Mono that completes when the notification is processed
	 */
	private Mono<Void> handleIncomingNotification(AcpSchema.JSONRPCNotification notification) {
		return Mono.defer(() -> {
			var handler = notificationHandlers.get(notification.method());
			if (handler == null) {
				logger.warn("No handler registered for notification method: {}", notification);
				return Mono.empty();
			}
			return handler.handle(notification.params());
		});
	}

	/**
	 * Generates a unique request ID in a non-blocking way. Combines a session-specific
	 * prefix with an atomic counter to ensure uniqueness.
	 * @return A unique request ID string
	 */
	private String generateRequestId() {
		return this.sessionPrefix + "-" + this.requestCounter.getAndIncrement();
	}

	/**
	 * Sends a JSON-RPC request and returns the response.
	 * @param <T> The expected response type
	 * @param method The method name to call
	 * @param requestParams The request parameters
	 * @param typeRef Type reference for response deserialization
	 * @return A Mono containing the response
	 */
	@Override
	public <T> Mono<T> sendRequest(String method, Object requestParams, TypeRef<T> typeRef) {
		String requestId = this.generateRequestId();

		return Mono.deferContextual(ctx -> Mono.<AcpSchema.JSONRPCResponse>create(pendingResponseSink -> {
			logger.debug("Sending message for method {} with id {}", method, requestId);
			this.pendingResponses.put(requestId, pendingResponseSink);
			AcpSchema.JSONRPCRequest jsonrpcRequest = new AcpSchema.JSONRPCRequest(AcpSchema.JSONRPC_VERSION, requestId,
					method, requestParams);
			this.transport.sendMessage(jsonrpcRequest).contextWrite(ctx).subscribe(v -> {
			}, error -> {
				this.pendingResponses.remove(requestId);
				pendingResponseSink.error(error);
			});
		})).timeout(this.requestTimeout).handle((jsonRpcResponse, deliveredResponseSink) -> {
			if (jsonRpcResponse.error() != null) {
				logger.error("Error handling request: {}", jsonRpcResponse.error());
				deliveredResponseSink.error(new AcpError(jsonRpcResponse.error()));
			}
			else {
				if (typeRef.getType().equals(Void.class)) {
					deliveredResponseSink.complete();
				}
				else {
					deliveredResponseSink.next(this.transport.unmarshalFrom(jsonRpcResponse.result(), typeRef));
				}
			}
		});
	}

	/**
	 * Sends a JSON-RPC notification.
	 * @param method The method name for the notification
	 * @param params The notification parameters
	 * @return A Mono that completes when the notification is sent
	 */
	@Override
	public Mono<Void> sendNotification(String method, Object params) {
		AcpSchema.JSONRPCNotification jsonrpcNotification = new AcpSchema.JSONRPCNotification(AcpSchema.JSONRPC_VERSION,
				method, params);
		return this.transport.sendMessage(jsonrpcNotification);
	}

	/**
	 * Closes the session gracefully, allowing pending operations to complete.
	 * @return A Mono that completes when the session is closed
	 */
	@Override
	public Mono<Void> closeGracefully() {
		return Mono.fromRunnable(this::dismissPendingResponses);
	}

	/**
	 * Closes the session immediately, potentially interrupting pending operations.
	 */
	@Override
	public void close() {
		dismissPendingResponses();
	}

	/**
	 * ACP-specific error wrapper for JSON-RPC errors.
	 */
	public static class AcpError extends RuntimeException {

		private final AcpSchema.JSONRPCError error;

		public AcpError(AcpSchema.JSONRPCError error) {
			super(error.message());
			this.error = error;
		}

		public AcpSchema.JSONRPCError getError() {
			return error;
		}

		public int getCode() {
			return error.code();
		}

		public Object getData() {
			return error.data();
		}

	}

}
