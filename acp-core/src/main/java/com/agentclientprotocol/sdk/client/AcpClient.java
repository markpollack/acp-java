/*
 * Copyright 2025-2025 the original author or authors.
 */

package com.agentclientprotocol.sdk.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.agentclientprotocol.sdk.spec.AcpClientSession;
import com.agentclientprotocol.sdk.spec.AcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSession;
import com.agentclientprotocol.sdk.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Factory class for creating Agent Client Protocol (ACP) clients. ACP is a protocol that
 * enables applications to interact with autonomous coding agents through a standardized
 * interface.
 *
 * <p>
 * This class serves as the main entry point for establishing connections with ACP agents,
 * implementing the client-side of the ACP specification. The protocol follows a
 * client-agent architecture where:
 * <ul>
 * <li>The client (this implementation) initiates connections and sends prompts</li>
 * <li>The agent responds to prompts and can request client capabilities (file access,
 * etc.)</li>
 * <li>Communication occurs through a transport layer (e.g., stdio) using JSON-RPC
 * 2.0</li>
 * </ul>
 *
 * <p>
 * The class provides factory methods to create either:
 * <ul>
 * <li>{@link AcpAsyncClient} for non-blocking operations with Mono responses</li>
 * <li>{@link AcpSyncClient} for blocking operations with direct responses (future)</li>
 * </ul>
 *
 * <p>
 * Example of creating a basic asynchronous client:
 *
 * <pre>{@code
 * // Create transport
 * AgentParameters params = AgentParameters.builder("gemini")
 *     .arg("--experimental-acp")
 *     .build();
 * StdioAcpClientTransport transport = new StdioAcpClientTransport(params, McpJsonMapper.getDefault());
 *
 * // Build client
 * AcpAsyncClient client = AcpClient.async(transport)
 *     .requestTimeout(Duration.ofSeconds(30))
 *     .sessionUpdateConsumer(notification -> {
 *         System.out.println("Session update: " + notification);
 *         return Mono.empty();
 *     })
 *     .build();
 *
 * // Initialize and use
 * client.initialize(new AcpSchema.InitializeRequest(1, new AcpSchema.ClientCapabilities()))
 *     .flatMap(initResponse -> client.newSession(new AcpSchema.NewSessionRequest("/workspace", List.of())))
 *     .flatMap(sessionResponse -> client.prompt(new AcpSchema.PromptRequest(
 *         sessionResponse.sessionId(),
 *         List.of(new AcpSchema.TextContent("Fix the failing test")))))
 *     .doOnNext(response -> System.out.println("Response: " + response))
 *     .block();
 *
 * client.closeGracefully().block();
 * }</pre>
 *
 * <p>
 * The client supports:
 * <ul>
 * <li>Protocol version negotiation and capability exchange</li>
 * <li>Optional authentication with various methods</li>
 * <li>Session creation and management</li>
 * <li>Prompt submission with streaming updates</li>
 * <li>File system operations (read/write) through client handlers</li>
 * <li>Permission requests for sensitive operations</li>
 * <li>Terminal operations for command execution</li>
 * </ul>
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @see AcpAsyncClient
 * @see AcpClientTransport
 */
public interface AcpClient {

	Logger logger = LoggerFactory.getLogger(AcpClient.class);

	/**
	 * Start building a synchronous ACP client with the specified transport layer. The
	 * synchronous ACP client provides blocking operations. Synchronous clients wait for
	 * each operation to complete before returning, making them simpler to use but
	 * potentially less performant for concurrent operations.
	 * @param transport The transport layer implementation for ACP communication
	 * @return A new builder instance for configuring the client
	 * @throws IllegalArgumentException if transport is null
	 */
	static SyncSpec sync(AcpClientTransport transport) {
		return new SyncSpec(transport);
	}

	/**
	 * Start building an asynchronous ACP client with the specified transport layer. The
	 * asynchronous ACP client provides non-blocking operations using Project Reactor's
	 * Mono type. The transport layer handles the low-level communication between client
	 * and agent using protocols like stdio.
	 * @param transport The transport layer implementation for ACP communication. Common
	 * implementation is {@code StdioAcpClientTransport} for stdio-based communication.
	 * @return A new builder instance for configuring the client
	 * @throws IllegalArgumentException if transport is null
	 */
	static AsyncSpec async(AcpClientTransport transport) {
		return new AsyncSpec(transport);
	}

