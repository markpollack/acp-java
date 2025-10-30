# ACP Java SDK

Pure Java implementation of the [Agent Client Protocol (ACP)](https://agentclientprotocol.com/) specification.

## Overview

The Agent Client Protocol (ACP) standardizes communication between code editors and coding agents. This library provides a pure Java implementation of the ACP client, enabling Java applications to interact with ACP-compliant coding agents.

**Key Features:**
- **Pure Java** - No Kotlin dependencies, clean Java 17 API
- **Reactive** - Built on Project Reactor for non-blocking I/O
- **Type-Safe** - Comprehensive protocol type definitions
- **Async & Sync** - Both asynchronous (Mono-based) and synchronous client APIs
- **Transport Agnostic** - STDIO transport included, extensible for other transports

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>org.acp</groupId>
    <artifactId>acp-java-sdk</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Basic Usage

#### Async Client (Recommended)

```java
import org.acp.client.*;
import org.acp.client.transport.*;
import org.acp.spec.AcpSchema.*;

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

#### Sync Client (Simpler API)

```java
import org.acp.client.*;

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

### Core Types (`org.acp.spec`)

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

### Client Layer (`org.acp.client`)

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

### Transport (`org.acp.client.transport`)

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
- Maven 3.6 or later

### Build Commands

```bash
# Compile
mvn compile

# Run tests (requires GEMINI_API_KEY)
export GEMINI_API_KEY=your-key-here
mvn test

# Package
mvn package

# Install to local Maven repository
mvn install

# Build with release artifacts (sources, javadoc, GPG signing)
mvn install -Prelease
```

## Testing

```bash
# Run all tests
mvn test

# Run with specific API key
export GEMINI_API_KEY=your-gemini-api-key
mvn test

# Skip tests
mvn install -DskipTests
```

Integration tests require:
- Gemini CLI (`npm install -g @google/gemini-cli@0.8.2+`)
- `GEMINI_API_KEY` environment variable

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
│   ├── main/java/org/acp/
│   │   ├── spec/           Protocol definitions and types
│   │   ├── client/         High-level client implementations
│   │   └── util/           Utility classes
│   └── test/
│       ├── java/org/acp/   Integration tests
│       └── resources/      Test configuration
├── pom.xml                 Maven configuration
├── LICENSE                 Apache License 2.0
└── README.md              This file
```

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Submit a pull request

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

## Links

- **ACP Specification**: https://agentclientprotocol.com/
- **Issues**: https://github.com/acp-java/acp-java-sdk/issues
- **Source Code**: https://github.com/acp-java/acp-java-sdk

## Authors

- **Mark Pollack** - [@markpollack](https://github.com/markpollack)
- **Christian Tzolov** - [@tzolov](https://github.com/tzolov)

Extracted from [Spring AI Agents](https://github.com/spring-ai-community/spring-ai-agents) project.
