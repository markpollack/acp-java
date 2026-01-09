# Step 3.7: Typed Handler API & Diagnostic Logging - Learnings

**Date**: 2026-01-09

## What Was Done

- Added typed handler API (`Function<Request, Response>`) with auto-unmarshalling for:
  - `readTextFileHandler(Function<ReadTextFileRequest, ReadTextFileResponse>)`
  - `writeTextFileHandler(Function<WriteTextFileRequest, WriteTextFileResponse>)`
  - `requestPermissionHandler(Function<RequestPermissionRequest, RequestPermissionResponse>)`
- Added DEBUG/TRACE logging in `AcpClientSession`:
  - DEBUG: Session creation with handler list
  - DEBUG: Handler invocation and completion
  - TRACE: Full request/notification params
  - WARN: Missing handler with suggestions
- Added specific error message for `session/request_permission` method
- Added `slf4j-simple` to tutorial dependencies
- Updated module-07 to use typed handlers

## What Worked Well

1. **Overloading with Function<> works**: Java can distinguish between `SyncRequestHandler<T>` (takes Object) and `Function<Request, Response>` (takes typed param)
2. **Auto-unmarshalling simplifies user code**: Users no longer need to know about `TypeRef` or `transport.unmarshalFrom()`
3. **Graduated logging levels**: DEBUG for useful info, TRACE for detailed debugging, WARN for actionable issues
4. **SLF4J simple binding**: Easy fix for tutorial warnings

## What Was Challenging

1. **Lambda ambiguity**: Initially tried to delegate from typed handler to raw handler, but lambdas matched both overloads
   - Fix: Create explicit `SyncRequestHandler<T>` variable to disambiguate

2. **Debugging permission issues**: Without `--yolo` flag, Gemini CLI sends `session/request_permission` which we didn't have a handler for
   - Fix: Added specific WARN message suggesting `--yolo` or handler registration

3. **Understanding Gemini CLI behavior**: ToolCall notifications are informational-only; actual file requests require permission approval
   - Learning: The ACP protocol has two paths - tool notifications vs actual method calls

## Key Decisions

1. **Keep both handler variants**: Typed handlers are preferred, but raw handlers remain for advanced use cases
2. **DEBUG vs TRACE separation**:
   - DEBUG: What's happening (method names, success/failure)
   - TRACE: Full data (params, available handlers)
3. **WARN for missing handlers**: Users need to know when their handlers aren't being called

## Patterns to Reuse

### Typed Handler with Auto-Unmarshalling
```java
public SyncSpec readTextFileHandler(
        Function<AcpSchema.ReadTextFileRequest, AcpSchema.ReadTextFileResponse> handler) {
    Assert.notNull(handler, "Handler must not be null");
    SyncRequestHandler<AcpSchema.ReadTextFileResponse> rawHandler = params -> {
        AcpSchema.ReadTextFileRequest request = transport.unmarshalFrom(params,
                new TypeRef<AcpSchema.ReadTextFileRequest>() {});
        return handler.apply(request);
    };
    this.requestHandlers.put(METHOD_NAME, fromSync(rawHandler));
    return this;
}
```

### Graduated Logging in Session
```java
// On session creation
logger.debug("Session created with {} handlers: {}", handlers.size(), handlers.keySet());

// On handler invocation
logger.debug("Invoking handler for method '{}'", method);
logger.trace("Handler params: {}", params);

// On handler completion
handler.handle(params)
    .doOnSuccess(r -> logger.debug("Handler completed successfully"))
    .doOnError(e -> logger.debug("Handler threw error: {}", e.getMessage()))

// On missing handler
logger.warn("No handler for '{}': {} - {}", method, error.message(), suggestion);
logger.trace("Available handlers: {}", handlers.keySet());
```

## Patterns to Avoid

1. **Silent failures**: Always log when expected behavior doesn't occur
2. **Generic error messages**: Include specific suggestions (e.g., "use --yolo flag or register a handler")
3. **TRACE for everything**: Reserve TRACE for high-volume data; use DEBUG for general diagnostics

## Research: Zed Integration in Gemini CLI

Investigated `zedIntegration.ts` in gemini-cli:

**What it is:**
- Full ACP agent implementation for Zed editor (not just tests)
- Zed is a modern Rust-based code editor
- gemini-cli acts as an ACP backend that Zed connects to

**Testing patterns observed:**
- Mock ACP connection with `vi.fn()` for `sessionUpdate`, `requestPermission`
- Test initialization with client capabilities
- Test session creation and error handling
- Test capability-based feature enablement (fs, terminal)

**Applicable to Java SDK:**
- Consider mock transport tests that verify handler invocation
- Test capability-based handler registration
- Test error paths (missing handlers, auth failures)

## Files Modified

| File | Changes |
|------|---------|
| `AcpClient.java` | Added TypeRef import, typed handler overloads |
| `AcpClientSession.java` | Added DEBUG/TRACE logging, improved error messages |
| `tutorial/pom.xml` | Added slf4j-simple dependency |
| `module-07/AgentRequestsClient.java` | Switched to typed handlers |

## Test Results

- SDK: All tests pass
- Module-07: Works with typed handlers and no SLF4J warning

## Next Steps

- Step 3.8: Comprehensive MCP SDK alignment audit
- Analyze test coverage gaps vs Python/TypeScript SDK patterns
- Consider adding handler invocation tests based on Zed patterns
