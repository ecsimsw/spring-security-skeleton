package com.ecsimsw.common.service;

import com.ecsimsw.common.support.utils.ClientKeyUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import static com.ecsimsw.common.config.LogConfig.TRACE_ID;
import static com.ecsimsw.common.config.LogConfig.TRACE_ID_HEADER;

@Service
public class InternalCommunicateService {

    private final RestTemplate restTemplate;

    @Value("${service.gateway}")
    public String gateway;

    public InternalCommunicateService(RestTemplateBuilder builder) {
        this.restTemplate = builder
            .setConnectTimeout(java.time.Duration.ofSeconds(5))
            .setReadTimeout(java.time.Duration.ofSeconds(5))
            .build();
    }

    public <T> ResponseEntity<T> request(HttpMethod method, String path, Class<T> type) {
        return request(method, path, null, type);
    }

    public <T> ResponseEntity<T> request(HttpMethod method, String path, Object requestBody, Class<T> type) {
        try {
            var url = gateway + path;
            return restTemplate.exchange(url, method, httpEntity(requestBody), type);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                .headers(ex.getResponseHeaders())
                .body((T) ex.getResponseBodyAsString());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body((T) ("Internal Server Error: " + ex.getMessage()));
        }
    }

    private static HttpEntity<Object> httpEntity(Object requestBody) {
        var headers = new HttpHeaders();
        headers.set("X-Client-Key", ClientKeyUtils.init());
        headers.set(TRACE_ID_HEADER, MDC.get(TRACE_ID));
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        if(requestBody != null) {
            return new HttpEntity<>(requestBody, headers);
        }
        return new HttpEntity<>(headers);
    }
}

