# Step 1.4: Agent Integration Tests - Learnings

**Date**: 2025-12-30

## What Was Done

Created comprehensive integration tests and mock utilities for testing the full client ↔ agent communication lifecycle.

## Components Created

### 1. AbstractAcpClientAgentIT Base Class

**Location**: `src/test/java/com/agentclientprotocol/sdk/integration/AbstractAcpClientAgentIT.java`

Abstract base class providing a framework for integration tests:
- Template methods: `createClientTransport()`, `createAgentTransport()`, `closeTransports()`
- 8 integration test methods covering the full protocol lifecycle

Test coverage:
- `initializeHandshakeSucceeds()` - Protocol version negotiation
- `sessionCreationWorks()` - Session creation with cwd
- `promptResponseFlowWorks()` - Basic prompt/response
- `sessionUpdatesStreamCorrectly()` - Streaming session updates
- `agentToClientPermissionRequestWorks()` - Agent→Client permission flow
- `agentToClientFileReadWorks()` - Agent→Client file read
- `gracefulShutdownWorks()` - Clean shutdown
- `cancelNotificationWorks()` - Cancel notification handling

### 2. InMemoryClientAgentTest

**Location**: `src/test/java/com/agentclientprotocol/sdk/integration/InMemoryClientAgentTest.java`

Concrete implementation using `InMemoryTransportPair` for fast, deterministic tests:
```java
class InMemoryClientAgentTest extends AbstractAcpClientAgentIT {
    private InMemoryTransportPair transportPair;

    @Override
    protected AcpClientTransport createClientTransport() {
        transportPair = InMemoryTransportPair.create();
        return transportPair.clientTransport();
    }
    // ...
}
```

### 3. MockAcpAgent

**Location**: `src/test/java/com/agentclientprotocol/sdk/test/MockAcpAgent.java`

Mock agent for client testing:
- Records all received requests (init, newSession, prompts, cancellations)
- Configurable responses via builder
- Synchronous waiting with `expectPrompts(count)` / `awaitPrompts(timeout)`

Usage:
```java
MockAcpAgent mockAgent = MockAcpAgent.builder(agentTransport)
    .initializeResponse(new AcpSchema.InitializeResponse(...))
    .promptResponse(request -> new AcpSchema.PromptResponse(StopReason.END_TURN))
    .build();

mockAgent.start();
// ... run client tests ...
assertThat(mockAgent.getReceivedPrompts()).hasSize(1);
```

### 4. MockAcpClient

**Location**: `src/test/java/com/agentclientprotocol/sdk/test/MockAcpClient.java`

Mock client for agent testing:
- Records received session updates, permission requests, file requests
- Auto-handles agent→client requests with configurable responses
- Convenience methods: `initialize()`, `newSession(cwd)`, `prompt(text)`

Usage:
```java
MockAcpClient mockClient = MockAcpClient.builder(clientTransport)
    .permissionResponse(request -> new AcpSchema.RequestPermissionResponse(...))
    .fileContent("/path/file.txt", "content")
    .build();

mockClient.initialize();
mockClient.newSession("/workspace");
mockClient.prompt("Do something");
assertThat(mockClient.getReceivedUpdates()).isNotEmpty();
```

## Key Learnings

### 1. Lambda Self-Reference Issue

When defining handlers that need to reference the agent/client being built, use `AtomicReference`:

```java
// Problem: 'agent' not initialized in lambda
AcpAsyncAgent agent = AcpAgent.async(transport)
    .promptHandler((request, updater) -> {
        return agent.requestPermission(...); // Compile error!
    })
    .build();

// Solution: Use AtomicReference
AtomicReference<AcpAsyncAgent> agentRef = new AtomicReference<>();
AcpAsyncAgent agent = AcpAgent.async(transport)
    .promptHandler((request, updater) -> {
        return agentRef.get().requestPermission(...); // Works!
    })
    .build();
agentRef.set(agent);
```

### 2. Maven Surefire Test Naming

By default, Surefire only runs `*Test.java` files, not `*IT.java`:
- Integration tests ending in `IT` are for Failsafe plugin
- Renamed `InMemoryClientAgentIT` → `InMemoryClientAgentTest` to run with Surefire

### 3. Schema Type Discovery

During test implementation, discovered correct schema types:
- `AgentCapabilities.loadSession` (not `streaming`)
- `ToolKind.EDIT` (not `FILE_WRITE`)
- `PermissionOptionKind.ALLOW_ONCE` / `REJECT_ONCE` (not `ALLOW` / `DENY`)
- `PromptRequest.prompt()` (not `content()`)
- `AgentThoughtChunk` (not `AgentThinkingChunk`)

### 4. Test Timeout Handling

Integration tests require careful timeout management:
```java
protected static final Duration TIMEOUT = Duration.ofSeconds(10);

// Allow time for async startup
agent.start().subscribe();
Thread.sleep(100);

// Block with timeout on operations
client.initialize(...).block(TIMEOUT);
```

### 5. Session Update Streaming Test Pattern

To verify streaming updates are received:
```java
List<AcpSchema.SessionNotification> receivedUpdates = new CopyOnWriteArrayList<>();
CountDownLatch updateLatch = new CountDownLatch(2);

client = AcpClient.async(transport)
    .sessionUpdateConsumer(notification -> {
        receivedUpdates.add(notification);
        updateLatch.countDown();
        return Mono.empty();
    })
    .build();

// Trigger prompt that sends updates
client.prompt(...).block(TIMEOUT);

assertThat(updateLatch.await(5, TimeUnit.SECONDS)).isTrue();
assertThat(receivedUpdates).hasSize(2);
```

## Test Statistics

After implementation:
- Total tests: 148
- New tests: 15 (8 integration + 3 MockAcpAgent + 4 MockAcpClient)
- All passing

## Package Structure After Step 1.4

```
src/test/java/com/agentclientprotocol/sdk/
├── integration/
│   ├── AbstractAcpClientAgentIT.java    # Base class (NEW)
│   └── InMemoryClientAgentTest.java     # In-memory tests (NEW)
└── test/
    ├── InMemoryTransportPair.java       # (existing)
    ├── MockAcpAgent.java                # (NEW)
    ├── MockAcpAgentTest.java            # (NEW)
    ├── MockAcpClient.java               # (NEW)
    └── MockAcpClientTest.java           # (NEW)
```

## Integration Test Coverage

| Test | Verifies |
|------|----------|
| initializeHandshakeSucceeds | Protocol negotiation |
| sessionCreationWorks | Session management |
| promptResponseFlowWorks | Basic prompt lifecycle |
| sessionUpdatesStreamCorrectly | Streaming notifications |
| agentToClientPermissionRequestWorks | Bidirectional requests |
| agentToClientFileReadWorks | File system operations |
| gracefulShutdownWorks | Clean lifecycle termination |
| cancelNotificationWorks | Notification handling |

## Next Steps

Phase 1 (Agent-Side) is now complete. Phase 2 will focus on:
1. Step 2.1: Semantic versioning system
2. Step 2.2: Protocol version negotiation
3. Step 2.3: Error handling improvements
4. Step 2.4: Logging and observability
