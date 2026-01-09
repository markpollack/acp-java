# ACP Java SDK

Pure Java implementation of the [Agent Client Protocol (ACP)](https://agentclientprotocol.com/) specification for building both clients and agents.

## Overview

The Agent Client Protocol (ACP) standardizes communication between code editors and coding agents. This SDK enables Java applications to:

- **Connect to** ACP-compliant agents (Client SDK)
- **Build** ACP-compliant agents in Java (Agent SDK)

**Key Features:**
- Java 17+, reactive (Project Reactor), type-safe
- Async and sync APIs
- Stdio and WebSocket transports
- Capability negotiation and structured error handling

## Installation

> **Note:** Not yet published to Maven Central. For now, build and install locally using `./mvnw install`.

```xml
<dependency>
    <groupId>com.agentclientprotocol</groupId>
    <artifactId>acp-core</artifactId>
    <version>0.9.0</version>
</dependency>
```

For WebSocket server support (agents accepting WebSocket connections):
```xml
<dependency>
    <groupId>com.agentclientprotocol</groupId>
    <artifactId>acp-websocket-jetty</artifactId>
    <version>0.9.0</version>
</dependency>
```

---

## Getting Started

### 1. Hello World Client

Connect to an ACP agent and send a prompt:

```java
import com.agentclientprotocol.sdk.client.*;
import com.agentclientprotocol.sdk.client.transport.*;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import java.util.List;

// Connect to an agent via stdio
var params = AgentParameters.builder("gemini").arg("--experimental-acp").build();
var transport = new StdioAcpClientTransport(params);

// Create client
AcpSyncClient client = AcpClient.sync(transport).build();

// Initialize, create session, send prompt
client.initialize();
var session = client.newSession(new NewSessionRequest("/workspace", List.of()));
var response = client.prompt(new PromptRequest(
    session.sessionId(),
    List.of(new TextContent("Hello, world!"))
));

client.close();
```

### 2. Hello World Agent

Create a minimal ACP agent:

```java
import com.agentclientprotocol.sdk.agent.*;
import com.agentclientprotocol.sdk.agent.transport.*;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import reactor.core.publisher.Mono;
import java.util.List;

// Create stdio transport
var transport = new StdioAcpAgentTransport();

// Build agent with handlers
AcpAsyncAgent agent = AcpAgent.async(transport)
    .initializeHandler(req -> Mono.just(
        new InitializeResponse(1, new AgentCapabilities(), List.of())
    ))
    .newSessionHandler(req -> Mono.just(
        new NewSessionResponse(java.util.UUID.randomUUID().toString(), null, null)
    ))
    .promptHandler((req, updater) -> Mono.just(
        new PromptResponse(StopReason.END_TURN)
    ))
    .build();

// Start and run
agent.start().block();
```

---

## Progressive Examples

### 3. Streaming Updates

Send real-time updates to the client during prompt processing:

```java
.promptHandler((request, updater) -> {
    return updater.sendUpdate(request.sessionId(),
            new AgentThoughtChunk("agent_thought_chunk",
                new TextContent("Thinking...")))
        .then(updater.sendUpdate(request.sessionId(),
            new AgentMessageChunk("agent_message_chunk",
                new TextContent("Here's my response."))))
        .then(Mono.just(new PromptResponse(StopReason.END_TURN)));
})
```

Clients receive updates via the session update consumer:

```java
AcpAsyncClient client = AcpClient.async(transport)
    .sessionUpdateConsumer(notification -> {
        System.out.println("Update: " + notification.update());
        return Mono.empty();
    })
    .build();
```

### 4. Agent-to-Client Requests

Agents can request file operations from the client:

```java
.promptHandler((request, updater) -> {
    // Read a file from the client's filesystem
    return agent.readTextFile(new ReadTextFileRequest(
            request.sessionId(), "/src/Main.java", null, null))
        .flatMap(file -> {
            String content = file.content();
            // Process content...
            return Mono.just(new PromptResponse(StopReason.END_TURN));
        });
})
```

Clients must register handlers for agent requests:

```java
AcpAsyncClient client = AcpClient.async(transport)
    .readTextFileHandler(params -> {
        // Read file and return content
        ReadTextFileRequest req = /* unmarshal params */;
        String content = Files.readString(Path.of(req.path()));
        return Mono.just(new ReadTextFileResponse(content));
    })
    .build();
```

### 5. Capability Negotiation

Check what features the peer supports before using them:

```java
// Client: check agent capabilities after initialize
client.initialize(new InitializeRequest(1, clientCaps)).block();

NegotiatedCapabilities agentCaps = client.getAgentCapabilities();
if (agentCaps.supportsLoadSession()) {
    // Agent supports session persistence
}
if (agentCaps.supportsImageContent()) {
    // Agent can handle image content in prompts
}
```

```java
// Agent: check client capabilities before requesting operations
NegotiatedCapabilities clientCaps = agent.getClientCapabilities();
if (clientCaps.supportsReadTextFile()) {
    agent.readTextFile(...);
} else {
    // Client doesn't support file reading - handle gracefully
}

// Or use require methods (throws AcpCapabilityException if not supported)
clientCaps.requireWriteTextFile();
agent.writeTextFile(...);
```

### 6. Error Handling

Handle protocol errors with structured exceptions:

```java
import com.agentclientprotocol.sdk.error.*;

try {
    client.prompt(request);
} catch (AcpProtocolException e) {
    if (e.isConcurrentPrompt()) {
        // Another prompt is already in progress
    } else if (e.isMethodNotFound()) {
        // Agent doesn't support this method
    }
    System.err.println("Error " + e.getCode() + ": " + e.getMessage());
} catch (AcpCapabilityException e) {
    // Tried to use a capability the peer doesn't support
    System.err.println("Capability not supported: " + e.getCapability());
} catch (AcpConnectionException e) {
    // Transport-level connection error
}
```

### 7. WebSocket Transport

Use WebSocket instead of stdio for network-based communication:

**Client (JDK-native, no extra dependencies):**
```java
import com.agentclientprotocol.sdk.client.transport.WebSocketAcpClientTransport;
import java.net.URI;

var transport = new WebSocketAcpClientTransport(
    URI.create("ws://localhost:8080/acp"),
    McpJsonMapper.getDefault()
);
AcpSyncClient client = AcpClient.sync(transport).build();
```

**Agent (requires acp-websocket-jetty module):**
```java
import com.agentclientprotocol.sdk.agent.transport.WebSocketAcpAgentTransport;

var transport = new WebSocketAcpAgentTransport(
    8080,                           // port
    "/acp",                         // path
    McpJsonMapper.getDefault()
);
AcpAsyncAgent agent = AcpAgent.async(transport)
    // ... handlers ...
    .build();

agent.start().block();  // Starts WebSocket server on port 8080
```

---

## API Reference

### Packages

| Package | Description |
|---------|-------------|
| `com.agentclientprotocol.sdk.spec` | Protocol types (`AcpSchema.*`) |
| `com.agentclientprotocol.sdk.client` | Client SDK (`AcpClient`, `AcpAsyncClient`, `AcpSyncClient`) |
| `com.agentclientprotocol.sdk.agent` | Agent SDK (`AcpAgent`, `AcpAsyncAgent`, `AcpSyncAgent`) |
| `com.agentclientprotocol.sdk.capabilities` | Capability negotiation (`NegotiatedCapabilities`) |
| `com.agentclientprotocol.sdk.error` | Exceptions (`AcpProtocolException`, `AcpCapabilityException`) |

### Transports

| Transport | Client | Agent | Module |
|-----------|--------|-------|--------|
| Stdio | `StdioAcpClientTransport` | `StdioAcpAgentTransport` | acp-core |
| WebSocket | `WebSocketAcpClientTransport` | `WebSocketAcpAgentTransport` | acp-core / acp-websocket-jetty |

---

## Building

```bash
./mvnw compile      # Compile
./mvnw test         # Run tests (246 tests across 2 modules)
./mvnw install      # Install to local Maven repository
```

## Testing Your Code

Use the mock utilities for testing:

```java
import com.agentclientprotocol.sdk.test.*;

// Create in-memory transport pair for testing
InMemoryTransportPair pair = InMemoryTransportPair.create();

// Use pair.clientTransport() for client, pair.agentTransport() for agent
MockAcpClient mockClient = MockAcpClient.builder(pair.clientTransport())
    .fileContent("/test.txt", "test content")
    .build();
```

---

## Roadmap

### v0.9.0 (Current)
- Client and Agent SDKs with async/sync APIs
- Stdio and WebSocket transports
- Capability negotiation
- Structured error handling
- Full protocol compliance (all SessionUpdate types, MCP configs, `_meta` extensibility)
- 232 tests

### v1.0.0 (Planned)
- Maven Central publishing
- Production hardening
- Performance optimizations
