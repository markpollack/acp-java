# ACP Java Client SDK

Pure Java **client-side** implementation of the [Agent Client Protocol (ACP)](https://agentclientprotocol.com/) specification.

> **Note:** This is a **client-only** SDK for v0.8.0. It enables Java applications to **connect to** ACP-compliant agents.
> Agent-side implementation (for **building** ACP agents in Java) is planned for v0.9.0. See [Roadmap](#roadmap) below.

## Overview

The Agent Client Protocol (ACP) standardizes communication between code editors and coding agents. This library provides a Java client implementation, enabling Java applications to interact with ACP-compliant coding agents like Google Gemini, Anthropic Claude, and others.

**What You Can Build:**
- Desktop applications that connect to ACP agents
- IDE plugins that integrate with coding agents
- CLI tools that orchestrate agent workflows
- Backend services that use agents for code generation

**What This Release Includes:**
- **Client-Side Only** - Connect to and interact with existing ACP agents
- **Not Included Yet** - Building your own ACP agents in Java (coming in v0.9.0)

**Key Features:**
- **Java 17** - Modern Java API
- **Reactive** - Built on Project Reactor for non-blocking I/O
- **Type-Safe** - Complete protocol type definitions (all ACP v1 types)
- **Async & Sync** - Both asynchronous (Mono-based) and synchronous client APIs
- **Stdio Transport** - Process management and JSON-RPC message framing

## Quick Start

### Prerequisites

Before using this SDK, you need:

1. **Java 17 or later** - Check with `java -version`
2. **Maven 3.6+** - This project uses Maven with the included wrapper (`./mvnw`)
3. **An ACP-compliant agent** - For example:
   - [Google Gemini CLI](https://www.npmjs.com/package/@google/gemini-cli): `npm install -g @google/gemini-cli`
   - Set `GEMINI_API_KEY` environment variable with your API key

### Maven Dependency

> **Note:** Not yet published to Maven Central. For now, build and install locally using `./mvnw install`.

```xml
<dependency>
    <groupId>com.agentclientprotocol</groupId>
    <artifactId>acp-java-sdk</artifactId>
    <version>0.8.0</version>
</dependency>
```

### Basic Usage

#### Async Client

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
    .prompt(session.sessionId(), "Create a README.md file")
    .block();

// Close gracefully
client.closeGracefully().block();
```

#### Sync Client

```java
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import io.modelcontextprotocol.json.McpJsonMapper;
import java.time.Duration;
import java.util.List;

// Create transport (same as async example)
AgentParameters params = AgentParameters.builder("gemini")
    .arg("--experimental-acp")
    .build();
McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
StdioAcpClientTransport transport = new StdioAcpClientTransport(params, jsonMapper);

// Create sync client (wraps async client)
AcpSyncClient client = AcpClient.sync(transport)
    .requestTimeout(Duration.ofSeconds(30))
    .build();

// Same API but blocking by default
InitializeResponse initResponse = client.initialize(
    new InitializeRequest(1, new ClientCapabilities())
);

NewSessionResponse session = client.newSession(
    new NewSessionRequest("/path/to/workspace", List.of())
);

PromptResponse response = client.prompt(
    session.sessionId(),
    "Create a README.md file"
);

// Close the client
client.close();
```

## Architecture

```
┌─────────────────────────────────────┐
│  Java Application                   │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│  AcpAsyncClient / AcpSyncClient     │
│  - High-level fluent API            │
│  - Session management               │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│  AcpClientSession                   │
│  - JSON-RPC message handling        │
│  - Request/response lifecycle       │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│  StdioAcpClientTransport            │
│  - Process management               │
│  - STDIO communication              │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│  ACP-compliant Agent Process        │
│  (Gemini, Claude, etc.)             │
└─────────────────────────────────────┘
```

## API Components

### Core Types (`com.agentclientprotocol.sdk.spec`)

- **`AcpSchema`** - Complete ACP protocol type definitions
  - `InitializeRequest/Response` - Protocol handshake
  - `NewSessionRequest/Response` - Session management
  - `PromptRequest/Response` - Agent prompting
  - `SessionNotification` - Agent updates
  - All content types, capabilities, and enums

- **`AcpClientSession`** - Low-level session implementation
  - JSON-RPC 2.0 message handling
  - Request/response lifecycle management
  - Notification processing

- **`AcpSession`** - Session interface
- **`AcpTransport`** - Transport abstraction

### Client Layer (`com.agentclientprotocol.sdk.client`)

- **`AcpClient`** - Builder entry point
  - `AcpClient.async(transport)` - Create async client builder
  - `AcpClient.sync(transport)` - Create sync client builder

- **`AcpAsyncClient`** - Reactive async client
  - All operations return `Mono<T>`
  - Composable with Reactor pipelines
  - Non-blocking I/O

- **`AcpSyncClient`** - Synchronous client
  - Blocking API for simpler code
  - Wraps `AcpAsyncClient`

### Transport (`com.agentclientprotocol.sdk.client.transport`)

- **`StdioAcpClientTransport`** - STDIO transport
  - Manages agent process lifecycle
  - Handles stdin/stdout communication
  - Graceful shutdown with SIGTERM

- **`AgentParameters`** - Process configuration
  - Command and arguments
  - Environment variables
  - Working directory

## Building

### Requirements
- Java 17 or later
- Maven 3.6 or later (or use included Maven wrapper)

### Build Commands

```bash
# Compile
./mvnw compile

# Package
./mvnw package

# Install to local Maven repository
./mvnw install

# Build with release artifacts (sources, javadoc, GPG signing)
./mvnw install -Prelease
```

## Dependencies

- **Project Reactor** (`reactor-core`) - Reactive programming
- **Jackson** (`jackson-databind`) - JSON processing
- **MCP JSON** (`mcp-json`, `mcp-json-jackson2`) - JSON utilities from Model Context Protocol SDK
- **SLF4J** - Logging facade

## Roadmap

### v0.8.0 (Current) - Client SDK
- Client implementation with async and sync APIs
- Stdio transport
- Complete protocol type definitions

### v0.9.0 (Planned - Q1 2026) - Agent SDK
- Agent-side implementation
- Agent session management
- Agent request handlers
- Agent transport providers

### v1.0.0 (GA - Q2 2026)
- Stable client and agent implementations
- Helper utilities
- Performance optimizations