	/**
	 * Asynchronous client specification. This class follows the builder pattern to
	 * provide a fluent API for setting up clients with custom configurations.
	 *
	 * <p>
	 * The builder supports configuration of:
	 * <ul>
	 * <li>Transport layer for client-agent communication</li>
	 * <li>Request timeouts for operation boundaries</li>
	 * <li>Client capabilities for feature negotiation</li>
	 * <li>Request handlers for incoming agent requests (file operations, etc.)</li>
	 * <li>Notification handlers for streaming updates</li>
	 * </ul>
	 */
	class AsyncSpec {

		private final AcpClientTransport transport;

		private Duration requestTimeout = Duration.ofSeconds(30); // Default timeout

		private AcpSchema.ClientCapabilities clientCapabilities;

		private final Map<String, AcpClientSession.RequestHandler<?>> requestHandlers = new HashMap<>();

		private final Map<String, AcpClientSession.NotificationHandler> notificationHandlers = new HashMap<>();

		private final List<Function<AcpSchema.SessionNotification, Mono<Void>>> sessionUpdateConsumers = new ArrayList<>();

		private AsyncSpec(AcpClientTransport transport) {
			Assert.notNull(transport, "Transport must not be null");
			this.transport = transport;
		}

		/**
		 * Sets the duration to wait for agent responses before timing out requests. This
		 * timeout applies to all requests made through the client, including initialize,
		 * prompt, and session operations.
		 * @param requestTimeout The duration to wait before timing out requests. Must not
		 * be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if requestTimeout is null
		 */
		public AsyncSpec requestTimeout(Duration requestTimeout) {
			Assert.notNull(requestTimeout, "Request timeout must not be null");
			this.requestTimeout = requestTimeout;
			return this;
		}

		/**
		 * Sets the client capabilities that will be advertised to the agent during
		 * initialization. Capabilities define what features the client supports, such as
		 * file system operations, terminal access, and authentication methods.
		 * @param clientCapabilities The client capabilities configuration. Must not be
		 * null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if clientCapabilities is null
		 */
		public AsyncSpec clientCapabilities(AcpSchema.ClientCapabilities clientCapabilities) {
			Assert.notNull(clientCapabilities, "Client capabilities must not be null");
			this.clientCapabilities = clientCapabilities;
			return this;
		}

		/**
		 * Adds a handler for file system read requests from the agent. When the agent
		 * needs to read a file, this handler will be invoked with the request parameters.
		 * @param handler The handler function that processes read requests
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if handler is null
		 */
		public AsyncSpec readTextFileHandler(AcpClientSession.RequestHandler<AcpSchema.ReadTextFileResponse> handler) {
			Assert.notNull(handler, "Read text file handler must not be null");
			this.requestHandlers.put(AcpSchema.METHOD_FS_READ_TEXT_FILE, handler);
			return this;
		}

		/**
		 * Adds a handler for file system write requests from the agent. When the agent
		 * needs to write a file, this handler will be invoked with the request
		 * parameters.
		 * @param handler The handler function that processes write requests
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if handler is null
		 */
		public AsyncSpec writeTextFileHandler(
				AcpClientSession.RequestHandler<AcpSchema.WriteTextFileResponse> handler) {
			Assert.notNull(handler, "Write text file handler must not be null");
			this.requestHandlers.put(AcpSchema.METHOD_FS_WRITE_TEXT_FILE, handler);
			return this;
		}

		/**
		 * Adds a handler for permission requests from the agent. When the agent needs
		 * permission for a sensitive operation, this handler will be invoked.
		 * @param handler The handler function that processes permission requests
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if handler is null
		 */
		public AsyncSpec requestPermissionHandler(
				AcpClientSession.RequestHandler<AcpSchema.RequestPermissionResponse> handler) {
			Assert.notNull(handler, "Request permission handler must not be null");
			this.requestHandlers.put(AcpSchema.METHOD_SESSION_REQUEST_PERMISSION, handler);
			return this;
		}

		/**
		 * Adds a consumer to be notified when session update notifications are received
		 * from the agent. Session updates include agent thoughts, message chunks, and
		 * other streaming content during prompt processing.
		 * @param sessionUpdateConsumer A consumer that receives session update
		 * notifications. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if sessionUpdateConsumer is null
		 */
		public AsyncSpec sessionUpdateConsumer(
				Function<AcpSchema.SessionNotification, Mono<Void>> sessionUpdateConsumer) {
			Assert.notNull(sessionUpdateConsumer, "Session update consumer must not be null");
			this.sessionUpdateConsumers.add(sessionUpdateConsumer);
			return this;
		}

