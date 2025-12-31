# ACP Java SDK

Pure Java implementation of the [Agent Client Protocol (ACP)](https://agentclientprotocol.com/) specification for building both clients and agents.

## Overview

The Agent Client Protocol (ACP) standardizes communication between code editors and coding agents. This library provides both client and agent implementations, enabling Java applications to:

- **Connect to** ACP-compliant agents (Client SDK)
- **Build** ACP-compliant agents in Java (Agent SDK)

**What You Can Build:**
- Desktop applications that connect to ACP agents
- IDE plugins that integrate with coding agents
- CLI tools that orchestrate agent workflows
- Backend services that use agents for code generation
- **Your own ACP-compliant coding agents in Java**

**Key Features:**
- **Java 17** - Modern Java API
- **Reactive** - Built on Project Reactor for non-blocking I/O
- **Type-Safe** - Complete protocol type definitions (all ACP v1 types)
- **Async & Sync** - Both asynchronous (Mono-based) and synchronous APIs
- **Modular** - Core module with zero external dependencies; optional WebSocket module
- **Stdio Transport** - Process management and JSON-RPC message framing
- **WebSocket Transport** - JDK-native client, Jetty-based server (optional module)

## Modules

The SDK is organized into modules to minimize dependencies:

| Module | Artifact | Dependencies | Description |
|--------|----------|--------------|-------------|
| **acp-core** | `acp-core` | Reactor, Jackson, SLF4J | Core SDK with stdio transport and WebSocket client |
| **acp-websocket-jetty** | `acp-websocket-jetty` | acp-core + Jetty | WebSocket agent transport (server-side) |

**Design Philosophy:**
- `acp-core` has **no external server dependencies** - uses JDK-native WebSocket client
- WebSocket *server* support requires Jetty, isolated in a separate module
- Community can contribute alternative WebSocket modules (Netty, Undertow, etc.)

## Quick Start

### Prerequisites

Before using this SDK, you need:

1. **Java 17 or later** - Check with `java -version`
2. **Maven 3.6+** - This project uses Maven with the included wrapper (`./mvnw`)

For client testing:
3. **An ACP-compliant agent** - For example:
   - [Google Gemini CLI](https://www.npmjs.com/package/@google/gemini-cli): `npm install -g @google/gemini-cli`
   - Set `GEMINI_API_KEY` environment variable with your API key

### Maven Dependency

> **Note:** Not yet published to Maven Central. For now, build and install locally using `./mvnw install`.

**For most users (stdio transport, or WebSocket client):**
```xml
<dependency>
    <groupId>com.agentclientprotocol</groupId>
    <artifactId>acp-core</artifactId>
    <version>0.9.0</version>
</dependency>
```

**For agents accepting WebSocket connections (requires Jetty):**
```xml
<dependency>
    <groupId>com.agentclientprotocol</groupId>
    <artifactId>acp-websocket-jetty</artifactId>
    <version>0.9.0</version>
</dependency>
```

## Client SDK

The Client SDK enables Java applications to connect to ACP-compliant agents.

### Async Client

```java
import com.agentclientprotocol.sdk.client.AcpAsyncClient;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import io.modelcontextprotocol.json.McpJsonMapper;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.List;

// Create agent parameters for Gemini CLI
AgentParameters params = AgentParameters.builder("gemini")
    .arg("--experimental-acp")
    .build();

// Create transport
McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
StdioAcpClientTransport transport = new StdioAcpClientTransport(params, jsonMapper);

// Build async client with session update consumer
AcpAsyncClient client = AcpClient.async(transport)
    .requestTimeout(Duration.ofSeconds(30))
    .sessionUpdateConsumer(notification -> {
        System.out.println("Agent update: " + notification.update());
        return Mono.empty();
    })
    .build();

// Initialize the agent
InitializeResponse initResponse = client
    .initialize(new InitializeRequest(1, new ClientCapabilities()))
    .block();

// Create a new session
NewSessionResponse session = client
    .newSession(new NewSessionRequest("/path/to/workspace", List.of()))
    .block();

// Send a prompt
PromptResponse response = client
    .prompt(new PromptRequest(session.sessionId(),
        List.of(new TextContent("Create a README.md file"))))
    .block();

// Close gracefully
client.closeGracefully().block();
```

### Sync Client

```java
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import io.modelcontextprotocol.json.McpJsonMapper;
import java.time.Duration;
import java.util.List;

// Create transport
AgentParameters params = AgentParameters.builder("gemini")
    .arg("--experimental-acp")
    .build();
McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
StdioAcpClientTransport transport = new StdioAcpClientTransport(params, jsonMapper);

// Create sync client
AcpSyncClient client = AcpClient.async(transport)
    .requestTimeout(Duration.ofSeconds(30))
    .buildSync();

// Same API but blocking by default
InitializeResponse initResponse = client.initialize(
    new InitializeRequest(1, new ClientCapabilities())
);

NewSessionResponse session = client.newSession(
    new NewSessionRequest("/path/to/workspace", List.of())
);

PromptResponse response = client.prompt(
    new PromptRequest(session.sessionId(),
        List.of(new TextContent("Create a README.md file")))
);

// Close the client
client.close();
```

## Agent SDK

The Agent SDK enables building ACP-compliant agents in Java.

### Async Agent

```java
import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpAsyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import io.modelcontextprotocol.json.McpJsonMapper;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.List;

// Create transport (reads from stdin, writes to stdout)
McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
StdioAcpAgentTransport transport = new StdioAcpAgentTransport(jsonMapper);

// Build async agent with handlers
AcpAsyncAgent agent = AcpAgent.async(transport)
    .requestTimeout(Duration.ofSeconds(60))
    .initializeHandler(request -> {
        return Mono.just(new InitializeResponse(
            1,
            new AgentCapabilities(true, null, null),
            List.of()
        ));
    })
    .newSessionHandler(request -> {
        String sessionId = java.util.UUID.randomUUID().toString();
        return Mono.just(new NewSessionResponse(sessionId, null, null));
    })
    .promptHandler((request, updater) -> {
        // Send streaming updates during processing
        return updater.sendUpdate(request.sessionId(),
                new AgentThoughtChunk("agent_thought_chunk",
                    new TextContent("Analyzing request...")))
            .then(updater.sendUpdate(request.sessionId(),
                new AgentMessageChunk("agent_message_chunk",
                    new TextContent("I'll help you with that."))))
            .then(Mono.just(new PromptResponse(StopReason.END_TURN)));
    })
    .cancelHandler(notification -> {
        System.err.println("Cancelled: " + notification.sessionId());
        return Mono.empty();
    })
    .build();

// Start the agent
agent.start().block();

// Agent runs until client disconnects or shutdown signal
// ...

// Graceful shutdown
agent.closeGracefully().block();
```

### Agent with Client Requests

Agents can request file operations and permissions from the client:

```java
AcpAsyncAgent agent = AcpAgent.async(transport)
    .initializeHandler(request -> Mono.just(
        new InitializeResponse(1, new AgentCapabilities(), List.of())
    ))
    .newSessionHandler(request -> Mono.just(
        new NewSessionResponse("session-1", null, null)
    ))
    .promptHandler((request, updater) -> {
        // Read a file from the client
        return agent.readTextFile(new ReadTextFileRequest(
                request.sessionId(), "/src/Main.java", null, null))
            .flatMap(fileResponse -> {
                String content = fileResponse.content();
                // Process the file...
                return updater.sendUpdate(request.sessionId(),
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Read file: " + content.length() + " chars")));
            })
            .then(Mono.just(new PromptResponse(StopReason.END_TURN)));
    })
    .build();
```

### Sync Agent

```java
import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import io.modelcontextprotocol.json.McpJsonMapper;
import reactor.core.publisher.Mono;
import java.util.List;

// Create transport
McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
StdioAcpAgentTransport transport = new StdioAcpAgentTransport(jsonMapper);

// Build sync agent
AcpSyncAgent agent = AcpAgent.sync(transport)
    .initializeHandler(request -> Mono.just(
        new InitializeResponse(1, new AgentCapabilities(), List.of())
    ))
    .newSessionHandler(request -> Mono.just(
        new NewSessionResponse("session-1", null, null)
    ))
    .promptHandler((request, updater) -> Mono.just(
        new PromptResponse(StopReason.END_TURN)
    ))
    .build();

// Start the agent (blocking)
agent.start();

// Use blocking methods for client requests
ReadTextFileResponse file = agent.readTextFile(
    new ReadTextFileRequest("session-1", "/path/to/file.txt", null, null)
);

// Close
agent.close();
```

## Architecture

```
CLIENT SIDE                              AGENT SIDE
┌─────────────────────────────────────┐  ┌─────────────────────────────────────┐
│  Java Application                   │  │  Your ACP Agent                     │
└────────────┬────────────────────────┘  └────────────┬────────────────────────┘
             │                                        │
┌────────────▼────────────────────────┐  ┌────────────▼────────────────────────┐
│  AcpAsyncClient / AcpSyncClient     │  │  AcpAsyncAgent / AcpSyncAgent       │
│  - High-level fluent API            │  │  - Handler registration             │
│  - Session management               │  │  - Session updates                  │
└────────────┬────────────────────────┘  └────────────┬────────────────────────┘
             │                                        │
┌────────────▼────────────────────────┐  ┌────────────▼────────────────────────┐
│  AcpClientSession                   │  │  AcpAgentSession                    │
│  - JSON-RPC message handling        │  │  - Single-turn enforcement          │
│  - Request/response lifecycle       │  │  - Bidirectional requests           │
└────────────┬────────────────────────┘  └────────────┬────────────────────────┘
             │                                        │
┌────────────▼────────────────────────┐  ┌────────────▼────────────────────────┐
│  StdioAcpClientTransport            │  │  StdioAcpAgentTransport             │
│  - Spawns agent process             │◄─►│  - Reads from stdin                 │
│  - STDIO communication              │  │  - Writes to stdout                 │
└─────────────────────────────────────┘  └─────────────────────────────────────┘
```

## API Components

### Core Types (`com.agentclientprotocol.sdk.spec`)

- **`AcpSchema`** - Complete ACP protocol type definitions
  - `InitializeRequest/Response` - Protocol handshake
  - `NewSessionRequest/Response` - Session management
  - `PromptRequest/Response` - Agent prompting
  - `SessionNotification` - Agent updates
  - All content types, capabilities, and enums

- **`AcpClientSession`** / **`AcpAgentSession`** - Session implementations
- **`AcpTransport`** - Transport abstraction

### Client Layer (`com.agentclientprotocol.sdk.client`)

- **`AcpClient`** - Builder entry point
- **`AcpAsyncClient`** - Reactive async client (Mono-based)
- **`AcpSyncClient`** - Synchronous client (blocking)
- **`StdioAcpClientTransport`** - STDIO transport (spawns agent process)

### Agent Layer (`com.agentclientprotocol.sdk.agent`)

- **`AcpAgent`** - Builder entry point
- **`AcpAsyncAgent`** - Reactive async agent (Mono-based)
- **`AcpSyncAgent`** - Synchronous agent (blocking)
- **`StdioAcpAgentTransport`** - STDIO transport (reads stdin/writes stdout)

## Building

### Requirements
- Java 17 or later
- Maven 3.6 or later (or use included Maven wrapper)

### Build Commands

```bash
# Compile
./mvnw compile

# Run tests
./mvnw test

# Package
./mvnw package

# Install to local Maven repository
./mvnw install

# Build with release artifacts (sources, javadoc, GPG signing)
./mvnw install -Prelease
```

## Testing

The SDK includes comprehensive tests:

- **Unit tests** - Fast, isolated tests for all components
- **Integration tests** - Full client-agent communication tests
- **Mock utilities** - `MockAcpAgent` and `MockAcpClient` for testing

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=InMemoryClientAgentTest
```

## Dependencies

- **Project Reactor** (`reactor-core`) - Reactive programming
- **Jackson** (`jackson-databind`) - JSON processing
- **MCP JSON** (`mcp-json`, `mcp-json-jackson2`) - JSON utilities from Model Context Protocol SDK
- **SLF4J** - Logging facade

## Roadmap

### v0.9.0 (Current) - Full SDK
- Client implementation with async and sync APIs
- Agent implementation with async and sync APIs
- Stdio transport for both client and agent
- Complete protocol type definitions
- Integration tests and mock utilities

### v1.0.0 (Planned) - GA Release
- WebSocket transport
- Spring WebSocket integration module
- Performance optimizations
- Production hardening
