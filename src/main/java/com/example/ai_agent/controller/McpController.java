package com.example.ai_agent.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ai_agent.service.McpToolService;

@RestController
@RequestMapping("api/mcp")
public class McpController {

	private final McpToolService mcpToolService;

	public McpController(McpToolService mcpToolService) {
		this.mcpToolService = mcpToolService;
	}

	@GetMapping("status")
	public Map<String, Object> status() {
		Map<String, Boolean> clients = mcpToolService.getStatus();
		int toolCount = mcpToolService.getToolCallbacks().length;
		return Map.of("clients", clients, "toolCount", toolCount);
	}

	@PostMapping("reinitialize")
	public Map<String, Object> reinitialize() {
		int initialized = mcpToolService.initializeAll();
		int toolCount = mcpToolService.getToolCallbacks().length;
		return Map.of("initializedClients", initialized, "toolCount", toolCount,
				"clients", mcpToolService.getStatus());
	}
}
