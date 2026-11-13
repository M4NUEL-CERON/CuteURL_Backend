package com.example.cuteurl.controller;

import com.example.cuteurl.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UrlControllerTest {

    private final UrlService service = mock(UrlService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new UrlController(service)).build();

    private org.springframework.test.web.servlet.ResultActions shorten(String json) throws Exception {
        return mvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void shortenReturnsTheShortUrl() throws Exception {
        when(service.isValidUrl("https://example.com")).thenReturn(true);
        when(service.shorten("https://example.com")).thenReturn("https://cute.example/abc123");

        shorten("{\"url\":\"https://example.com\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shortUrl").value("https://cute.example/abc123"));
    }

    @Test
    void shortenRejectsInvalidUrlsWithoutTouchingStorage() throws Exception {
        when(service.isValidUrl(anyString())).thenReturn(false);

        shorten("{\"url\":\"ftp://example.com\"}").andExpect(status().isBadRequest());
        shorten("{\"url\":\"   \"}").andExpect(status().isBadRequest());
        shorten("{}").andExpect(status().isBadRequest());

        verify(service, never()).shorten(anyString());
    }

    @Test
    void knownCodeRedirectsWith302() throws Exception {
        when(service.resolve("abc123")).thenReturn("https://example.com/page");

        mvc.perform(get("/abc123"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://example.com/page"));
    }

    @Test
    void unknownOrExpiredCodeReturns404() throws Exception {
        mvc.perform(get("/nope00")).andExpect(status().isNotFound());
    }
}
