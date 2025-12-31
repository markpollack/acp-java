/*
 * Copyright 2025-2025 the original author or authors.
 */

package com.agentclientprotocol.sdk;

import java.time.Duration;
import java.util.List;

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

}
