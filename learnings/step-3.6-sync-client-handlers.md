# Step 3.6: Sync Client Handler Signatures - Learnings

**Date**: 2026-01-09

## What Was Done

- Added `SyncRequestHandler<T>` interface to `AcpClient` that returns plain values (no `Mono<>`)
- Created library-owned daemon scheduler `SYNC_HANDLER_SCHEDULER` for sync handler execution
- Added `fromSync()` conversion method in `SyncSpec` using `Mono.fromCallable()` + custom scheduler
- Updated all `SyncSpec` handler methods to accept sync handlers as the primary API
- Deprecated async handler methods in `SyncSpec` with `*Async` suffix
- Updated `sessionUpdateConsumer()` to accept plain `Consumer<SessionNotification>` for sync clients
- Updated tutorial modules 05, 07, 08 to use the new sync handler patterns

## What Worked Well

1. **Following MCP SDK pattern**: The `fromSync()` conversion pattern from MCP Java SDK translated well
2. **Using library-owned daemon scheduler**: Avoided global `Schedulers.boundedElastic()` which was causing lingering thread issues
3. **Overloading with deprecation**: Kept async methods available but deprecated with `*Async` suffix for backward compatibility
4. **Clean tutorial code**: Tutorial modules now look much cleaner without `Mono.just()` and `Mono.empty()` wrappers

## What Was Challenging

1. **Scheduler choice**: Had to reconsider using `Schedulers.boundedElastic()` after remembering the best practices from Step 3.5
2. **Method overloading with generics**: Java's type erasure required careful design to avoid ambiguous overloads

## Key Decisions

1. **Library-owned scheduler vs global scheduler**: Created `SYNC_HANDLER_SCHEDULER` as a static field in `AcpClient` interface with daemon threads. This follows our best practices:
   - Daemon threads (won't prevent JVM exit)
   - Descriptive name prefix `acp-sync-handler`
   - Library-owned (not dependent on Reactor global state)

2. **Deprecation strategy**: Rather than removing async handlers from `SyncSpec`, deprecated them with `*Async` suffix. This allows:
   - Existing code using async patterns to still work
   - IDE warnings to guide users toward sync handlers
   - Gradual migration without breaking changes

3. **Consumer vs Function for session updates**: `sessionUpdateConsumer()` now accepts `Consumer<SessionNotification>` instead of `Function<SessionNotification, Mono<Void>>`. This matches the sync pattern where callbacks don't need to return anything.

## Patterns to Reuse

### Sync-to-Async Handler Conversion
```java
private static <T> AcpClientSession.RequestHandler<T> fromSync(SyncRequestHandler<T> syncHandler) {
    return params -> Mono.fromCallable(() -> syncHandler.handle(params))
            .subscribeOn(SYNC_HANDLER_SCHEDULER);
}
```

### Library-Owned Daemon Scheduler
```java
Scheduler SYNC_HANDLER_SCHEDULER = Schedulers.fromExecutorService(
        Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "acp-sync-handler");
            t.setDaemon(true);
            return t;
        }), "acp-sync-handler");
```

## Patterns to Avoid

1. **Using global `Schedulers.boundedElastic()`**: Causes lingering threads and prevents clean JVM shutdown
2. **Same handler interface for sync and async clients**: Creates confusion about when `Mono<>` is actually needed

## API Comparison

### Before (Confusing)
```java
// Why does a "sync" client require Mono<> in handlers?
AcpSyncClient client = AcpClient.sync(transport)
    .readTextFileHandler(rawParams -> {
        String content = Files.readString(path);
        return Mono.just(new ReadTextFileResponse(content));  // Ceremony!
    })
    .sessionUpdateConsumer(notification -> {
        System.out.println(notification);
        return Mono.empty();  // Why return Mono.empty()?
    })
    .build();
```

### After (Clean)
```java
// Sync handlers return plain values - natural for blocking I/O
AcpSyncClient client = AcpClient.sync(transport)
    .readTextFileHandler(rawParams -> {
        String content = Files.readString(path);
        return new ReadTextFileResponse(content);  // Direct return!
    })
    .sessionUpdateConsumer(notification ->  // Plain Consumer, no return
        System.out.println(notification))
    .build();
```

## Files Modified

| File | Changes |
|------|---------|
| `AcpClient.java` | Added `SyncRequestHandler<T>`, `SYNC_HANDLER_SCHEDULER`, sync handler methods in `SyncSpec` |
| `module-05/StreamingUpdatesClient.java` | Removed Mono import, simplified sessionUpdateConsumer |
| `module-07/AgentRequestsClient.java` | Removed Mono import, simplified handlers |
| `module-08/PermissionsClient.java` | Removed Mono import, simplified permission handler |

## Test Results

- 235 tests total
- 1 pre-existing failure (WebSocket retry test - unrelated)
- All new sync handler functionality works correctly

## Next Steps

- Consider applying the same pattern to `AsyncSpec` if users want typed request handlers
- Step 3.7: Comprehensive MCP SDK Design Alignment Audit should verify we haven't missed other similar patterns
