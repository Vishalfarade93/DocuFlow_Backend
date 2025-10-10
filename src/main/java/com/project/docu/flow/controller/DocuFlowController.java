package com.project.docu.flow.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocuFlowController {
	@GetMapping("/")
	public String home() {
		return "Wlecome to docuflow home have a great day ";
	}

}