		/**
		 * Adds a custom request handler for a specific method. This allows handling
		 * additional agent requests beyond the standard file system and permission
		 * operations.
		 * @param method The method name (e.g., "custom/operation")
		 * @param handler The handler function for this method
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if method or handler is null
		 */
		public AsyncSpec requestHandler(String method, AcpClientSession.RequestHandler<?> handler) {
			Assert.notNull(method, "Method must not be null");
			Assert.notNull(handler, "Handler must not be null");
			this.requestHandlers.put(method, handler);
			return this;
		}

		/**
		 * Adds a custom notification handler for a specific method. This allows handling
		 * additional agent notifications beyond session updates.
		 * @param method The method name (e.g., "custom/notification")
		 * @param handler The handler function for this method
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if method or handler is null
		 */
		public AsyncSpec notificationHandler(String method, AcpClientSession.NotificationHandler handler) {
			Assert.notNull(method, "Method must not be null");
			Assert.notNull(handler, "Handler must not be null");
			this.notificationHandlers.put(method, handler);
			return this;
		}

		/**
		 * Creates an instance of {@link AcpAsyncClient} with the provided configurations
		 * or sensible defaults.
		 * @return a new instance of {@link AcpAsyncClient}
		 */
		public AcpAsyncClient build() {
			// Set up session update notification handler
			if (!sessionUpdateConsumers.isEmpty()) {
				notificationHandlers.put(AcpSchema.METHOD_SESSION_UPDATE, params -> {
					AcpSchema.SessionNotification notification = transport.unmarshalFrom(params,
							new io.modelcontextprotocol.json.TypeRef<AcpSchema.SessionNotification>() {
							});
					logger.debug("Received session update for session: {}", notification.sessionId());

					// Call all registered consumers
					return Mono
						.when(sessionUpdateConsumers.stream().map(consumer -> consumer.apply(notification)).toList());
				});
			}

			// Create session with request and notification handlers
			AcpSession session = new AcpClientSession(requestTimeout, transport, requestHandlers, notificationHandlers);

			return new AcpAsyncClient(session);
		}

	}

	/**
	 * Synchronous client specification. This class follows the builder pattern to
	 * provide a fluent API for setting up synchronous clients with custom configurations.
	 *
	 * <p>
	 * The builder supports configuration of:
	 * <ul>
	 * <li>Transport layer for client-agent communication</li>
	 * <li>Request timeouts for operation boundaries</li>
	 * <li>Client capabilities for feature negotiation</li>
	 * <li>Request handlers for incoming agent requests (file operations, etc.)</li>
	 * <li>Notification handlers for streaming updates</li>
	 * </ul>
	 */
	class SyncSpec {

		private final AcpClientTransport transport;

		private Duration requestTimeout = Duration.ofSeconds(30); // Default timeout

		private AcpSchema.ClientCapabilities clientCapabilities;

		private final Map<String, AcpClientSession.RequestHandler<?>> requestHandlers = new HashMap<>();

		private final Map<String, AcpClientSession.NotificationHandler> notificationHandlers = new HashMap<>();

		private final List<Function<AcpSchema.SessionNotification, Mono<Void>>> sessionUpdateConsumers = new ArrayList<>();

		private SyncSpec(AcpClientTransport transport) {
			Assert.notNull(transport, "Transport must not be null");
			this.transport = transport;
		}

		/**
		 * Sets the duration to wait for agent responses before timing out requests. This
		 * timeout applies to all requests made through the client, including initialize,
		 * prompt, and session operations.
		 * @param requestTimeout The duration to wait before timing out requests. Must not
		 * be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if requestTimeout is null
		 */
		public SyncSpec requestTimeout(Duration requestTimeout) {
			Assert.notNull(requestTimeout, "Request timeout must not be null");
			this.requestTimeout = requestTimeout;
			return this;
		}

		/**
		 * Sets the client capabilities that will be advertised to the agent during
		 * initialization. Capabilities define what features the client supports, such as
		 * file system operations, terminal access, and authentication methods.
		 * @param clientCapabilities The client capabilities configuration. Must not be
		 * null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if clientCapabilities is null
		 */
		public SyncSpec clientCapabilities(AcpSchema.ClientCapabilities clientCapabilities) {
			Assert.notNull(clientCapabilities, "Client capabilities must not be null");
			this.clientCapabilities = clientCapabilities;
			return this;
		}

