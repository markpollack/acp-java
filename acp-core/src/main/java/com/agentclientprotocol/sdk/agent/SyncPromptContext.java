/*
 * Copyright 2025-2025 the original author or authors.
 */

package com.agentclientprotocol.sdk.agent;

import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * Synchronous context provided to prompt handlers for accessing agent capabilities.
 *
 * <p>
 * This is the synchronous equivalent of {@link PromptContext}, providing blocking
 * methods for use with {@link AcpAgent.SyncPromptHandler}.
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * AcpAgent.sync(transport)
 *     .promptHandler((request, context) -> {
 *         // Send an update (blocks until sent)
 *         context.sendUpdate(sessionId, update);
 *
 *         // Read a file (blocks until complete)
 *         var content = context.readTextFile(new ReadTextFileRequest(...));
 *
 *         // Request permission (blocks until user responds)
 *         var permission = context.requestPermission(new RequestPermissionRequest(...));
 *
 *         return new PromptResponse(StopReason.END_TURN);
 *     })
 *     .build();
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.9.1
 * @see PromptContext
 * @see AcpAgent.SyncPromptHandler
 */
public interface SyncPromptContext {

	// ========================================================================
	// Session Updates
	// ========================================================================

	/**
	 * Sends a session update notification to the client.
	 * Blocks until the notification is sent.
	 * @param sessionId The session ID
	 * @param update The session update to send
	 */
	void sendUpdate(String sessionId, AcpSchema.SessionUpdate update);

	// ========================================================================
	// File System Operations
	// ========================================================================

	/**
	 * Requests the client to read a text file.
	 * Blocks until the response is received.
	 * @param request The read file request
	 * @return The file content
	 * @throws com.agentclientprotocol.sdk.error.AcpCapabilityException if client doesn't support file reading
	 */
	AcpSchema.ReadTextFileResponse readTextFile(AcpSchema.ReadTextFileRequest request);

	/**
	 * Requests the client to write a text file.
	 * Blocks until the write is complete.
	 * @param request The write file request
	 * @return The write response
	 * @throws com.agentclientprotocol.sdk.error.AcpCapabilityException if client doesn't support file writing
	 */
	AcpSchema.WriteTextFileResponse writeTextFile(AcpSchema.WriteTextFileRequest request);

	// ========================================================================
	// Permission Requests
	// ========================================================================

	/**
	 * Requests permission from the client for a sensitive operation.
	 * Blocks until the user responds.
	 * @param request The permission request
	 * @return The permission response
	 */
	AcpSchema.RequestPermissionResponse requestPermission(AcpSchema.RequestPermissionRequest request);

	// ========================================================================
	// Terminal Operations
	// ========================================================================

	/**
	 * Requests the client to create a terminal.
	 * Blocks until the terminal is created.
	 * @param request The create terminal request
	 * @return The terminal ID response
	 * @throws com.agentclientprotocol.sdk.error.AcpCapabilityException if client doesn't support terminals
	 */
	AcpSchema.CreateTerminalResponse createTerminal(AcpSchema.CreateTerminalRequest request);

	/**
	 * Requests terminal output from the client.
	 * Blocks until the output is received.
	 * @param request The terminal output request
	 * @return The terminal output
	 */
	AcpSchema.TerminalOutputResponse getTerminalOutput(AcpSchema.TerminalOutputRequest request);

	/**
	 * Requests the client to release a terminal.
	 * Blocks until the terminal is released.
	 * @param request The release terminal request
	 * @return The release response
	 */
	AcpSchema.ReleaseTerminalResponse releaseTerminal(AcpSchema.ReleaseTerminalRequest request);

	/**
	 * Waits for a terminal to exit.
	 * Blocks until the terminal exits.
	 * @param request The wait for exit request
	 * @return The exit status
	 */
	AcpSchema.WaitForTerminalExitResponse waitForTerminalExit(AcpSchema.WaitForTerminalExitRequest request);

	/**
	 * Requests the client to kill a terminal.
	 * Blocks until the terminal is killed.
	 * @param request The kill terminal request
	 * @return The kill response
	 */
	AcpSchema.KillTerminalCommandResponse killTerminal(AcpSchema.KillTerminalCommandRequest request);

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
