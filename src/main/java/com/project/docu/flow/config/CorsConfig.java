package com.project.docu.flow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
	@Bean
	public WebMvcConfigurer configurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/**")
		        .allowedOriginPatterns("http://localhost:5173") // ✅ Specific origin
		        .allowedMethods("GET", "POST", "PUT", "DELETE","OPTIONS")
		        .allowCredentials(true);

			}
		};
	}
}
