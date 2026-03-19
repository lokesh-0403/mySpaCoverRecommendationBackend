package zasyaSolutions.mySpaCoverSkuRecommendation.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import zasyaSolutions.mySpaCoverSkuRecommendation.config.AppProperties;

@Service
public class InventoryApiService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;

    public InventoryApiService(AppProperties appProperties) {
        this.appProperties = appProperties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) appProperties.getInventory().getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) appProperties.getInventory().getReadTimeout().toMillis());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public String login() {
        validateInventoryConfiguration();

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("email", appProperties.getInventory().getLoginEmail());
        payload.put("password", appProperties.getInventory().getLoginPassword());

        ResponseEntity<String> response = exchange(
            buildUri(appProperties.getInventory().getLoginEndpoint()),
            payload,
            buildJsonHeaders()
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Inventory API login failed with status " + response.getStatusCode().value());
        }

        JsonObject body = parseJson(response.getBody());
        String token = extractText(body, "token");
        if (!StringUtils.hasText(token)) {
            token = body.has("data") && body.get("data").isJsonObject()
                ? extractText(body.getAsJsonObject("data"), "token")
                : "";
        }
        if (!StringUtils.hasText(token)) {
            token = extractText(body, "access_token");
        }
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Inventory API login succeeded but no auth token was returned");
        }

        return token;
    }

    public String searchInventory(List<String> skuPayload, String authToken) {
        if (!StringUtils.hasText(appProperties.getInventory().getWebhookKey())) {
            throw new IllegalStateException("Inventory API webhook key is not configured");
        }

        HttpHeaders headers = buildJsonHeaders();
        headers.setBearerAuth(authToken);
        headers.set("X-Webhook-Key", appProperties.getInventory().getWebhookKey());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", skuPayload);

        ResponseEntity<String> response = exchange(
            buildUri(appProperties.getInventory().getInventoryEndpoint()),
            payload,
            headers
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Inventory API request failed with status " + response.getStatusCode().value());
        }
        if (!StringUtils.hasText(response.getBody())) {
            throw new IllegalStateException("Inventory API returned an empty response body");
        }

        return response.getBody();
    }

    private ResponseEntity<String> exchange(String uri, Object body, HttpHeaders headers) {
        try {
            HttpEntity<Object> requestEntity = new HttpEntity<>(body, headers);
            return restTemplate.exchange(uri, HttpMethod.POST, requestEntity, String.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Inventory API request failed: " + exception.getMessage(), exception);
        }
    }

    private String buildUri(String endpoint) {
        String baseUrl = appProperties.getInventory().getBaseUrl();
        if (baseUrl.endsWith("/") && endpoint.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + endpoint;
        }
        if (!baseUrl.endsWith("/") && !endpoint.startsWith("/")) {
            return baseUrl + "/" + endpoint;
        }
        return baseUrl + endpoint;
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private void validateInventoryConfiguration() {
        if (!StringUtils.hasText(appProperties.getInventory().getBaseUrl())) {
            throw new IllegalStateException("Inventory API base URL is not configured");
        }
        if (!StringUtils.hasText(appProperties.getInventory().getLoginEmail())
            || !StringUtils.hasText(appProperties.getInventory().getLoginPassword())) {
            throw new IllegalStateException("Inventory API credentials are not configured");
        }
    }

    private JsonObject parseJson(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception exception) {
            throw new IllegalStateException("Inventory API returned invalid JSON", exception);
        }
    }

    private String extractText(JsonObject node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isJsonNull()) {
            return "";
        }
        String value = node.get(fieldName).getAsString();
        return value == null ? "" : value.trim();
    }
}
