package com.example.ai_agent.controller;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ai_agent.service.ChatMemoryService;
import com.example.ai_agent.service.ChatService;
import com.example.ai_agent.service.ConversationSummaryService;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("api/chat")
public class ChatController {
	private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

	private final ChatService chatService;
	private final ChatMemoryService chatMemoryService;
	private final ConversationSummaryService summaryService;

	public ChatController(ChatService chatService, ChatMemoryService chatMemoryService,
			ConversationSummaryService summaryService) {
		this.chatService = chatService;
		this.chatMemoryService = chatMemoryService;
		this.summaryService = summaryService;
	}

	@PostMapping(value = "message", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Flux<String> chat(@RequestBody ChatRequest request, Principal principal) {
		String userId = getUserId(request.userId(), principal);
		chatMemoryService.setCurrentUserId(userId);
		return chatService.processChat(request.prompt());
	}

	@PostMapping("summarize")
	public String summarize(@RequestBody(required = false) SummarizeRequest request, Principal principal) {
		try {
			String userId = getUserId(request != null ? request.userId() : null, principal);
			return summaryService.summarizeAndSave(userId);
		} catch (Exception e) {
			logger.error("Error summarizing conversation", e);
			return "Failed to summarize conversation. Please try again.";
		}
	}

	private String getUserId(String requestUserId, Principal principal) {
		// Production: use authenticated user from Spring Security
		if (principal != null) {
			return principal.getName().toLowerCase();
		}
		// Development: use provided userId or default
		return requestUserId != null ? requestUserId.toLowerCase() : "user1";
	}

	public record ChatRequest(String prompt, String userId) {
	}

	public record SummarizeRequest(String userId) {
	}
}
