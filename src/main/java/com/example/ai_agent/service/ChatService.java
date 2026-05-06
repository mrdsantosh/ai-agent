package com.example.ai_agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.example.ai_agent.tool.DateTimeService;
import com.example.ai_agent.tool.WeatherService;

import reactor.core.publisher.Flux;

@Service
public class ChatService {
	private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

	private final ChatClient chatClient;
	public static final String SYSTEM_PROMPT = """
			You are a helpful AI Agent.
			
			Guidelines:
			1. Use markdown tables for structured data
			2. If unsure, say "I don't know"

			Output rules (MANDATORY):
			- Return the result ONLY as a markdown table

			""";
	private final ChatMemoryService chatMemoryService;
	private final McpToolService mcpToolService;

	public ChatService(ChatClient.Builder chatClientBuilder, ChatMemoryService chatMemoryService,
			@NonNull VectorStore vectorStore, DateTimeService dateTimeService, WeatherService weatherService,
			McpToolService mcpToolService) {
		// MCP tools are intentionally NOT baked in here. They are attached
		// per-request via mcpToolService.getToolCallbacks() so chat keeps
		// working when the MCP server is down and recovers automatically
		// when it comes back online.
		this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT)
				.defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
				.defaultTools(dateTimeService, weatherService)
				.build();
		this.chatMemoryService = chatMemoryService;
		this.mcpToolService = mcpToolService;
	}

	public Flux<String> processChat(String prompt) {
		logger.info("Processing chat: '{}'", prompt);
		return Flux.defer(
				() -> chatMemoryService.callWithMemory(chatClient, prompt, mcpToolService.getToolCallbacks()))
				.onErrorResume(e -> {
					logger.error("Error processing streaming chat request", e);
					return Flux.just("I don't know - there was an error processing your request.");
				});
	}
}
