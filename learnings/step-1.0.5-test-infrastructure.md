# Step 1.0.5: Test Infrastructure Foundation - Learnings

**Date**: 2025-12-30

## What Was Done

Created the foundational test infrastructure for the ACP Java SDK following patterns from:
- Kotlin ACP SDK's `ProtocolDriver` pattern
- MCP Java SDK's `MockMcpTransport` pattern

## Components Created

### 1. InMemoryTransportPair

**Location**: `src/test/java/com/agentclientprotocol/sdk/test/InMemoryTransportPair.java`

Bidirectional in-memory transport for testing client ↔ agent communication:
- Uses `Sinks.Many<JSONRPCMessage>` for each direction
- `InMemoryClientTransport` - Implements `AcpClientTransport`
- `InMemoryAgentTransport` - Implements `AcpAgentTransport`
- Messages sent by client arrive at agent, and vice versa

```java
InMemoryTransportPair pair = InMemoryTransportPair.create();
AcpClientTransport clientTransport = pair.clientTransport();
AcpAgentTransport agentTransport = pair.agentTransport();
```

### 2. ProtocolDriver Interface

**Location**: `src/test/java/com/agentclientprotocol/sdk/test/ProtocolDriver.java`

Abstract transport setup enabling same test logic across different transports:
```java
public interface ProtocolDriver {
    void runWithTransports(BiConsumer<AcpClientTransport, AcpAgentTransport> testBlock);
}
```

### 3. InMemoryProtocolDriver

**Location**: `src/test/java/com/agentclientprotocol/sdk/test/InMemoryProtocolDriver.java`

Implementation using `InMemoryTransportPair` for fast unit tests.

### 4. AbstractProtocolTest

**Location**: `src/test/java/com/agentclientprotocol/sdk/test/AbstractProtocolTest.java`

Base class with core protocol tests:
- `messageFromClientArrivesAtAgent` - Client → Agent message delivery
- `messageFromAgentArrivesAtClient` - Agent → Client message delivery
- `bidirectionalMessageExchange` - Request/response round-trip
- `transportCloseGracefullyCompletesWithoutError` - Graceful shutdown

Concrete test class (`InMemoryProtocolTest`) extends this with specific driver.

### 5. Golden Files

**Location**: `src/test/resources/golden/`

Sample JSON files for wire format validation:
- `initialize-request.json`
- `initialize-response.json`
- `session-new-request.json`
- `session-new-response.json`
- `session-prompt-request.json`
- `session-update-notification.json`

### 6. GoldenFileTest

**Location**: `src/test/java/com/agentclientprotocol/sdk/test/GoldenFileTest.java`

Tests verifying JSON serialization/deserialization against golden files.

## Key Learnings

### 1. Reactive Transport Testing

The in-memory transport uses `Sinks.Many<JSONRPCMessage>` which is reactive:
- `connect()` and `start()` return immediately
- Handler receives messages via `doOnNext()`
- Use `CountDownLatch` for synchronization in tests

### 2. McpJsonMapper Usage

`McpJsonMapper` is an interface without direct ObjectMapper access:
- Use `readValue()` and `writeValueAsString()` for serialization
- For JsonNode parsing in tests, create a separate `ObjectMapper`
- Use `AcpSchema.deserializeJsonRpcMessage()` for proper polymorphic deserialization

### 3. SessionUpdate Type Hierarchy

`SessionUpdate` is polymorphic with discriminator `sessionUpdate`:
- `AgentMessageChunk` - requires `sessionUpdate` type + `content`
- `UserMessageChunk` - similar structure
- Constructor order: `(sessionUpdate, content)` not just `(content)`

## Test Statistics

After implementation:
- Total tests: 107
- New tests: 14 (InMemoryProtocolTest: 4, InMemoryTransportPairTest: 4, GoldenFileTest: 6)
- All passing

## Pattern for Future Transport Tests

```java
// Define new driver
class StdioProtocolDriver implements ProtocolDriver {
    @Override
    public void runWithTransports(BiConsumer<AcpClientTransport, AcpAgentTransport> testBlock) {
        // Set up stdio transport pair
        // Run test
        // Clean up
    }
}

// Extend abstract test
class StdioProtocolTest extends AbstractProtocolTest {
    StdioProtocolTest() {
        super(new StdioProtocolDriver());
    }
}
```

## Next Steps

Step 1.1 will:
1. Create `AcpAgent` factory class (matching `AcpClient` pattern)
2. Create `AcpAgentSession` for session management
3. Create `AcpAsyncAgent` and `AcpSyncAgent` implementations
4. Use this test infrastructure for TDD
