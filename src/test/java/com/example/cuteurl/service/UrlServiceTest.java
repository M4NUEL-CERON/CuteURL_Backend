package com.example.cuteurl.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UrlServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final UrlService service;

    UrlServiceTest() {
        when(redis.opsForValue()).thenReturn(ops);
        service = new UrlService(redis, "https://cute.example", 30);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "http://example.com/a?b=c#d", "https://sub.example.co/path"})
    void acceptsHttpAndHttpsUrlsWithAHost(String url) {
        assertThat(service.isValidUrl(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com", "javascript:alert(1)", "example.com", "https://", "http://exa mple.com", "mailto:a@b.com"})
    void rejectsEverythingElse(String url) {
        assertThat(service.isValidUrl(url)).isFalse();
    }

    @Test
    void rejectsUrlsLongerThanTheLimit() {
        String base = "https://example.com/";
        assertThat(service.isValidUrl(base + "a".repeat(UrlService.MAX_URL_LENGTH - base.length()))).isTrue();
        assertThat(service.isValidUrl(base + "a".repeat(UrlService.MAX_URL_LENGTH - base.length() + 1))).isFalse();
    }

    @Test
    void shortenStoresTheUrlWithTtlAndReturnsAShortLink() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        String shortUrl = service.shorten("https://example.com");

        assertThat(shortUrl).matches("https://cute\\.example/[0-9a-f]{6}");
        String code = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
        verify(ops).setIfAbsent("url:" + code, "https://example.com", Duration.ofDays(30));
    }

    @Test
    void shortenRetriesWhenTheCodeIsAlreadyTaken() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false, false, true);

        service.shorten("https://example.com");

        verify(ops, times(3)).setIfAbsent(anyString(), eq("https://example.com"), any(Duration.class));
    }

    @Test
    void resolveReadsTheStoredUrl() {
        when(ops.get("url:abc123")).thenReturn("https://example.com");

        assertThat(service.resolve("abc123")).isEqualTo("https://example.com");
        assertThat(service.resolve("nope00")).isNull();
    }
}
