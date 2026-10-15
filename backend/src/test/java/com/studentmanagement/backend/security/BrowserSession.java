package com.studentmanagement.backend.security;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives MockMvc like a browser tab: keeps a cookie jar (honouring Max-Age=0
 * deletions) and, like the frontend's Axios interceptor, copies the
 * XSRF-TOKEN cookie into the X-XSRF-TOKEN header on unsafe requests.
 */
final class BrowserSession {

    static final String CSRF_COOKIE = "XSRF-TOKEN";
    static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final MockMvc mockMvc;
    private final String clientAddress;
    private final Map<String, Cookie> jar = new LinkedHashMap<>();

    BrowserSession(MockMvc mockMvc, String clientAddress) {
        this.mockMvc = mockMvc;
        this.clientAddress = clientAddress;
    }

    MvcResult get(String path) throws Exception {
        return send(MockMvcRequestBuilders.get(path));
    }

    MvcResult post(String path, String json) throws Exception {
        return unsafe(MockMvcRequestBuilders.post(path), json, true);
    }

    MvcResult postWithoutCsrfHeader(String path, String json) throws Exception {
        return unsafe(MockMvcRequestBuilders.post(path), json, false);
    }

    MvcResult put(String path, String json) throws Exception {
        return unsafe(MockMvcRequestBuilders.put(path), json, true);
    }

    MvcResult delete(String path) throws Exception {
        return unsafe(MockMvcRequestBuilders.delete(path), null, true);
    }

    String cookie(String name) {
        Cookie cookie = jar.get(name);
        return cookie == null ? null : cookie.getValue();
    }

    void setCookie(String name, String value) {
        jar.put(name, new Cookie(name, value));
    }

    void dropCookie(String name) {
        jar.remove(name);
    }

    static List<String> setCookieHeaders(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie");
    }

    static String setCookieHeader(MvcResult result, String name) {
        return setCookieHeaders(result).stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElse(null);
    }

    private MvcResult unsafe(MockHttpServletRequestBuilder builder, String json, boolean withCsrfHeader)
            throws Exception {
        if (withCsrfHeader) {
            if (!jar.containsKey(CSRF_COOKIE)) {
                get("/api/auth/csrf");
            }
            builder.header(CSRF_HEADER, cookie(CSRF_COOKIE));
        }
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return send(builder);
    }

    private MvcResult send(MockHttpServletRequestBuilder builder) throws Exception {
        if (!jar.isEmpty()) {
            builder.cookie(jar.values().toArray(Cookie[]::new));
        }
        builder.with(request -> {
            request.setRemoteAddr(clientAddress);
            return request;
        });

        MvcResult result = mockMvc.perform(builder).andReturn();
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                jar.remove(cookie.getName());
            } else {
                jar.put(cookie.getName(), cookie);
            }
        }
        return result;
    }
}
