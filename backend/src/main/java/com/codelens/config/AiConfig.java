package com.codelens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiConfig {

    @Bean
    RestClient aiRestClient(RestClient.Builder builder, CodeLensProperties props) {
        // uvicorn speaks http/1.1 only
        var http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.ai().timeout());
        return builder.baseUrl(props.ai().baseUrl()).requestFactory(factory).build();
    }
}
