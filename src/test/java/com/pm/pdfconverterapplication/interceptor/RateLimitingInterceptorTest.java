package com.pm.pdfconverterapplication.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitingInterceptorTest {

    @Test
    void sixteenthRequestIsRejectedWithRetryAfter() throws Exception {
        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(false, "", new ObjectMapper());
        MockHttpServletRequest request = request("10.0.0.1");

        for (int i = 0; i < 15; i++) {
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(request, response, new Object()));
        assertEquals(429, response.getStatus());
        assertEquals("3600", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("error"));
        assertTrue(!response.getContentAsString().contains("clientIp"));
    }

    @Test
    void separateAddressesHaveSeparateBuckets() throws Exception {
        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(false, "", new ObjectMapper());

        for (int i = 0; i < 15; i++) {
            assertTrue(interceptor.preHandle(request("10.0.0.1"), new MockHttpServletResponse(), new Object()));
        }

        assertTrue(interceptor.preHandle(request("10.0.0.2"), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void forwardedHeadersRequireTrustedPeer() throws Exception {
        RateLimitingInterceptor untrusted = new RateLimitingInterceptor(true, "127.0.0.1", new ObjectMapper());
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest request = request("192.0.2.10");
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            assertTrue(untrusted.preHandle(request, new MockHttpServletResponse(), new Object()));
        }
        MockHttpServletRequest untrustedRequest = request("192.0.2.10");
        untrustedRequest.addHeader("X-Forwarded-For", "198.51.100.99");
        assertEquals(false, untrusted.preHandle(untrustedRequest, new MockHttpServletResponse(), new Object()));

        RateLimitingInterceptor trusted = new RateLimitingInterceptor(true, "127.0.0.1", new ObjectMapper());
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest request = request("127.0.0.1");
            request.addHeader("X-Forwarded-For", "198.51.100.10");
            assertTrue(trusted.preHandle(request, new MockHttpServletResponse(), new Object()));
        }
        MockHttpServletRequest trustedRequest = request("127.0.0.1");
        trustedRequest.addHeader("X-Forwarded-For", "198.51.100.11");
        assertTrue(trusted.preHandle(trustedRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void forwardedAddressesAreParsedFromRightAndSkipTrustedHops() throws Exception {
        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(true, "10.0.0.1,10.0.0.2", new ObjectMapper());

        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest request = request("10.0.0.2");
            request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1, 10.0.0.2");
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        }

        MockHttpServletRequest rejected = request("10.0.0.2");
        rejected.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1, 10.0.0.2");
        assertEquals(false, interceptor.preHandle(rejected, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void garbageForwardedAddressesFallBackToRemoteAddress() throws Exception {
        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(true, "10.0.0.2", new ObjectMapper());

        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest request = request("10.0.0.2");
            request.addHeader("X-Forwarded-For", "not-an-ip, also-garbage");
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        }

        MockHttpServletRequest rejected = request("10.0.0.2");
        rejected.addHeader("X-Forwarded-For", "not-an-ip, also-garbage");
        assertEquals(false, interceptor.preHandle(rejected, new MockHttpServletResponse(), new Object()));
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.setMethod("GET");
        request.setRequestURI("/api/convert");
        return request;
    }
}
