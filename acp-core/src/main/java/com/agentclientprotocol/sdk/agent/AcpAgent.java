/*
 * Copyright 2025-2025 the original author or authors.
 */

package com.agentclientprotocol.sdk.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.agentclientprotocol.sdk.spec.AcpAgentSession;
import com.agentclientprotocol.sdk.spec.AcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Factory class for creating Agent Client Protocol (ACP) agents. ACP agents
 * provide autonomous coding capabilities to clients (such as code editors)
 * through a standardized interface.
 *
 * <p>
 * This class serves as the main entry point for implementing ACP-compliant agents,
 * implementing the agent-side of the ACP specification. The protocol follows a
 * client-agent architecture where:
 * <ul>
 * <li>The agent (this implementation) responds to client requests and sends updates</li>
 * <li>The client connects to the agent and sends prompts</li>
 * <li>Communication occurs through a transport layer (e.g., stdio) using JSON-RPC 2.0</li>
 * </ul>
 *
 * <p>
 * The class provides factory methods to create either:
 * <ul>
 * <li>{@link AcpAsyncAgent} for non-blocking operations with Mono/Flux responses</li>
 * <li>{@link AcpSyncAgent} for blocking operations with direct responses</li>
 * </ul>
 *
 * <p>
 * Example of creating a basic asynchronous agent:
 *
 * <pre>{@code
 * AcpAsyncAgent agent = AcpAgent.async(transport)
 *     .requestTimeout(Duration.ofSeconds(30))
 *     .agentInfo(new AcpSchema.AgentCapabilities(true, null, null))
 *     .initializeHandler(request -> {
 *         return Mono.just(new AcpSchema.InitializeResponse(1,
 *             new AcpSchema.AgentCapabilities(), List.of()));
 *     })
 *     .newSessionHandler(request -> {
 *         return Mono.just(new AcpSchema.NewSessionResponse(
 *             "session-1", null, null));
 *     })
 *     .promptHandler((request, updater) -> {
 *         updater.sendUpdate(new AcpSchema.AgentMessageChunk(
 *             "agent_message_chunk",
 *             new AcpSchema.TextContent("Working on it...")));
 *         return Mono.just(new AcpSchema.PromptResponse(AcpSchema.StopReason.END_TURN));
 *     })
 *     .build();
 *
 * agent.start().block();
 * }</pre>
 *
 * <p>
 * The agent supports:
 * <ul>
 * <li>Protocol version negotiation and capability exchange</li>
 * <li>Optional authentication with various methods</li>
 * <li>Session creation and management (including loadSession)</li>
 * <li>Prompt processing with streaming session updates</li>
 * <li>File system requests to client (read/write)</li>
 * <li>Permission requests for sensitive operations</li>
 * <li>Terminal operations for command execution</li>
 * </ul>
 *
 * @author Mark Pollack
 * @see AcpAsyncAgent
 * @see AcpAgentTransport
 */
public interface AcpAgent {

	Logger logger = LoggerFactory.getLogger(AcpAgent.class);

	/**
	 * Default request timeout duration.
	 */
	Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

	/**
	 * Start building a synchronous ACP agent with the specified transport layer.
	 * The synchronous agent provides blocking operations for simpler implementations.
	 * @param transport The transport layer to use for communication
	 * @return A builder for configuring the synchronous agent
	 */
	static SyncAgentBuilder sync(AcpAgentTransport transport) {
		return new SyncAgentBuilder(transport);
	}

	/**
	 * Start building an asynchronous ACP agent with the specified transport layer.
	 * The asynchronous agent provides non-blocking operations with Mono/Flux responses.
	 * @param transport The transport layer to use for communication
	 * @return A builder for configuring the asynchronous agent
	 */
	static AsyncAgentBuilder async(AcpAgentTransport transport) {
		return new AsyncAgentBuilder(transport);
	}

	/**
	 * Functional interface for handling initialize requests.
	 */
	@FunctionalInterface
	interface InitializeHandler {

		Mono<AcpSchema.InitializeResponse> handle(AcpSchema.InitializeRequest request);

	}

	/**
	 * Functional interface for handling authenticate requests.
	 */
	@FunctionalInterface
	interface AuthenticateHandler {

		Mono<AcpSchema.AuthenticateResponse> handle(AcpSchema.AuthenticateRequest request);

	}

	/**
	 * Functional interface for handling new session requests.
	 */
	@FunctionalInterface
	interface NewSessionHandler {

