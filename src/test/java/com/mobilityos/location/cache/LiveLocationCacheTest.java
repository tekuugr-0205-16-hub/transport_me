package com.mobilityos.location.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveLocationCacheTest {

    @Test
    @SuppressWarnings("unchecked")
    void getsManyLastPingTimesWithOneHashMultiGet() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        HashOperations<String, String, String> hashOperations = mock(HashOperations.class);
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        Instant first = Instant.parse("2026-08-27T08:00:00Z");
        Instant second = Instant.parse("2026-08-27T08:00:05Z");
        when(hashOperations.multiGet(
                eq("vehicles:live:last-ping"),
                eq(List.of("10", "20", "30"))
        )).thenReturn(Arrays.asList(first.toString(), null, second.toString()));

        LiveLocationCache cache = new LiveLocationCache(redisTemplate);

        Map<Long, Instant> result = cache.getLastPingTimes(List.of(10L, 20L, 30L));

        assertEquals(Map.of(10L, first, 30L, second), result);
        verify(hashOperations, times(1)).multiGet(any(), any());
    }
}
