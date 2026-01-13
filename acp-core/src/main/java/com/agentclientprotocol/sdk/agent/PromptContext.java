/*
 * Copyright 2025-2025 the original author or authors.
 */

package com.agentclientprotocol.sdk.agent;

import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import reactor.core.publisher.Mono;

/**
 * Context provided to prompt handlers for accessing agent capabilities.
 *
 * <p>
 * This interface provides prompt handlers with everything they need to:
 * <ul>
 * <li>Send session updates back to the client</li>
 * <li>Request file operations from the client</li>
 * <li>Request permissions from the client</li>
 * <li>Create and manage terminals</li>
 * <li>Query client capabilities</li>
 * </ul>
 *
 * <p>
 * This follows the MCP SDK's "Exchange" pattern where handlers receive
 * a context object with all necessary capabilities, eliminating the need
 * for external references to the agent instance.
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * agent.promptHandler((request, context) -> {
 *     // Send an update
 *     context.sendUpdate(sessionId, update);
 *
 *     // Read a file (if client supports it)
 *     if (context.getClientCapabilities().supportsReadTextFile()) {
 *         var content = context.readTextFile(new ReadTextFileRequest(...)).block();
 *     }
 *
 *     // Request permission
 *     var permission = context.requestPermission(new RequestPermissionRequest(...));
 *
 *     return Mono.just(new PromptResponse(StopReason.END_TURN));
 * });
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.9.1
 * @see AcpAgent.PromptHandler
 * @see SyncPromptContext
 */
public interface PromptContext {

	// ========================================================================
	// Session Updates
	// ========================================================================

	/**
	 * Sends a session update notification to the client.
	 * Used for streaming updates during prompt processing.
	 * @param sessionId The session ID
	 * @param update The session update to send
	 * @return A Mono that completes when the notification is sent
	 */
	Mono<Void> sendUpdate(String sessionId, AcpSchema.SessionUpdate update);

	// ========================================================================
	// File System Operations
	// ========================================================================

	/**
	 * Requests the client to read a text file.
	 * @param request The read file request
	 * @return A Mono containing the file content
	 * @throws com.agentclientprotocol.sdk.error.AcpCapabilityException if client doesn't support file reading
	 */
	Mono<AcpSchema.ReadTextFileResponse> readTextFile(AcpSchema.ReadTextFileRequest request);

	/**
	 * Requests the client to write a text file.
	 * @param request The write file request
	 * @return A Mono that completes when the file is written
	 * @throws com.agentclientprotocol.sdk.error.AcpCapabilityException if client doesn't support file writing
	 */
	Mono<AcpSchema.WriteTextFileResponse> writeTextFile(AcpSchema.WriteTextFileRequest request);

	// ========================================================================
	// Permission Requests
	// ========================================================================

	/**
	 * Requests permission from the client for a sensitive operation.
	 * @param request The permission request
	 * @return A Mono containing the permission response
	 */
	Mono<AcpSchema.RequestPermissionResponse> requestPermission(AcpSchema.RequestPermissionRequest request);

	// ========================================================================
	// Terminal Operations
	// ========================================================================

	/**
	 * Requests the client to create a terminal.
	 * @param request The create terminal request
	 * @return A Mono containing the terminal ID
	 * @throws com.agentclientprotocol.sdk.error.AcpCapabilityException if client doesn't support terminals
	 */
	Mono<AcpSchema.CreateTerminalResponse> createTerminal(AcpSchema.CreateTerminalRequest request);

	/**
	 * Requests terminal output from the client.
	 * @param request The terminal output request
	 * @return A Mono containing the terminal output
	 */
	Mono<AcpSchema.TerminalOutputResponse> getTerminalOutput(AcpSchema.TerminalOutputRequest request);

	/**
	 * Requests the client to release a terminal.
	 * @param request The release terminal request
	 * @return A Mono that completes when the terminal is released
	 */
	Mono<AcpSchema.ReleaseTerminalResponse> releaseTerminal(AcpSchema.ReleaseTerminalRequest request);

	/**
	 * Waits for a terminal to exit.
	 * @param request The wait for exit request
	 * @return A Mono containing the exit status
	 */
	Mono<AcpSchema.WaitForTerminalExitResponse> waitForTerminalExit(AcpSchema.WaitForTerminalExitRequest request);

	/**
	 * Requests the client to kill a terminal.
	 * @param request The kill terminal request
	 * @return A Mono that completes when the terminal is killed
	 */
	Mono<AcpSchema.KillTerminalCommandResponse> killTerminal(AcpSchema.KillTerminalCommandRequest request);

	// ========================================================================
	// Client Capabilities
	// ========================================================================

	/**
	 * Returns the capabilities negotiated with the client during initialization.
	 *
	 * <p>
	 * Use this to check what features the client supports before calling
	 * methods like {@link #readTextFile} or {@link #createTerminal}.
	 *
	 * @return the negotiated client capabilities, or null if not yet initialized
	 */
	NegotiatedCapabilities getClientCapabilities();

}