		Mono<AcpSchema.NewSessionResponse> handle(AcpSchema.NewSessionRequest request);

	}

	/**
	 * Functional interface for handling load session requests.
	 */
	@FunctionalInterface
	interface LoadSessionHandler {

		Mono<AcpSchema.LoadSessionResponse> handle(AcpSchema.LoadSessionRequest request);

	}

	/**
	 * Interface for sending session updates during prompt processing.
	 */
	interface SessionUpdateSender {

		/**
		 * Sends a session update notification to the client.
		 * @param sessionId The session ID
		 * @param update The session update to send
		 * @return A Mono that completes when the notification is sent
		 */
		Mono<Void> sendUpdate(String sessionId, AcpSchema.SessionUpdate update);

	}

	/**
	 * Functional interface for handling prompt requests with streaming updates.
	 */
	@FunctionalInterface
	interface PromptHandler {

		/**
		 * Handles a prompt request, optionally sending session updates during processing.
		 * @param request The prompt request
		 * @param updater Interface for sending session updates
		 * @return A Mono containing the prompt response
		 */
		Mono<AcpSchema.PromptResponse> handle(AcpSchema.PromptRequest request, SessionUpdateSender updater);

	}

	/**
	 * Functional interface for handling set session mode requests.
	 */
	@FunctionalInterface
	interface SetSessionModeHandler {

		Mono<AcpSchema.SetSessionModeResponse> handle(AcpSchema.SetSessionModeRequest request);

	}

	/**
	 * Functional interface for handling set session model requests.
	 */
	@FunctionalInterface
	interface SetSessionModelHandler {

		Mono<AcpSchema.SetSessionModelResponse> handle(AcpSchema.SetSessionModelRequest request);

	}

	/**
	 * Functional interface for handling cancel notifications.
	 */
	@FunctionalInterface
	interface CancelHandler {

		Mono<Void> handle(AcpSchema.CancelNotification notification);

	}

	/**
	 * Builder for creating asynchronous ACP agents.
	 */
	class AsyncAgentBuilder {

		private final AcpAgentTransport transport;

		private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

		private InitializeHandler initializeHandler;

		private AuthenticateHandler authenticateHandler;

		private NewSessionHandler newSessionHandler;

		private LoadSessionHandler loadSessionHandler;

		private PromptHandler promptHandler;

		private SetSessionModeHandler setSessionModeHandler;

		private SetSessionModelHandler setSessionModelHandler;

		private CancelHandler cancelHandler;

		AsyncAgentBuilder(AcpAgentTransport transport) {
			Assert.notNull(transport, "Transport must not be null");
			this.transport = transport;
		}

		/**
		 * Sets the timeout for requests sent to the client.
		 * @param timeout The request timeout duration
		 * @return This builder for chaining
		 */
		public AsyncAgentBuilder requestTimeout(Duration timeout) {
			Assert.notNull(timeout, "Timeout must not be null");
			this.requestTimeout = timeout;
			return this;
		}

		/**
		 * Sets the handler for initialize requests.
		 * @param handler The initialize handler
		 * @return This builder for chaining
		 */
		public AsyncAgentBuilder initializeHandler(InitializeHandler handler) {
			this.initializeHandler = handler;
			return this;
		}

		/**
		 * Sets the handler for authenticate requests.
		 * @param handler The authenticate handler
		 * @return This builder for chaining
		 */
		public AsyncAgentBuilder authenticateHandler(AuthenticateHandler handler) {
			this.authenticateHandler = handler;
			return this;
		}

		/**
		 * Sets the handler for new session requests.
		 * @param handler The new session handler
		 * @return This builder for chaining
		 */
		public AsyncAgentBuilder newSessionHandler(NewSessionHandler handler) {
			this.newSessionHandler = handler;
			return this;
		}

		/**
		 * Sets the handler for load session requests.
		 * @param handler The load session handler
		 * @return This builder for chaining
		 */
		public AsyncAgentBuilder loadSessionHandler(LoadSessionHandler handler) {
			this.loadSessionHandler = handler;
			return this;
		}

		/**
		 * Sets the handler for prompt requests.
		 * @param handler The prompt handler
		 * @return This builder for chaining
		 */
		public AsyncAgentBuilder promptHandler(PromptHandler handler) {
			this.promptHandler = handler;
			return this;
		}

