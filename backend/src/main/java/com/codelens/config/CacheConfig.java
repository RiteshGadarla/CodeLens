package com.codelens.config;

import com.codelens.api.dto.GraphView;
import com.codelens.api.dto.HotspotList;
import com.codelens.api.dto.ModuleMetricList;
import com.codelens.api.dto.OverviewDto;
import com.codelens.impact.ImpactResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

// query results in redis; keys carry the graph version so a new analysis never serves stale data
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String IMPACT = "impact";
    public static final String OVERVIEW = "overview";
    public static final String HOTSPOTS = "hotspots";
    public static final String MODULES = "modules";
    public static final String GRAPH = "graph";

    @Bean
    RedisCacheManagerBuilderCustomizer redisCaches(ObjectMapper mapper,
                                                   @Value("${spring.cache.redis.time-to-live:30m}") Duration ttl) {
        return builder -> builder
                .withCacheConfiguration(IMPACT, typed(mapper, ImpactResult.class, ttl))
                .withCacheConfiguration(OVERVIEW, typed(mapper, OverviewDto.class, ttl))
                .withCacheConfiguration(HOTSPOTS, typed(mapper, HotspotList.class, ttl))
                .withCacheConfiguration(MODULES, typed(mapper, ModuleMetricList.class, ttl))
                .withCacheConfiguration(GRAPH, typed(mapper, GraphView.class, ttl));
    }

    // redis down should degrade to no cache, not errors
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    private static RedisCacheConfiguration typed(ObjectMapper mapper, Class<?> type, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .prefixCacheNameWith("codelens:")
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, type)));
    }
}
