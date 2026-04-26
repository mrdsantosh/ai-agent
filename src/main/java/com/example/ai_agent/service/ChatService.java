package com.example.ai_agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

@Service
public class ChatService {
	private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

	private final ChatClient chatClient;

	public ChatService(ChatClient.Builder chatClientBuilder) {
		this.chatClient = chatClientBuilder.build();
	}

	public Flux<String> processChat(String prompt) {
		logger.info("Processing streaming chat request - prompt: '{}'", prompt);
		try {
			return chatClient.prompt().user(prompt).stream().content();
		} catch (Exception e) {
			logger.error("Error processing streaming chat request", e);
			return Flux.just("I don't know - there was an error processing your request.");
		}
	}
}
