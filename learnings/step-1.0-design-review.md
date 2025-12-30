# Step 1.0: Design Review - Learnings

**Date**: 2025-12-30

## What Was Done

- Read and reviewed all required documents:
  - `FINAL-DESIGN-DOC.md` - Comprehensive design synthesis
  - `DESIGN-DECISION-TURN-BASED-ARCHITECTURE.md` - Single-turn evidence
  - `~/acp/research/kotlin-sdk/CLAUDE.md` - AgentSupport/AgentSession patterns
  - MCP Java SDK `McpServer`/`McpServerSession` implementation
  - `PACKAGE_NAMING.md` - Package structure decisions
  - `~/community/claude-agent-sdk-java/` - High-level API implementation
  - `~/community/mintlify-docs/agent-sdk/` - API design philosophy
  - Official ACP specification (`~/acp/research/agent-client-protocol/`)

## Critical Design Verifications

### 1. Multiple Sessions Support (Trains of Thought) - VERIFIED

**ACP Specification** (`docs/overview/architecture.mdx:20`):
> "Each connection can support several concurrent sessions, so you can have multiple trains of thought going on at once."

**Our Design** supports this correctly:
```java
try (var client = AcpClient.sync().transport(transport).build()) {
    client.initialize();  // Capability exchange

    String session1 = client.newSession().sessionId();  // First train of thought
    String session2 = client.newSession().sessionId();  // Second train of thought

    client.prompt(session1, "Question 1");  // Route by session ID
    client.prompt(session2, "Question 2");  // Parallel "train of thought"
}
```

**Implementation Verification**:
- `AcpAsyncClient.newSession()` creates new sessions with unique IDs
- `AcpAsyncClient.prompt(PromptRequest)` takes `sessionId` for routing
- `AcpClientSession` manages transport-level JSON-RPC (one per connection)
- Multiple ACP sessions (trains of thought) are identified by session IDs

### 2. Two-API Architecture (NOT Three-Tier) - CONFIRMED

**Key Decision**: ACP uses a **Two-API architecture** (Sync + Async), NOT the three-tier Query/Sync/Async pattern from Claude CLI SDK.

**Rationale** (from `FINAL-DESIGN-DOC.md`):
> "No One-Shot API: Unlike the Claude CLI SDK, ACP does not have a separate one-shot `Query` class. ACP's protocol requires explicit session lifecycle (initialize → session/new → prompt → close), making a one-shot abstraction less natural."

| API | Class | Style | Use Case |
|-----|-------|-------|----------|
| **Blocking** | `AcpSyncClient` | Iterator-based | Traditional applications |
| **Reactive** | `AcpAsyncClient` | Flux/Mono | High concurrency |

**Both clients provide identical capabilities** - they differ only in programming paradigm.

### 3. Turn-Based Semantics Within Sessions - VALIDATED

**Critical Finding**: Only ONE prompt can be active per session at a time.

This is separate from multiple sessions support:
- **Multiple sessions**: Client can have many concurrent sessions (trains of thought)
- **Single turn per session**: Within each session, only one `session/prompt` active at a time

Enforcement pattern from Kotlin SDK:
```java
private final AtomicReference<ActivePrompt> activePrompt = new AtomicReference<>();

if (!activePrompt.compareAndSet(null, newPrompt)) {
    throw new IllegalStateException("There is already active prompt execution");
}
```

## High-Level API Design Patterns

### TurnSpec Pattern for Reactive Response Handling

```java
interface TurnSpec {
    Mono<String> text();              // Collected text (enables flatMap chaining)
    Flux<String> textStream();        // Streaming text as it arrives
    Flux<SessionUpdate> updates();    // All update types
}

// Multi-turn with flatMap chaining
client.prompt(sessionId, "My favorite color is blue.").text()
    .flatMap(r1 -> client.prompt(sessionId, "What is it?").text())
    .subscribe(System.out::println);  // "blue"
```

### Iterator Pattern for Blocking API

```java
// Simple text extraction (convenience method)
String answer = client.promptText(sessionId, "What is 2+2?");

// Full message access with iteration
for (SessionUpdate update : client.promptAndReceive(sessionId, "Hello")) {
    if (update.hasTextDelta()) {
        System.out.print(update.textDelta());
    }
}
```

