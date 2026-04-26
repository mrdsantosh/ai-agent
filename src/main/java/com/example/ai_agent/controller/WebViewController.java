package com.example.ai_agent.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebViewController {
	@Value("${ui.features.multi-user:true}")
	private boolean multiUserEnabled;

	@GetMapping("/")
	public String index(Model model) {
		model.addAttribute("multiUserEnabled", multiUserEnabled);
		return "chat";
	}
}
