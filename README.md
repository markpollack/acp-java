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

Note, not yet published to maven central!  Hang in there...

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

- **`AcpSchema`** - Complete ACP protocol type definitions (1071 lines)
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

## Publishing to Maven Central

This project is configured for publishing to Maven Central Portal using the modern `central-publishing-maven-plugin`.

### Prerequisites

1. **Maven Central account** - [Create account](https://central.sonatype.com/)
2. **GPG key** - [Generate GPG key](https://central.sonatype.org/publish/requirements/gpg/)
3. **Credentials** - Add to `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>central</id>
            <username>your-central-username</username>
            <password>your-central-password</password>
        </server>
    </servers>
</settings>
```

### Publishing

```bash
# Deploy to Maven Central Portal (staging repository)
mvn clean deploy -Prelease

# With auto-publish enabled (configured in pom.xml)
# Artifacts will be automatically released after validation
```

See [Maven Central Portal Documentation](https://central.sonatype.org/publish/publish-portal-maven/) for details.

## Dependencies

### Core Dependencies
- **Project Reactor** (`reactor-core`) - Reactive programming
- **Jackson** (`jackson-databind`) - JSON processing
- **MCP JSON** (`mcp-json`, `mcp-json-jackson2`) - JSON utilities (reused from Model Context Protocol SDK)
- **SLF4J** - Logging facade

### Test Dependencies
- JUnit 5, AssertJ, Mockito, Logback

### About MCP JSON Dependency

This library reuses JSON utilities from the [Model Context Protocol (MCP) Java SDK](https://github.com/modelcontextprotocol/java-sdk). Both MCP and ACP use JSON-RPC 2.0 for communication, so sharing JSON serialization utilities reduces code duplication.

If you prefer not to depend on MCP SDK, you can replace this with direct Jackson usage in a future version.

## Project Structure

```
acp-java-sdk/
├── src/
│   ├── main/java/com/agentclientprotocol/sdk/
│   │   ├── spec/           Protocol definitions and types
│   │   ├── client/         High-level client implementations
│   │   └── util/           Utility classes
│   └── test/
│       ├── java/com/agentclientprotocol/sdk/   Integration tests
│       └── resources/      Test configuration
├── pom.xml                 Maven configuration
├── LICENSE                 Apache License 2.0
└── README.md              This file
```

## Scope and Limitations

### ✅ What's Included (v0.1.0)

This release provides client-side ACP support:

- **All ACP Client Methods** - `initialize`, `authenticate`, `session/new`, `session/load`, `prompt`, `cancel`
- **All ACP Types** - Complete schema with 1,069 lines of protocol definitions
- **Request Handlers** - Handle agent requests for file operations, permissions, terminal access
- **Streaming Updates** - Receive `session/update` notifications with agent thoughts, tool calls, plans
- **Async & Sync APIs** - Choose between reactive (`Mono`-based) or blocking APIs
- **Stdio Transport** - Process management and JSON-RPC framing
- **Documentation** - Javadoc, examples, and this README

### ❌ What's Not Included (Coming in v0.9.0)

**Agent-side implementation** for building ACP agents in Java:

- ❌ Agent factory and builder (`AcpAgent.async()`, `AcpAgent.sync()`)
- ❌ Agent session management (`AcpAgentSession`)
- ❌ Agent request handlers (handling `initialize`, `prompt`, etc.)
- ❌ Outbound client requests (agent calling client methods)
- ❌ Agent transport implementations
- ❌ Agent examples and documentation

**Workaround:** If you need to build ACP agents in Java today, consider:
- Using the [Kotlin SDK](https://github.com/agentclientprotocol/kotlin-sdk) which has full agent support and runs on the JVM
- Waiting for v0.9.0 agent support in this SDK (planned for Q1 2026)

## Roadmap

### v0.8.0 (Current) - Client SDK ✅
- ✅ ACP client implementation
- ✅ Async and sync client APIs
- ✅ Stdio transport
- ✅ Full protocol type definitions
- ✅ Build configuration for Maven Central

### v0.9.0 (Planned - Q1 2026) - Agent SDK
- [ ] Agent-side implementation (`AcpAsyncAgent`, `AcpSyncAgent`)
- [ ] Agent session management
- [ ] Agent request handlers
- [ ] Agent transport providers (stdio, HTTP)
- [ ] Agent examples and documentation
- [ ] Complete parity with TypeScript/Python/Kotlin/Rust SDKs
- [ ] Improved unit test coverage

### v1.0.0 (GA - Q2 2026)
- [ ] Stable client and agent implementations
- [ ] Test coverage (80%+)
- [ ] Helper utilities (content builders, tool call builders)
- [ ] Performance optimizations
- [ ] Documentation and examples

### Future Releases
- [ ] Additional transport options (HTTP, WebSocket)
- [ ] Enhanced observability (metrics, tracing)
- [ ] Spring Boot integration
- [ ] GraalVM native image support
- [ ] Kotlin DSL for builder APIs

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Submit a pull request

**Priority Areas for Contributions:**
- 🔥 **Agent-side implementation** (v0.2.0 roadmap)
- 🧪 **Unit tests** for existing components
- 📚 **Additional examples** showing different use cases
- 🛠️ **Helper utilities** for common patterns

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

## Links

- **ACP Specification**: https://agentclientprotocol.com/
- **Issues**: https://github.com/acp-java/acp-java-sdk/issues
- **Source Code**: https://github.com/acp-java/acp-java-sdk

