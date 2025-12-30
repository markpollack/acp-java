# Step 1.1: Agent Transport Layer - Learnings

**Date**: 2025-12-30

## What Was Done

Created the agent-side transport layer for the ACP Java SDK, enabling agents to communicate with clients via stdio.

## Components Created

### 1. StdioAcpAgentTransport

**Location**: `src/main/java/com/agentclientprotocol/sdk/agent/transport/StdioAcpAgentTransport.java`

Agent-side stdio transport implementation:
- Reads from `InputStream` (stdin by default) for client → agent messages
- Writes to `OutputStream` (stdout by default) for agent → client messages
- Separate schedulers for inbound and outbound processing
- Ready signals via `Sinks.One` to coordinate startup

Key patterns used:
- `AtomicBoolean isClosing` for graceful shutdown coordination
- `Sinks.One<Void>` for ready signals (inboundReady, outboundReady)
- `sendMessage()` waits for both sinks before allowing sends

```java
StdioAcpAgentTransport transport = new StdioAcpAgentTransport(jsonMapper);
transport.start(msg -> handleMessage(msg)).subscribe();
```

### 2. StdioProtocolDriver

**Location**: `src/test/java/com/agentclientprotocol/sdk/test/StdioProtocolDriver.java`

Protocol driver using piped streams for testing:
- Uses `PipedInputStream`/`PipedOutputStream` to connect client and agent transports
- Simulates real stdio communication within the same JVM
- Enables `AbstractProtocolTest` to run against stdio transport

Data flow:
```
Client writes → clientOut (pipe) → agentIn → Agent reads
Agent writes → agentOut (pipe) → clientIn → Client reads
```

### 3. StdioProtocolTest

**Location**: `src/test/java/com/agentclientprotocol/sdk/test/StdioProtocolTest.java`

Runs all `AbstractProtocolTest` tests through the `StdioProtocolDriver`.

### 4. StdioAcpAgentTransportTest

**Location**: `src/test/java/com/agentclientprotocol/sdk/agent/transport/StdioAcpAgentTransportTest.java`

Unit tests for agent transport:
- Constructor validation
- Double-start prevention
- Message receive from input stream
- Message send to output stream
- Graceful close

## Key Learnings

### 1. Ready Signal Coordination

The transport uses two `Sinks.One<Void>` signals to coordinate startup:
- `inboundReady` - emitted when inbound processing thread starts
- `outboundReady` - emitted when outbound processing subscriber is active

`sendMessage()` waits for both signals before attempting to enqueue:
```java
public Mono<Void> sendMessage(JSONRPCMessage message) {
    return Mono.zip(inboundReady.asMono(), outboundReady.asMono())
        .then(Mono.defer(() -> {
            if (outboundSink.tryEmitNext(message).isSuccess()) {
                return Mono.empty();
            } else {
                return Mono.error(new RuntimeException("Failed to enqueue"));
            }
        }));
}
```

### 2. Piped Streams for Testing

`PipedInputStream`/`PipedOutputStream` enable in-JVM stdio simulation:
- Buffer size must be large enough (65536 bytes) to avoid blocking
- Both sides must be connected before use
- Close all streams in finally block to prevent resource leaks

### 3. Transport Error Logging

Expected "Transport error" messages during test cleanup:
- Occur when streams close while transport is reading
- Logged via exception handler but don't cause test failures
- Normal behavior for stdio transport lifecycle

### 4. Handler Return Type Difference

ACP `AcpAgentTransport.start()` signature:
```java
Mono<Void> start(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler);
```

The handler returns `Mono<JSONRPCMessage>` (not `Mono<Void>`), allowing response messages.
MCP uses `Mono<Void>` - this is intentional for ACP's request/response pattern.

## Test Statistics

After implementation:
- Total tests: 117
- New tests: 10 (StdioAcpAgentTransportTest: 6, StdioProtocolTest: 4)
- All passing

## Package Structure After Step 1.1

```
com.agentclientprotocol.sdk
├── spec/
│   ├── AcpAgentTransport    # Interface (already existed)
│   └── ...
├── client/
│   └── transport/
│       └── StdioAcpClientTransport
└── agent/
    └── transport/
        └── StdioAcpAgentTransport   # NEW
```

## Next Steps

Step 1.2 will:
1. Create `AcpAgentSession` for JSON-RPC session management
2. Implement request/response correlation with handler maps
3. Add notification handling
4. Implement protocol methods (initialize, session/new, session/prompt)
