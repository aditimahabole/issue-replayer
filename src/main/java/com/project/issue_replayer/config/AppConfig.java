package com.project.issue_replayer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * WHY THIS CLASS?
 * RestTemplate is not auto-created by Spring Boot.
 * We need to tell Spring: "Create ONE RestTemplate and reuse it everywhere."
 * 
 * @Configuration = "This class contains Spring setup/config"
 * @Bean = "Create this object and manage it for me"
 * 
 * Now anywhere in the app, we can inject RestTemplate and use it.
 */
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
