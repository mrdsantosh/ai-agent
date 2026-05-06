package com.example.ai_agent.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.PostConstruct;

/**
 * Manages MCP client lifecycle so the application keeps working when an MCP
 * server is temporarily down.
 *
 * Strategy:
 *  - Spring AI auto-configures one McpSyncClient per configured connection,
 *    but with spring.ai.mcp.client.initialized=false they are NOT initialized
 *    at startup. We initialize them ourselves with try/catch.
 *  - getToolCallbacks() returns a fresh, request-time snapshot of callbacks
 *    from currently initialized clients. Down clients contribute nothing.
 *  - A scheduled retry plus a manual /api/mcp/reinitialize endpoint bring
 *    tools back online after the MCP server recovers.
 */
@Service
public class McpToolService {
	private static final Logger logger = LoggerFactory.getLogger(McpToolService.class);
	private static final ToolCallback[] EMPTY = new ToolCallback[0];

	private final List<McpSyncClient> mcpClients;
	private final Map<McpSyncClient, Boolean> initState = new ConcurrentHashMap<>();

	public McpToolService(List<McpSyncClient> mcpClients) {
		this.mcpClients = mcpClients == null ? List.of() : mcpClients;
		if (this.mcpClients.isEmpty()) {
			logger.info("No MCP clients configured");
		} else {
			logger.info("Discovered {} MCP client(s): {}", this.mcpClients.size(), this.mcpClients.stream()
					.map(McpToolService::clientName).collect(Collectors.joining(", ")));
		}
	}

	@PostConstruct
	public void start() {
		initializeAll();
	}

	/**
	 * Attempts to initialize every not-yet-initialized MCP client. Failures are
	 * swallowed and logged so the application keeps starting / serving.
	 *
	 * @return number of clients currently initialized after this call
	 */
	public synchronized int initializeAll() {
		int success = 0;
		for (McpSyncClient client : mcpClients) {
			if (Boolean.TRUE.equals(initState.get(client))) {
				success++;
				continue;
			}
			if (tryInitialize(client)) {
				success++;
			}
		}
		return success;
	}

	/**
	 * Returns the live MCP tool callbacks. Empty array when nothing is reachable
	 * so a normal chat can still proceed without MCP tools.
	 */
	public ToolCallback[] getToolCallbacks() {
		if (mcpClients.isEmpty()) {
			return EMPTY;
		}
		List<McpSyncClient> ready = mcpClients.stream().filter(this::isReady).toList();
		if (ready.isEmpty()) {
			return EMPTY;
		}
		try {
			ToolCallback[] callbacks = SyncMcpToolCallbackProvider.builder().mcpClients(ready).build()
					.getToolCallbacks();
			return callbacks != null ? callbacks : EMPTY;
		} catch (Exception e) {
			// listTools failed mid-flight (server died between init and use)
			logger.warn("Failed to load MCP tool callbacks, marking clients as down: {}", e.getMessage());
			ready.forEach(c -> initState.put(c, false));
			return EMPTY;
		}
	}

	/**
	 * Snapshot of per-client status, useful for /api/mcp/status.
	 */
	public Map<String, Boolean> getStatus() {
		Map<String, Boolean> out = new LinkedHashMap<>();
		for (McpSyncClient client : mcpClients) {
			out.put(clientName(client), Boolean.TRUE.equals(initState.get(client)));
		}
		return out;
	}

	private boolean isReady(McpSyncClient client) {
		if (Boolean.TRUE.equals(initState.get(client))) {
			return true;
		}
		// Lazy init attempt for previously-down clients
		return tryInitialize(client);
	}

	private boolean tryInitialize(McpSyncClient client) {
		String name = clientName(client);
		try {
			client.initialize();
			initState.put(client, true);
			logger.info("Initialized MCP client '{}' ({} tool(s) available)", name, safeToolCount(client));
			return true;
		} catch (Exception e) {
			initState.put(client, false);
			logger.warn("MCP client '{}' is unavailable: {}", name, e.getMessage());
			return false;
		}
	}

	@Scheduled(fixedDelayString = "${app.mcp.retry-interval-ms:30000}", initialDelayString = "${app.mcp.retry-initial-delay-ms:30000}")
	public void retryFailedClients() {
		if (mcpClients.isEmpty()) {
			return;
		}
		long failed = mcpClients.stream().filter(c -> !Boolean.TRUE.equals(initState.get(c))).count();
		if (failed == 0) {
			return;
		}
		logger.debug("Background retry: {} MCP client(s) currently down", failed);
		initializeAll();
	}

	private int safeToolCount(McpSyncClient client) {
		try {
			return client.listTools().tools().size();
		} catch (Exception e) {
			return 0;
		}
	}

	private static String clientName(McpSyncClient client) {
		try {
			return client.getClientInfo().name();
		} catch (Exception e) {
			return "<unknown>";
		}
	}
}
