package com.project.docu.flow.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.docu.flow.service.LdapUserService;

@RestController
public class DocuFlowController {
	
	@Autowired
	private LdapUserService ldapUserService;
	
	@GetMapping("/")
	public List<String> home() {
		System.out.println("called.................................................................................");
		return ldapUserService.getEmailsByGroup("Reviewers");
	}

}