		/**
		 * Sets the handler for set session mode requests.
		 * @param handler The set session mode handler
		 * @return This builder for chaining
		 */
		public AsyncAgentBuilder setSessionModeHandler(SetSessionModeHandler handler) {
			this.setSessionModeHandler = handler;
			return this;
		}

		/**
		 * Sets the handler for set session model requests.
		 * @param handler The set session model handler
		 * @return This builder for chaining
		 */
		public AsyncAgentBuilder setSessionModelHandler(SetSessionModelHandler handler) {
			this.setSessionModelHandler = handler;
			return this;
		}

		/**
		 * Sets the handler for cancel notifications.
		 * @param handler The cancel handler
		 * @return This builder for chaining
		 */
		public AsyncAgentBuilder cancelHandler(CancelHandler handler) {
			this.cancelHandler = handler;
			return this;
		}

		/**
		 * Builds the asynchronous ACP agent.
		 * @return A new AcpAsyncAgent instance
		 */
		public AcpAsyncAgent build() {
			return new DefaultAcpAsyncAgent(transport, requestTimeout, initializeHandler, authenticateHandler,
					newSessionHandler, loadSessionHandler, promptHandler, setSessionModeHandler, setSessionModelHandler,
					cancelHandler);
		}

	}

	/**
	 * Builder for creating synchronous ACP agents.
	 */
	class SyncAgentBuilder {

		private final AsyncAgentBuilder asyncBuilder;

		SyncAgentBuilder(AcpAgentTransport transport) {
			this.asyncBuilder = new AsyncAgentBuilder(transport);
		}

		/**
		 * Sets the timeout for requests sent to the client.
		 * @param timeout The request timeout duration
		 * @return This builder for chaining
		 */
		public SyncAgentBuilder requestTimeout(Duration timeout) {
			asyncBuilder.requestTimeout(timeout);
			return this;
		}

		/**
		 * Sets the handler for initialize requests.
		 * @param handler The initialize handler
		 * @return This builder for chaining
		 */
		public SyncAgentBuilder initializeHandler(InitializeHandler handler) {
			asyncBuilder.initializeHandler(handler);
			return this;
		}

		/**
		 * Sets the handler for authenticate requests.
		 * @param handler The authenticate handler
		 * @return This builder for chaining
		 */
		public SyncAgentBuilder authenticateHandler(AuthenticateHandler handler) {
			asyncBuilder.authenticateHandler(handler);
			return this;
		}

		/**
		 * Sets the handler for new session requests.
		 * @param handler The new session handler
		 * @return This builder for chaining
		 */
		public SyncAgentBuilder newSessionHandler(NewSessionHandler handler) {
			asyncBuilder.newSessionHandler(handler);
			return this;
		}

		/**
		 * Sets the handler for load session requests.
		 * @param handler The load session handler
		 * @return This builder for chaining
		 */
		public SyncAgentBuilder loadSessionHandler(LoadSessionHandler handler) {
			asyncBuilder.loadSessionHandler(handler);
			return this;
		}

		/**
		 * Sets the handler for prompt requests.
		 * @param handler The prompt handler
		 * @return This builder for chaining
		 */
		public SyncAgentBuilder promptHandler(PromptHandler handler) {
			asyncBuilder.promptHandler(handler);
			return this;
		}

		/**
		 * Sets the handler for set session mode requests.
		 * @param handler The set session mode handler
		 * @return This builder for chaining
		 */
		public SyncAgentBuilder setSessionModeHandler(SetSessionModeHandler handler) {
			asyncBuilder.setSessionModeHandler(handler);
			return this;
		}

		/**
		 * Sets the handler for set session model requests.
		 * @param handler The set session model handler
		 * @return This builder for chaining
		 */
		public SyncAgentBuilder setSessionModelHandler(SetSessionModelHandler handler) {
			asyncBuilder.setSessionModelHandler(handler);
			return this;
		}

		/**
		 * Sets the handler for cancel notifications.
		 * @param handler The cancel handler
		 * @return This builder for chaining
		 */
		public SyncAgentBuilder cancelHandler(CancelHandler handler) {
			asyncBuilder.cancelHandler(handler);
			return this;
		}

		/**
		 * Builds the synchronous ACP agent.
		 * @return A new AcpSyncAgent instance
		 */
		public AcpSyncAgent build() {
			return new AcpSyncAgent(asyncBuilder.build());
		}

	}

}
