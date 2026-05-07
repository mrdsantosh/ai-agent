package com.example.ai_agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import com.example.ai_agent.tool.DateTimeService;
import com.example.ai_agent.tool.WeatherService;
import reactor.core.publisher.Flux;

@Service
public class ChatService {
  private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

  public static final String SYSTEM_PROMPT = """
      You are a helpful AI Agent.

      Guidelines:
      1. Use markdown tables for structured data
      2. If unsure, say "I don't know"

      Output rules (MANDATORY):
      - Return the result ONLY as a markdown table

      """;
  private final ChatClient chatClient;

  public ChatService(ChatClient.Builder chatClientBuilder, @NonNull ChatMemory chatMemory,
      ChatModel chatModel, @NonNull VectorStore vectorStore, DateTimeService dateTimeService,
      WeatherService weatherService, ToolCallbackProvider tools) {
    this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(),
            QuestionAnswerAdvisor.builder(vectorStore).build(), new SimpleLoggerAdvisor())
        .defaultTools(dateTimeService, weatherService) // Custom tools
        .defaultToolCallbacks(tools) // MCP tools
        .build();
  }

  public Flux<String> processChat(@NonNull String prompt, @NonNull String userId) {
    logger.info("Processing chat for user {}: '{}'", userId, prompt);
    try {
      return chatClient.prompt().user(prompt)
          .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId)).stream().content();
    } catch (Exception e) {
      logger.error("Error processing streaming chat request", e);
      return Flux.just("I don't know - there was an error processing your request.");
    }
  }
}