### Convenience Methods for Common Cases

| Simplicity | Method | Returns | Use Case |
|------------|--------|---------|----------|
| **Most simple** | `promptText(sessionId, prompt)` | `String` | Just want text |
| **Medium** | `promptAndReceive(sessionId, prompt)` | `Iterable<SessionUpdate>` | Need streaming |
| **Most powerful** | Full iterator/Flux | N/A | Full control |

### Factory Pattern with Fluent Builders

```java
// Client (existing)
AcpSyncClient client = AcpClient.sync(transport)
    .requestTimeout(Duration.ofMinutes(5))
    .sessionUpdateConsumer(update -> ...)
    .build();

// Agent (to implement)
AcpAsyncAgent agent = AcpAgent.async(transport)
    .agentInfo("my-agent", "1.0.0")
    .onPrompt(request -> processPrompt(request))
    .build();
```

### Cross-Turn Handlers

For concerns that span multiple turns:
```java
client.onUpdate(update -> logger.info("Update: {}", update.type()))
      .onTurnComplete(result -> metrics.recordTurn(result));
```

## Infrastructure Patterns

### Session Handler Maps (from MCP)

```java
public AcpAgentSession(
    Duration requestTimeout,
    AcpAgentTransport transport,
    Map<String, RequestHandler<?>> requestHandlers,
    Map<String, NotificationHandler> notificationHandlers
)
```

### Transport-Agnostic Testing (from Kotlin SDK)

```java
public abstract class AbstractProtocolTest {
    private final ProtocolDriver driver;

    @Test
    void simpleRequestReturnsResult() {
        driver.runWithProtocols((client, agent) -> {
            // Same test logic, different transports
        });
    }
}
```

## Package Structure - Confirmed Correct

```
com.agentclientprotocol.sdk
├── spec/                    # Protocol interfaces
│   ├── AcpAgentTransport   # Agent transport interface ✅
│   ├── AcpClientTransport  # Client transport interface ✅
│   └── ...
├── client/                  # Client implementation ✅
│   └── transport/           # Client transport impls ✅
└── agent/                   # Agent implementation (to be created)
    └── transport/           # Agent transport impls (to be created)
```

## Patterns to Reuse

1. **TurnSpec pattern** - Response handling with multiple extraction paths
2. **Iterator pattern** - Thread-safe blocking iteration
3. **Factory + Builder** - `AcpClient.sync()`/`AcpAgent.async()` entry points
4. **Convenience methods** - `promptText()`, `promptAndReceive()`
5. **Handler maps** - `Map<String, RequestHandler<?>>` for method dispatch
6. **AtomicReference** - Single-turn enforcement per session
7. **ProtocolDriver** - Transport-agnostic testing

## Patterns NOT Used (By Design)

1. **No Query/one-shot API** - ACP's explicit session lifecycle makes this unnatural
2. **No process-as-session model** - ACP uses wire-level session IDs

## Design Document Status

`FINAL-DESIGN-DOC.md` is complete and accurate:
- Section 2.3: Correctly explains why ACP needs explicit sessions
- Section 2.4: Documents all protocol methods
- Section 4: High-level API patterns (correctly notes no one-shot)
- Multiple sessions support documented at lines 86, 102, 109-113

**No updates needed to design documents.**

## Next Steps

Step 1.0.5 should:
1. Create test infrastructure before agent implementation (TDD approach)
2. Implement `InMemoryTransportPair` for unit testing
3. Create `ProtocolDriver` interface for transport-agnostic tests
4. Add golden file test data

## Documents Reviewed

| Document | Status | Notes |
|----------|--------|-------|
| FINAL-DESIGN-DOC.md | ✅ Complete | Multiple sessions documented correctly |
| DESIGN-DECISION-TURN-BASED-ARCHITECTURE.md | ✅ Complete | Single-turn per session validated |
| Kotlin SDK CLAUDE.md | ✅ Reviewed | Patterns directly applicable |
| MCP McpServer/McpServerSession | ✅ Reviewed | Factory and session patterns reusable |
| PACKAGE_NAMING.md | ✅ Current | Structure is correct |
| Official ACP Specification | ✅ Reviewed | Multiple sessions confirmed |
| claude-agent-sdk-java | ✅ Reviewed | Two-API pattern (not Query) applies |
