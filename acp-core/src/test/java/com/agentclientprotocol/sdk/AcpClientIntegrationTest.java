/*
 * Copyright 2025-2025 the original author or authors.
 */

package com.agentclientprotocol.sdk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.modelcontextprotocol.json.McpJsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.agentclientprotocol.sdk.client.AcpAsyncClient;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for ACP protocol using the high-level AcpAsyncClient. Requires gemini
 * CLI with ACP support.
 *
 * @author Mark Pollack
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class AcpClientIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(AcpClientIntegrationTest.class);

	@Test
	void testGeminiAgentWithHighLevelClient() throws Exception {
		logger.info("Starting Gemini ACP integration test with high-level client");

		// Create agent parameters for gemini
		AgentParameters params = AgentParameters.builder("gemini").arg("--experimental-acp").build();

		// Create JSON mapper - reuse MCP's default configured mapper
		McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

		// Create transport
		StdioAcpClientTransport transport = new StdioAcpClientTransport(params, jsonMapper);

		// Build client with session update consumer
		AcpAsyncClient client = AcpClient.async(transport)
			.requestTimeout(Duration.ofSeconds(30))
			.sessionUpdateConsumer(notification -> {
				logger.info("Received session update: sessionId={}, updateType={}", notification.sessionId(),
						notification.update().getClass().getSimpleName());
				return Mono.empty();
			})
			.build();

		try {
			logger.info("Sending initialize request");

			// Initialize
			AcpSchema.InitializeResponse initResponse = client
				.initialize(new AcpSchema.InitializeRequest(1, new AcpSchema.ClientCapabilities()))
				.block();

			assertThat(initResponse).isNotNull();
			assertThat(initResponse.protocolVersion()).isEqualTo(1);
			logger.info("Initialize response: protocolVersion={}, agentCapabilities={}", initResponse.protocolVersion(),
					initResponse.agentCapabilities());

			logger.info("Sending session/new request");

			// Create session
			AcpSchema.NewSessionResponse sessionResponse = client
				.newSession(new AcpSchema.NewSessionRequest(System.getProperty("user.dir"), List.of()))
				.block();

			assertThat(sessionResponse).isNotNull();
			assertThat(sessionResponse.sessionId()).isNotNull();
			logger.info("New session response: sessionId={}", sessionResponse.sessionId());

			logger.info("Sending session/prompt request");

			// Send prompt
			AcpSchema.PromptResponse promptResponse = client
				.prompt(new AcpSchema.PromptRequest(sessionResponse.sessionId(),
						List.of(new AcpSchema.TextContent("What is 2+2?"))))
				.block();

			assertThat(promptResponse).isNotNull();
			assertThat(promptResponse.stopReason()).isNotNull();
			logger.info("Prompt response: stopReason={}", promptResponse.stopReason());

			// Give some time for any final notifications
			Thread.sleep(1000);

			logger.info("Test completed successfully");
		}
		finally {
			logger.info("Closing client and transport");
			client.closeGracefully().block();
			transport.closeGracefully().block();
		}
	}

	@Test
	void testToolCallWithFileRead() throws Exception {
		logger.info("Starting Gemini tool call integration test");

		// Track all SessionUpdate types received
		Map<String, AtomicInteger> updateTypeCounts = new ConcurrentHashMap<>();
		List<AcpSchema.SessionUpdate> allUpdates = Collections.synchronizedList(new ArrayList<>());
		AtomicInteger permissionRequestCount = new AtomicInteger(0);

		// Create agent parameters for gemini
		AgentParameters params = AgentParameters.builder("gemini").arg("--experimental-acp").build();
		McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
		StdioAcpClientTransport transport = new StdioAcpClientTransport(params, jsonMapper);

		// Build client with handlers for session updates and permissions
		AcpAsyncClient client = AcpClient.async(transport)
			.requestTimeout(Duration.ofSeconds(60))
			.sessionUpdateConsumer(notification -> {
				AcpSchema.SessionUpdate update = notification.update();
				String updateType = update.getClass().getSimpleName();
				updateTypeCounts.computeIfAbsent(updateType, k -> new AtomicInteger(0)).incrementAndGet();
				allUpdates.add(update);

				// Log details for tool calls
				if (update instanceof AcpSchema.ToolCall toolCall) {
					logger.info("Tool call: id={}, title={}, kind={}, status={}", toolCall.toolCallId(),
							toolCall.title(), toolCall.kind(), toolCall.status());
				}
				else if (update instanceof AcpSchema.ToolCallUpdateNotification toolUpdate) {
					logger.info("Tool update: id={}, status={}", toolUpdate.toolCallId(), toolUpdate.status());
				}
				else if (update instanceof AcpSchema.AgentMessageChunk chunk) {
					if (chunk.content() instanceof AcpSchema.TextContent text) {
						logger.info("Agent message: {}", text.text().substring(0, Math.min(100, text.text().length())));
					}
				}
				else {
					logger.info("Session update: {}", updateType);
				}
				return Mono.empty();
			})
			// Auto-allow permission requests - using typed handler (no manual unmarshalling needed)
			.requestPermissionHandler((AcpSchema.RequestPermissionRequest request) -> {
				permissionRequestCount.incrementAndGet();
				logger.info("Permission request for tool: {}", request.toolCall().toolCallId());

				// Find the allow_once option or use first option
				String optionId = request.options().stream()
					.filter(opt -> opt.kind() == AcpSchema.PermissionOptionKind.ALLOW_ONCE)
					.findFirst()
					.map(AcpSchema.PermissionOption::optionId)
					.orElse(request.options().get(0).optionId());

				logger.info("Auto-allowing with option: {}", optionId);
				return Mono.just(new AcpSchema.RequestPermissionResponse(new AcpSchema.PermissionSelected(optionId)));
			})
			// Handle file read requests - using typed handler (no manual unmarshalling needed)
			.readTextFileHandler((AcpSchema.ReadTextFileRequest request) -> {
				logger.info("File read request: path={}", request.path());
				try {
					String content = Files.readString(Path.of(request.path()));
					return Mono.just(new AcpSchema.ReadTextFileResponse(content));
				}
				catch (IOException e) {
					logger.error("Failed to read file: {}", request.path(), e);
					return Mono.error(e);
				}
			})
			.build();

		try {
			// Initialize
			AcpSchema.InitializeResponse initResponse = client
				.initialize(new AcpSchema.InitializeRequest(1, new AcpSchema.ClientCapabilities(
						new AcpSchema.FileSystemCapability(true, false), // read only
						true // permissions
				)))
				.block();

			assertThat(initResponse).isNotNull();
			logger.info("Initialized with agent capabilities: {}", initResponse.agentCapabilities());

			// Create session
			AcpSchema.NewSessionResponse sessionResponse = client
				.newSession(new AcpSchema.NewSessionRequest(System.getProperty("user.dir"), List.of()))
				.block();

			assertThat(sessionResponse).isNotNull();
			String sessionId = sessionResponse.sessionId();
			logger.info("Created session: {}", sessionId);

			// Send a prompt that should trigger tool calls
			logger.info("Sending prompt to trigger tool call...");
			AcpSchema.PromptResponse promptResponse = client.prompt(new AcpSchema.PromptRequest(sessionId,
					List.of(new AcpSchema.TextContent("Read the pom.xml file and tell me the artifact ID only."))))
				.block();

			assertThat(promptResponse).isNotNull();
			logger.info("Prompt completed with stopReason: {}", promptResponse.stopReason());

			// Wait for any remaining updates
			Thread.sleep(2000);

			// Log summary
			logger.info("=== Session Update Summary ===");
			updateTypeCounts.forEach((type, count) -> logger.info("  {}: {}", type, count.get()));
			logger.info("Total updates received: {}", allUpdates.size());
			logger.info("Permission requests: {}", permissionRequestCount.get());

			// Verify we received expected update types
			assertThat(allUpdates).isNotEmpty();

			// Check for tool calls (either ToolCall or ToolCallUpdateNotification)
			boolean hasToolCalls = allUpdates.stream()
				.anyMatch(u -> u instanceof AcpSchema.ToolCall || u instanceof AcpSchema.ToolCallUpdateNotification);
			logger.info("Received tool calls: {}", hasToolCalls);

			// Check for agent messages
			boolean hasAgentMessages = allUpdates.stream().anyMatch(u -> u instanceof AcpSchema.AgentMessageChunk);
			assertThat(hasAgentMessages).as("Should receive agent message chunks").isTrue();

			logger.info("Tool call integration test completed successfully");
		}
		finally {
			client.closeGracefully().block();
			transport.closeGracefully().block();
		}
	}

}
