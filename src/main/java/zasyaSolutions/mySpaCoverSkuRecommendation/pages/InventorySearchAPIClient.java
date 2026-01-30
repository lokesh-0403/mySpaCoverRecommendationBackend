package zasyaSolutions.mySpaCoverSkuRecommendation.pages;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Page Object for Inventory Search API
 */
public class InventorySearchAPIClient {
    
    private String baseURL;
    private String endpoint;
    
    public InventorySearchAPIClient(String baseURL, String endpoint) {
        this.baseURL = baseURL;
        this.endpoint = endpoint;
        RestAssured.baseURI = baseURL;
    }
    
    /**
     * Create request specification with headers
     */
    private RequestSpecification createRequestSpec() {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }
    
    /**
     * Search inventory with SKU array
     */
    public Response searchInventoryBySKU(List<String> skuArray) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", skuArray);
        
        return createRequestSpec()
                .body(payload)
                .when()
                .post(endpoint)
                .then()
                .extract()
                .response();
    }
    
    /**
     * Search inventory with custom payload
     */
    public Response searchInventoryWithPayload(Map<String, Object> payload) {
        return createRequestSpec()
                .body(payload)
                .when()
                .post(endpoint)
                .then()
                .extract()
                .response();
    }
    
    /**
     * Search inventory and return status code
     */
    public int searchInventoryAndGetStatusCode(List<String> skuArray) {
        return searchInventoryBySKU(skuArray).getStatusCode();
    }
    
    /**
     * Search inventory and get response body as string
     */
    public String searchInventoryAndGetBody(List<String> skuArray) {
        return searchInventoryBySKU(skuArray).getBody().asString();
    }
    
    /**
     * Verify inventory response contains expected data
     */
    public boolean verifyInventoryExists(List<String> skuArray) {
        Response response = searchInventoryBySKU(skuArray);
        return response.getStatusCode() == 200 && 
               response.jsonPath().get("inventory") != null;
    }
    
    /**
     * Get inventory items from response
     */
    public List<Map<String, Object>> getInventoryItems(List<String> skuArray) {
        Response response = searchInventoryBySKU(skuArray);
        if (response.getStatusCode() == 200) {
            return response.jsonPath().getList("inventory");
        }
        return null;
    }
    
    /**
     * Get inbound items from response
     */
    public List<Map<String, Object>> getInboundItems(List<String> skuArray) {
        Response response = searchInventoryBySKU(skuArray);
        if (response.getStatusCode() == 200) {
            return response.jsonPath().getList("inbound");
        }
        return null;
    }
    
    /**
     * Set custom headers for request
     */
    public Response searchWithHeaders(List<String> skuArray, Map<String, String> headers) {
        RequestSpecification spec = createRequestSpec();
        
        for (Map.Entry<String, String> header : headers.entrySet()) {
            spec.header(header.getKey(), header.getValue());
        }
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", skuArray);
        
        return spec.body(payload)
                .when()
                .post(endpoint)
                .then()
                .extract()
                .response();
    }
}


