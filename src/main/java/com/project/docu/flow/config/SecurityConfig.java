package com.project.docu.flow.config;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterchain(HttpSecurity http) throws Exception {
		return http.cors(cors -> cors.configure(http)).csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.requestMatchers("/submit", "/submit/**")
						.hasAnyAuthority("ROLE_SUBMITTERS", "Submitters", "SUBMITTERS")
						.requestMatchers("/review", "/review/**")
						.hasAnyAuthority("ROLE_REVIEWERS", "Reviewers", "REVIEWERS")
						.requestMatchers("/approve", "/approve/**")
						.hasAnyAuthority("ROLE_APPROVERS", "Approvers", "APPROVERS").requestMatchers("/", "/login")
						.permitAll().requestMatchers("/api/me").authenticated().anyRequest().authenticated())
				.formLogin(form -> form.loginProcessingUrl("/login").usernameParameter("username")
						.passwordParameter("password").successHandler(authenticationSuccessHandler())
						.failureHandler(authenticationFailureHandler()).permitAll())
				.logout(logout -> logout.permitAll()).build();
	}

	@Bean
	public AuthenticationSuccessHandler authenticationSuccessHandler() {
		return (request, response, authentication) -> {
			response.setStatus(HttpServletResponse.SC_OK);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");

			Map<String, Object> data = new HashMap<>();
			data.put("message", "Login successful");
			data.put("username", authentication.getName());
			data.put("authorities", authentication.getAuthorities());

			ObjectMapper mapper = new ObjectMapper();
			response.getWriter().write(mapper.writeValueAsString(data));
			response.getWriter().flush();
		};
	}

	@Bean
	public AuthenticationFailureHandler authenticationFailureHandler() {
		return (request, response, exception) -> {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");

			Map<String, String> error = new HashMap<>();
			error.put("message", "Invalid username or password: " + exception.getMessage());

			ObjectMapper mapper = new ObjectMapper();
			response.getWriter().write(mapper.writeValueAsString(error));
			response.getWriter().flush();
		};
	}

	@Autowired
	public void configure(AuthenticationManagerBuilder auth) throws Exception {
		auth.ldapAuthentication().userSearchBase("ou=users").userSearchFilter("(uid={0})").groupSearchBase("ou=groups")
				.groupSearchFilter("(uniqueMember={0})").contextSource().url("ldap://localhost:10389/ou=system")
				.managerDn("uid=admin,ou=system").managerPassword("secret").and().groupRoleAttribute("cn")
				.rolePrefix("").authoritiesMapper(this::mapAuthorities);
	}

	private Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
		Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
		for (GrantedAuthority authority : authorities) {
			String authorityName = authority.getAuthority();
			if (authorityName.startsWith("ROLE_")) {
				authorityName = authorityName.substring(5);
			}
			mappedAuthorities.add(new SimpleGrantedAuthority(authorityName));
			String upperName = authorityName.toUpperCase();
			mappedAuthorities.add(new SimpleGrantedAuthority(upperName));
			mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + upperName));
		}
		return mappedAuthorities;
	}
}