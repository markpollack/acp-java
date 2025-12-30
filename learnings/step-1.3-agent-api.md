# Step 1.3: Agent High-Level API - Learnings

**Date**: 2025-12-30

## What Was Done

Created the high-level agent API for the ACP Java SDK, providing an easy-to-use interface for building ACP-compliant agents.

## Components Created

### 1. AcpAgent Factory

**Location**: `src/main/java/com/agentclientprotocol/sdk/agent/AcpAgent.java`

Factory interface following the MCP Java SDK pattern:
- `AcpAgent.async(transport)` → `AsyncAgentBuilder`
- `AcpAgent.sync(transport)` → `SyncAgentBuilder`

Builder API for registering handlers:
```java
AcpAsyncAgent agent = AcpAgent.async(transport)
    .requestTimeout(Duration.ofSeconds(30))
    .initializeHandler(request -> Mono.just(response))
    .newSessionHandler(request -> Mono.just(response))
    .promptHandler((request, updater) -> {
        updater.sendUpdate(sessionId, update);
        return Mono.just(response);
    })
    .build();
```

Handler interfaces defined:
- `InitializeHandler`
- `AuthenticateHandler`
- `NewSessionHandler`
- `LoadSessionHandler`
- `PromptHandler` (with `SessionUpdateSender`)
- `SetSessionModeHandler`
- `SetSessionModelHandler`
- `CancelHandler`

### 2. AcpAsyncAgent Interface

**Location**: `src/main/java/com/agentclientprotocol/sdk/agent/AcpAsyncAgent.java`

Interface for async agent operations:
- `start()` - Begins accepting client connections
- `sendSessionUpdate(sessionId, update)` - Sends session updates
- `requestPermission(request)` - Requests client permission
- `readTextFile(request)` / `writeTextFile(request)` - File operations
- `createTerminal(request)` / `getTerminalOutput(request)` - Terminal operations
- `closeGracefully()` / `close()` - Lifecycle management

### 3. DefaultAcpAsyncAgent Implementation

**Location**: `src/main/java/com/agentclientprotocol/sdk/agent/DefaultAcpAsyncAgent.java`

Default implementation that:
- Creates `AcpAgentSession` on `start()`
- Registers request/notification handlers with the session
- Delegates agent→client requests through the session
- Uses `TypeRef` for proper JSON deserialization

Key pattern for handler registration with type erasure:
```java
requestHandlers.put(METHOD_INITIALIZE, params -> {
    InitializeRequest request = transport.unmarshalFrom(params, new TypeRef<>() {});
    return handler.handle(request).cast(Object.class);
});
```

### 4. AcpSyncAgent Wrapper

**Location**: `src/main/java/com/agentclientprotocol/sdk/agent/AcpSyncAgent.java`

Blocking wrapper using `block()`:
- Provides synchronous API for simpler use cases
- Delegates to `AcpAsyncAgent` internally
- Configurable timeout for blocking operations
- `async()` method to access underlying async agent

### 5. AcpAgentTest

**Location**: `src/test/java/com/agentclientprotocol/sdk/agent/AcpAgentTest.java`

Unit tests covering:
- Builder creates async agent
- Builder creates sync agent
- Agent handles initialize request
- Agent handles new session request
- Agent handles prompt request
- Agent handles cancel notification
- Sync agent delegates to async
- Graceful close completes

## Key Learnings

### 1. Generic Type Handling with RequestHandler<?>

The `AcpAgentSession.RequestHandler<?>` uses a wildcard generic that loses type information. To work around this, use `.cast(Object.class)`:

```java
// Problem: Mono<InitializeResponse> not compatible with Mono<Object>
return handler.handle(request);

// Solution: Cast to Object
return handler.handle(request).cast(Object.class);
```

### 2. SessionUpdateSender Pattern

The `PromptHandler` receives a `SessionUpdateSender` to enable streaming updates during prompt processing:

```java
@FunctionalInterface
interface PromptHandler {
    Mono<PromptResponse> handle(PromptRequest request, SessionUpdateSender updater);
}

interface SessionUpdateSender {
    Mono<Void> sendUpdate(String sessionId, SessionUpdate update);
}
```

Usage in handler:
```java
.promptHandler((request, updater) -> {
    updater.sendUpdate(request.sessionId(),
        new AgentMessageChunk("agent_message_chunk", new TextContent("Working...")));
    // ... process prompt
    return Mono.just(new PromptResponse(StopReason.END_TURN));
})
```

### 3. Handler Registration is Lazy

Handlers are not registered until `start()` is called. This allows building the agent configuration first, then starting when ready:

```java
AcpAsyncAgent agent = AcpAgent.async(transport)
    .initializeHandler(...)
    .build();  // Agent created but not started

agent.start().block();  // Now handlers are registered
```

### 4. TypeRef for Deserialization

Using `TypeRef<>` anonymous classes to preserve generic type information at runtime:

```java
AcpSchema.PromptRequest request = transport.unmarshalFrom(params,
    new TypeRef<AcpSchema.PromptRequest>() {});
```

## Test Statistics

After implementation:
- Total tests: 133
- New tests: 7 (AcpAgentTest)
- All passing

## Package Structure After Step 1.3

```
com.agentclientprotocol.sdk
├── spec/
│   ├── AcpSession
│   ├── AcpClientSession
│   ├── AcpAgentSession
│   ├── AcpAgentTransport
│   └── AcpSchema
├── client/
│   ├── AcpClient              # Client factory
│   ├── AcpAsyncClient
│   └── AcpSyncClient
└── agent/
    ├── AcpAgent               # Agent factory (NEW)
    ├── AcpAsyncAgent          # Interface (NEW)
    ├── DefaultAcpAsyncAgent   # Implementation (NEW)
    ├── AcpSyncAgent           # Blocking wrapper (NEW)
    └── transport/
        └── StdioAcpAgentTransport
```

## API Symmetry with Client SDK

The agent API mirrors the client SDK pattern:

| Client | Agent |
|--------|-------|
| `AcpClient.async(transport)` | `AcpAgent.async(transport)` |
| `AcpClient.sync(transport)` | `AcpAgent.sync(transport)` |
| `AcpAsyncClient` | `AcpAsyncAgent` |
| `AcpSyncClient` | `AcpSyncAgent` |

## Next Steps

Step 1.4 will:
1. Create integration tests with real client ↔ agent communication
2. Implement `AbstractAcpClientAgentIT` base class
3. Create `StdioClientAgentIT` for stdio transport tests
4. Add `MockAcpAgent` and `MockAcpClient` for testing
5. Verify full protocol lifecycle works end-to-end
