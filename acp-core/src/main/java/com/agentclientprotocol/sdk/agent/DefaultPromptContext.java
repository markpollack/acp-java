/*
 * Copyright 2025-2025 the original author or authors.
 */

package com.agentclientprotocol.sdk.agent;

import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link PromptContext} that delegates to an {@link AcpAsyncAgent}.
 *
 * <p>
 * This class is created internally by {@link DefaultAcpAsyncAgent} and passed to prompt handlers.
 * It provides a clean interface for handlers to access all agent capabilities without needing
 * a direct reference to the agent instance.
 *
 * @author Mark Pollack
 * @since 0.9.1
 */
class DefaultPromptContext implements PromptContext {

	private final AcpAsyncAgent agent;

	/**
	 * Creates a new prompt context wrapping the given agent.
	 * @param agent The agent to delegate to
	 */
	DefaultPromptContext(AcpAsyncAgent agent) {
		this.agent = agent;
	}

	@Override
	public Mono<Void> sendUpdate(String sessionId, AcpSchema.SessionUpdate update) {
		return agent.sendSessionUpdate(sessionId, update);
	}

	@Override
	public Mono<AcpSchema.ReadTextFileResponse> readTextFile(AcpSchema.ReadTextFileRequest request) {
		return agent.readTextFile(request);
	}

	@Override
	public Mono<AcpSchema.WriteTextFileResponse> writeTextFile(AcpSchema.WriteTextFileRequest request) {
		return agent.writeTextFile(request);
	}

	@Override
	public Mono<AcpSchema.RequestPermissionResponse> requestPermission(AcpSchema.RequestPermissionRequest request) {
		return agent.requestPermission(request);
	}

	@Override
	public Mono<AcpSchema.CreateTerminalResponse> createTerminal(AcpSchema.CreateTerminalRequest request) {
		return agent.createTerminal(request);
	}

	@Override
	public Mono<AcpSchema.TerminalOutputResponse> getTerminalOutput(AcpSchema.TerminalOutputRequest request) {
		return agent.getTerminalOutput(request);
	}

	@Override
	public Mono<AcpSchema.ReleaseTerminalResponse> releaseTerminal(AcpSchema.ReleaseTerminalRequest request) {
		return agent.releaseTerminal(request);
	}

	@Override
	public Mono<AcpSchema.WaitForTerminalExitResponse> waitForTerminalExit(
			AcpSchema.WaitForTerminalExitRequest request) {
		return agent.waitForTerminalExit(request);
	}

	@Override
	public Mono<AcpSchema.KillTerminalCommandResponse> killTerminal(AcpSchema.KillTerminalCommandRequest request) {
		return agent.killTerminal(request);
	}

	@Override
	public NegotiatedCapabilities getClientCapabilities() {
		return agent.getClientCapabilities();
	}

}
