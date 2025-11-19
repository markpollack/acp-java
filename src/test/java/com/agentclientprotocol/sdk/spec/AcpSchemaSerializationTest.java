/*
 * Copyright 2025-2025 the original author or authors.
 */

package com.agentclientprotocol.sdk.spec;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.agentclientprotocol.sdk.AcpTestFixtures;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests JSON serialization and deserialization of ACP schema types.
 *
 * <p>
 * Verifies that all ACP protocol types can be correctly serialized to JSON and
 * deserialized back to Java objects with all fields preserved.
 * </p>
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 */
class AcpSchemaSerializationTest {

	private final McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

	// ---------------------------
	// Request/Response Serialization
	// ---------------------------

	@Test
	void initializeRequestSerialization() throws IOException {
		AcpSchema.InitializeRequest request = AcpTestFixtures.createInitializeRequest();

		String json = jsonMapper.writeValueAsString(request);
		AcpSchema.InitializeRequest deserialized = jsonMapper.readValue(json,
				new TypeRef<AcpSchema.InitializeRequest>() {
				});

		assertThat(deserialized.protocolVersion()).isEqualTo(1);
		assertThat(deserialized.clientCapabilities()).isNotNull();
	}

	@Test
	void initializeResponseSerialization() throws IOException {
		AcpSchema.InitializeResponse response = AcpTestFixtures.createInitializeResponse();

		String json = jsonMapper.writeValueAsString(response);
		AcpSchema.InitializeResponse deserialized = jsonMapper.readValue(json,
				new TypeRef<AcpSchema.InitializeResponse>() {
				});

		assertThat(deserialized.protocolVersion()).isEqualTo(1);
		assertThat(deserialized.agentCapabilities()).isNotNull();
		assertThat(deserialized.authMethods()).isEmpty();
	}

	@Test
	void newSessionRequestSerialization() throws IOException {
		AcpSchema.NewSessionRequest request = AcpTestFixtures.createNewSessionRequest();

		String json = jsonMapper.writeValueAsString(request);
		AcpSchema.NewSessionRequest deserialized = jsonMapper.readValue(json,
				new TypeRef<AcpSchema.NewSessionRequest>() {
				});

		assertThat(deserialized.cwd()).isEqualTo("/test/workspace");
		assertThat(deserialized.mcpServers()).isEmpty();
	}

	@Test
	void newSessionResponseSerialization() throws IOException {
		AcpSchema.NewSessionResponse response = AcpTestFixtures.createNewSessionResponse();

		String json = jsonMapper.writeValueAsString(response);
		AcpSchema.NewSessionResponse deserialized = jsonMapper.readValue(json,
				new TypeRef<AcpSchema.NewSessionResponse>() {
				});

		assertThat(deserialized.sessionId()).isEqualTo("test-session-id");
		assertThat(deserialized.modes()).isNotNull();
		assertThat(deserialized.models()).isNotNull();
	}

	@Test
	void promptRequestSerialization() throws IOException {
		AcpSchema.PromptRequest request = AcpTestFixtures.createPromptRequest();

		String json = jsonMapper.writeValueAsString(request);
		AcpSchema.PromptRequest deserialized = jsonMapper.readValue(json, new TypeRef<AcpSchema.PromptRequest>() {
		});

		assertThat(deserialized.sessionId()).isEqualTo("test-session-id");
		assertThat(deserialized.prompt()).hasSize(1);
	}

	@Test
	void promptResponseSerialization() throws IOException {
		AcpSchema.PromptResponse response = AcpTestFixtures.createPromptResponse();

		String json = jsonMapper.writeValueAsString(response);
		AcpSchema.PromptResponse deserialized = jsonMapper.readValue(json, new TypeRef<AcpSchema.PromptResponse>() {
		});

		assertThat(deserialized.stopReason()).isEqualTo(AcpSchema.StopReason.END_TURN);
	}

	// ---------------------------
	// Capabilities Serialization
	// ---------------------------

	@Test
	void clientCapabilitiesSerialization() throws IOException {
		AcpSchema.ClientCapabilities capabilities = AcpTestFixtures.createClientCapabilitiesWithFs();

		String json = jsonMapper.writeValueAsString(capabilities);
		AcpSchema.ClientCapabilities deserialized = jsonMapper.readValue(json,
				new TypeRef<AcpSchema.ClientCapabilities>() {
				});

		assertThat(deserialized.fs()).isNotNull();
		assertThat(deserialized.fs().readTextFile()).isTrue();
		assertThat(deserialized.fs().writeTextFile()).isTrue();
	}

	@Test
	void agentCapabilitiesSerialization() throws IOException {
		AcpSchema.AgentCapabilities capabilities = AcpTestFixtures.createAgentCapabilitiesWithLoadSession();

		String json = jsonMapper.writeValueAsString(capabilities);
		AcpSchema.AgentCapabilities deserialized = jsonMapper.readValue(json,
				new TypeRef<AcpSchema.AgentCapabilities>() {
				});

		assertThat(deserialized.loadSession()).isTrue();
		assertThat(deserialized.mcpCapabilities()).isNotNull();
		assertThat(deserialized.promptCapabilities()).isNotNull();
	}

	// ---------------------------
	// Content Block Serialization
	// ---------------------------

	@Test
	void textContentSerialization() throws IOException {
		AcpSchema.TextContent content = AcpTestFixtures.createTextContent("Hello, world!");

		String json = jsonMapper.writeValueAsString(content);
		AcpSchema.TextContent deserialized = jsonMapper.readValue(json, new TypeRef<AcpSchema.TextContent>() {
		});

		assertThat(deserialized.text()).isEqualTo("Hello, world!");
		assertThat(deserialized.type()).isEqualTo("text");
	}

