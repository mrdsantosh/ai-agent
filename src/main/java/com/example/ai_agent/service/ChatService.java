package com.example.ai_agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

@Service
public class ChatService {
	private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

	private final ChatClient chatClient;
	public static final String SYSTEM_PROMPT = """
			You are a helpful AI Agent for travel and expenses.

			Guidelines:
			1. Use markdown tables for structured data
			2. If unsure, say "I don't know"
			""";
	private final ChatMemoryService chatMemoryService;

	public ChatService(ChatClient.Builder chatClientBuilder, ChatMemoryService chatMemoryService, @NonNull VectorStore vectorStore) {
		this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT)
		.defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())  // RAG for policies
		.build();
		this.chatMemoryService = chatMemoryService;
	}

	public Flux<String> processChat(String prompt) {
		logger.info("Processing chat: '{}'", prompt);
		try {
			return chatMemoryService.callWithMemory(chatClient, prompt);
		} catch (Exception e) {
			logger.error("Error processing streaming chat request", e);
			return Flux.just("I don't know - there was an error processing your request.");
		}
	}
}
