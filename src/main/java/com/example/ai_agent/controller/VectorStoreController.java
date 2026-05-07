package com.example.ai_agent.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ai_agent.service.VectorStoreService;

@RestController
@RequestMapping("api/admin")
public class VectorStoreController {
  private final VectorStoreService vectorStoreService;

  public VectorStoreController(VectorStoreService vectorStoreService) {
    this.vectorStoreService = vectorStoreService;
  }

  @PostMapping("rag-load")
  public void loadDataToVectorStore(@RequestBody String content) {
    vectorStoreService.addContent(content);
  }
}
