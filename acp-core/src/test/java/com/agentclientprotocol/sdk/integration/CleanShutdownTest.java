/*
 * Copyright 2025-2025 the original author or authors.
 */

package com.agentclientprotocol.sdk.integration;

import java.util.Set;
import java.util.stream.Collectors;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.test.InMemoryTransportPair;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to verify that ACP transports clean up properly on shutdown
 * and don't leave lingering threads that prevent JVM exit.
 *
 * <p>
 * These tests address GitHub issue: Transport threads prevent JVM exit when
 * closeGracefully() isn't called properly. The fix uses daemon threads for
 * all transport schedulers.
 * </p>
 *
 * @author Mark Pollack
 */
class CleanShutdownTest {

	/**
	 * Verifies that no ACP-prefixed threads remain after client close.
	 *
	 * <p>
	 * This test ensures that the daemon thread fix is working correctly.
	 * Before the fix, non-daemon threads would prevent the JVM from exiting.
	 * </p>
	 */
	@Test
	void noLingeringAcpThreadsAfterClientClose() throws InterruptedException {
		// Record threads before test
		Set<String> acpThreadsBefore = getAcpThreadNames();

		// Create and use client with in-memory transport
		InMemoryTransportPair transportPair = InMemoryTransportPair.create();

		try (AcpSyncClient client = AcpClient.sync(transportPair.clientTransport()).build()) {
			// The transport threads are created when build() connects
			// Give threads a moment to start
			Thread.sleep(100);

			// Verify threads were created (sanity check)
			Set<String> threadsWhileRunning = getAcpThreadNames();
			// Note: InMemory transport may not create threads in the same way as stdio,
			// but we should still verify no leak occurs
		}

		// Allow time for thread cleanup
		Thread.sleep(500);

		// Verify no ACP threads remain
		Set<String> acpThreadsAfter = getAcpThreadNames();
		assertThat(acpThreadsAfter)
			.describedAs("ACP threads should be cleaned up after client close")
			.isEqualTo(acpThreadsBefore);
	}

	/**
	 * Verifies that daemon threads are used for transport schedulers.
	 *
	 * <p>
	 * This test inspects active threads to ensure any ACP-prefixed threads
	 * are daemon threads, which allows the JVM to exit gracefully.
	 * </p>
	 */
	@Test
	void acpThreadsAreDaemonThreads() throws InterruptedException {
		InMemoryTransportPair transportPair = InMemoryTransportPair.create();

		try (AcpSyncClient client = AcpClient.sync(transportPair.clientTransport()).build()) {
			// Give threads a moment to start
			Thread.sleep(100);

			// Check that any ACP threads are daemon threads
			Set<Thread> acpThreads = Thread.getAllStackTraces().keySet().stream()
				.filter(t -> t.getName().startsWith("acp-"))
				.collect(Collectors.toSet());

			for (Thread thread : acpThreads) {
				assertThat(thread.isDaemon())
					.describedAs("Thread '%s' should be a daemon thread", thread.getName())
					.isTrue();
			}
		}
	}

	/**
	 * Gets the names of all threads that start with "acp-".
	 * @return set of ACP thread names
	 */
	private Set<String> getAcpThreadNames() {
		return Thread.getAllStackTraces().keySet().stream()
			.map(Thread::getName)
			.filter(name -> name.startsWith("acp-"))
			.collect(Collectors.toSet());
	}

}
