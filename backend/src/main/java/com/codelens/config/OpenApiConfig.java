package com.codelens.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI codeLensApi() {
        return new OpenAPI().info(new Info()
                .title("CodeLens API")
                .description("Code intelligence and change impact analysis")
                .version("v1"));
    }
}