	@Test
	void imageContentSerialization() throws IOException {
		AcpSchema.ImageContent content = AcpTestFixtures.createImageContent();

		String json = jsonMapper.writeValueAsString(content);
		AcpSchema.ImageContent deserialized = jsonMapper.readValue(json, new TypeRef<AcpSchema.ImageContent>() {
		});

		assertThat(deserialized.type()).isEqualTo("image");
		assertThat(deserialized.data()).isEqualTo("base64-encoded-data");
		assertThat(deserialized.mimeType()).isEqualTo("image/png");
	}

	@Test
	void audioContentSerialization() throws IOException {
		AcpSchema.AudioContent content = AcpTestFixtures.createAudioContent();

		String json = jsonMapper.writeValueAsString(content);
		AcpSchema.AudioContent deserialized = jsonMapper.readValue(json, new TypeRef<AcpSchema.AudioContent>() {
		});

		assertThat(deserialized.type()).isEqualTo("audio");
		assertThat(deserialized.data()).isEqualTo("base64-encoded-data");
		assertThat(deserialized.mimeType()).isEqualTo("audio/wav");
	}

	// ---------------------------
	// JSON-RPC Message Serialization
	// ---------------------------

	@Test
	void jsonRpcRequestSerialization() throws IOException {
		AcpSchema.JSONRPCRequest request = AcpTestFixtures.createJsonRpcRequest("test/method", 1,
				Collections.singletonMap("key", "value"));

		String json = jsonMapper.writeValueAsString(request);
		AcpSchema.JSONRPCRequest deserialized = jsonMapper.readValue(json, new TypeRef<AcpSchema.JSONRPCRequest>() {
		});

		assertThat(deserialized.jsonrpc()).isEqualTo("2.0");
		assertThat(deserialized.id()).isEqualTo(1);
		assertThat(deserialized.method()).isEqualTo("test/method");
		assertThat(deserialized.params()).isNotNull();
	}

	@Test
	void jsonRpcNotificationSerialization() throws IOException {
		AcpSchema.JSONRPCNotification notification = AcpTestFixtures.createJsonRpcNotification("test/notification",
				Collections.singletonMap("key", "value"));

		String json = jsonMapper.writeValueAsString(notification);
		AcpSchema.JSONRPCNotification deserialized = jsonMapper.readValue(json,
				new TypeRef<AcpSchema.JSONRPCNotification>() {
				});

		assertThat(deserialized.jsonrpc()).isEqualTo("2.0");
		assertThat(deserialized.method()).isEqualTo("test/notification");
		assertThat(deserialized.params()).isNotNull();
	}

	@Test
	void jsonRpcResponseSerialization() throws IOException {
		AcpSchema.JSONRPCResponse response = AcpTestFixtures.createJsonRpcResponse(1,
				Collections.singletonMap("result", "success"));

		String json = jsonMapper.writeValueAsString(response);
		AcpSchema.JSONRPCResponse deserialized = jsonMapper.readValue(json, new TypeRef<AcpSchema.JSONRPCResponse>() {
		});

		assertThat(deserialized.jsonrpc()).isEqualTo("2.0");
		assertThat(deserialized.id()).isEqualTo(1);
		assertThat(deserialized.result()).isNotNull();
		assertThat(deserialized.error()).isNull();
	}

	@Test
	void jsonRpcErrorResponseSerialization() throws IOException {
		AcpSchema.JSONRPCResponse response = AcpTestFixtures.createJsonRpcErrorResponse(1, -32600,
				"Invalid Request");

		String json = jsonMapper.writeValueAsString(response);
		AcpSchema.JSONRPCResponse deserialized = jsonMapper.readValue(json, new TypeRef<AcpSchema.JSONRPCResponse>() {
		});

		assertThat(deserialized.jsonrpc()).isEqualTo("2.0");
		assertThat(deserialized.id()).isEqualTo(1);
		assertThat(deserialized.result()).isNull();
		assertThat(deserialized.error()).isNotNull();
		assertThat(deserialized.error().code()).isEqualTo(-32600);
		assertThat(deserialized.error().message()).isEqualTo("Invalid Request");
	}

	// ---------------------------
	// Session State Serialization
	// ---------------------------

	@Test
	void sessionModeStateSerialization() throws IOException {
		AcpSchema.SessionModeState state = AcpTestFixtures.createSessionModeState();

		String json = jsonMapper.writeValueAsString(state);
		AcpSchema.SessionModeState deserialized = jsonMapper.readValue(json,
				new TypeRef<AcpSchema.SessionModeState>() {
				});

		assertThat(deserialized.currentModeId()).isEqualTo("code");
		assertThat(deserialized.availableModes()).hasSize(1);
		assertThat(deserialized.availableModes().get(0).id()).isEqualTo("code");
	}

	@Test
	void sessionModelStateSerialization() throws IOException {
		AcpSchema.SessionModelState state = AcpTestFixtures.createSessionModelState();

		String json = jsonMapper.writeValueAsString(state);
		AcpSchema.SessionModelState deserialized = jsonMapper.readValue(json,
				new TypeRef<AcpSchema.SessionModelState>() {
				});

		assertThat(deserialized.currentModelId()).isEqualTo("test-model");
		assertThat(deserialized.availableModels()).hasSize(1);
		assertThat(deserialized.availableModels().get(0).modelId()).isEqualTo("test-model");
	}

}
