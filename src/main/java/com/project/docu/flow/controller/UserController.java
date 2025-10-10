package com.project.docu.flow.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

	@GetMapping("/api/me")
	public Map<String, Object> getCurrentUser(Authentication authentication) {
		Map<String, Object> userInfo = new HashMap<>();

		userInfo.put("username", authentication.getName());

		// Extract authorities (roles)
		List<String> roles = new ArrayList<>();
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			roles.add(authority.getAuthority());
		}

		userInfo.put("roles", roles);
		System.out.println(roles);

		return userInfo;
	}
}