		/**
		 * Adds a handler for file system read requests from the agent. When the agent
		 * needs to read a file, this handler will be invoked with the request parameters.
		 * @param handler The handler function that processes read requests
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if handler is null
		 */
		public SyncSpec readTextFileHandler(AcpClientSession.RequestHandler<AcpSchema.ReadTextFileResponse> handler) {
			Assert.notNull(handler, "Read text file handler must not be null");
			this.requestHandlers.put(AcpSchema.METHOD_FS_READ_TEXT_FILE, handler);
			return this;
		}

		/**
		 * Adds a handler for file system write requests from the agent. When the agent
		 * needs to write a file, this handler will be invoked with the request
		 * parameters.
		 * @param handler The handler function that processes write requests
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if handler is null
		 */
		public SyncSpec writeTextFileHandler(
				AcpClientSession.RequestHandler<AcpSchema.WriteTextFileResponse> handler) {
			Assert.notNull(handler, "Write text file handler must not be null");
			this.requestHandlers.put(AcpSchema.METHOD_FS_WRITE_TEXT_FILE, handler);
			return this;
		}

		/**
		 * Adds a handler for permission requests from the agent. When the agent needs
		 * permission for a sensitive operation, this handler will be invoked.
		 * @param handler The handler function that processes permission requests
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if handler is null
		 */
		public SyncSpec requestPermissionHandler(
				AcpClientSession.RequestHandler<AcpSchema.RequestPermissionResponse> handler) {
			Assert.notNull(handler, "Request permission handler must not be null");
			this.requestHandlers.put(AcpSchema.METHOD_SESSION_REQUEST_PERMISSION, handler);
			return this;
		}

		/**
		 * Adds a consumer to be notified when session update notifications are received
		 * from the agent. Session updates include agent thoughts, message chunks, and
		 * other streaming content during prompt processing.
		 * @param sessionUpdateConsumer A consumer that receives session update
		 * notifications. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if sessionUpdateConsumer is null
		 */
		public SyncSpec sessionUpdateConsumer(
				Function<AcpSchema.SessionNotification, Mono<Void>> sessionUpdateConsumer) {
			Assert.notNull(sessionUpdateConsumer, "Session update consumer must not be null");
			this.sessionUpdateConsumers.add(sessionUpdateConsumer);
			return this;
		}

		/**
		 * Adds a custom request handler for a specific method. This allows handling
		 * additional agent requests beyond the standard file system and permission
		 * operations.
		 * @param method The method name (e.g., "custom/operation")
		 * @param handler The handler function for this method
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if method or handler is null
		 */
		public SyncSpec requestHandler(String method, AcpClientSession.RequestHandler<?> handler) {
			Assert.notNull(method, "Method must not be null");
			Assert.notNull(handler, "Handler must not be null");
			this.requestHandlers.put(method, handler);
			return this;
		}

		/**
		 * Adds a custom notification handler for a specific method. This allows handling
		 * additional agent notifications beyond session updates.
		 * @param method The method name (e.g., "custom/notification")
		 * @param handler The handler function for this method
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if method or handler is null
		 */
		public SyncSpec notificationHandler(String method, AcpClientSession.NotificationHandler handler) {
			Assert.notNull(method, "Method must not be null");
			Assert.notNull(handler, "Handler must not be null");
			this.notificationHandlers.put(method, handler);
			return this;
		}

		/**
		 * Creates an instance of {@link AcpSyncClient} with the provided configurations
		 * or sensible defaults.
		 * @return a new instance of {@link AcpSyncClient}
		 */
		public AcpSyncClient build() {
			// Set up session update notification handler
			if (!sessionUpdateConsumers.isEmpty()) {
				notificationHandlers.put(AcpSchema.METHOD_SESSION_UPDATE, params -> {
					AcpSchema.SessionNotification notification = transport.unmarshalFrom(params,
							new io.modelcontextprotocol.json.TypeRef<AcpSchema.SessionNotification>() {
							});
					logger.debug("Received session update for session: {}", notification.sessionId());

					// Call all registered consumers
					return Mono
						.when(sessionUpdateConsumers.stream().map(consumer -> consumer.apply(notification)).toList());
				});
			}

			// Create session with request and notification handlers
			AcpSession session = new AcpClientSession(requestTimeout, transport, requestHandlers, notificationHandlers);

			return new AcpSyncClient(new AcpAsyncClient(session));
		}

	}

}
