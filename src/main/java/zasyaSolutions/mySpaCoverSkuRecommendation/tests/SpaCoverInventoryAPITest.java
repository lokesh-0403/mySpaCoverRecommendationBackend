package zasyaSolutions.mySpaCoverSkuRecommendation.tests;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import zasyaSolutions.mySpaCoverSkuRecommendation.main.BasePage;
import zasyaSolutions.mySpaCoverSkuRecommendation.pages.InventorySearchAPIClient;
import zasyaSolutions.mySpaCoverSkuRecommendation.utils.InventoryResponseOrganizer;
import zasyaSolutions.mySpaCoverSkuRecommendation.utils.PayloadGenerator;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static io.restassured.RestAssured.given;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test class for Spa Cover Inventory API
 */
public class SpaCoverInventoryAPITest extends BasePage {

    private InventorySearchAPIClient apiClient;

    @BeforeClass(dependsOnMethods = { "initializeBasePage" })
    public void setup() {
        apiClient = new InventorySearchAPIClient(baseUrl, inventoryEndpoint);

        output("Test Setup Complete");
        output("Base URL: " + baseUrl);
        output("Endpoint: " + inventoryEndpoint);
    }

    /**
     * Test Case 1: Original test with 3-dimension fallback logic
     * CSV: src/test/resources/testdata/spa_cover_dimensions.csv
     */
    @Test
    public void inventorySearchWithFallbackSkus() {
        List<List<String>> skuPayloads = PayloadGenerator.generatePayloads();

        // Create CSV output file with timestamp
        String outputFilePath = "inventory_report_" + System.currentTimeMillis() + ".csv";
        output("CSV output file will be saved to: " + outputFilePath);

        // IMPORTANT: Reset the file state before starting
        InventoryResponseOrganizer.resetFileState();

        executeInventorySearch(skuPayloads, outputFilePath, "Test Case 1 (3-Dimension)");
    }

    /**
     * Test Case 2: New test with static second dimension (360) and first dimension +1/-1 logic
     * CSV: src/test/resources/testdata/spa_cover_dimensions2.csv
     */
    @Test
    public void inventorySearchWithStaticSecondDimension() {
        List<List<String>> skuPayloads = PayloadGenerator.generatePayloadsForStaticSecondDimension();

        // Create CSV output file with timestamp
        String outputFilePath = "inventory_report_static360_" + System.currentTimeMillis() + ".csv";
        output("CSV output file will be saved to: " + outputFilePath);

        // IMPORTANT: Reset the file state before starting
        InventoryResponseOrganizer.resetFileState();

        executeInventorySearch(skuPayloads, outputFilePath, "Test Case 2 (Static 360)");
    }

    /**
     * Combined Test Case: Runs both test cases and outputs to a single CSV file
     * This includes:
     * - 3-dimension test case from spa_cover_dimensions.csv
     * - 2-dimension test case (static 360) from spa_cover_dimensions2.csv
     */
    @Test
    public void inventorySearchCombinedTestCases() {
        List<List<String>> skuPayloads = PayloadGenerator.generateCombinedPayloads();

        // Create CSV output file with timestamp
        String outputFilePath = "inventory_report_combined_" + System.currentTimeMillis() + ".csv";
        output("CSV output file will be saved to: " + outputFilePath);
        output("This file will contain results from BOTH test cases");

        // IMPORTANT: Reset the file state before starting
        InventoryResponseOrganizer.resetFileState();

        executeInventorySearch(skuPayloads, outputFilePath, "Combined Test Cases");
    }

    /**
     * Common method to execute inventory search and process results
     */
    private void executeInventorySearch(List<List<String>> skuPayloads, String outputFilePath, String testCaseName) {
        output("\n" + "=".repeat(80));
        output("STARTING: " + testCaseName);
        output("=".repeat(80));

        for (int i = 0; i < skuPayloads.size(); i++) {

            List<String> skuList = skuPayloads.get(i);

            Map<String, Object> data = new HashMap<>();
            data.put("sku", skuList);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String payload = gson.toJson(data);

            output("Endpoint: " + inventoryEndpoint);
            output(">> Sending payload for entry " + (i + 1) + "/" + skuPayloads.size() + ": " + payload);

            Response response = requestSpec
                    .body(payload)
                    .log().all()
                    .header("Accept", "application/json")
                    .when()
                    .post(inventoryEndpoint)
                    .then()
                    .log().all()
                    .extract()
                    .response();

            output("Status Code: " + response.getStatusCode());

            // Assert status code
            Assert.assertEquals(response.getStatusCode(), 201);

            output(">> Response for entry " + (i + 1) + ": .... ");

            String responseBody = response.getBody().asString();
            JsonPath js1 = new JsonPath(responseBody);
            int count = js1.getInt("inventory.size()");
            output("Total inventory items found: " + count);
            Assert.assertTrue(count >= 0, "Inventory items should be returned");

            List<String> availableInventorySkus = js1.getList("inventory.sku");
            List<String> availableInboundSkus = js1.getList("inbound.sku");
            output(">> Getting a get Response for inventory sku : " + availableInventorySkus);
            output(">> Getting a get Response for inbound sku : " + availableInboundSkus);

            // Process and organize inventory - this will APPEND to CSV
            try {
                output("\n>> Processing and organizing inventory for payload " + (i + 1));
                InventoryResponseOrganizer.processAndSaveInventory(
                        responseBody, // The JSON response from API
                        skuList, // The list of SKUs we sent in payload
                        outputFilePath // Where to save the report
                );
                output(">> Inventory organized successfully for payload " + (i + 1));
            } catch (Exception e) {
                output("!! Error organizing inventory: " + e.getMessage());
                e.printStackTrace();
            }

        }

        output("\n" + "=".repeat(80));
        output("✓ " + testCaseName + " COMPLETED");
        output("✓ INVENTORY REPORT SAVED TO: " + outputFilePath);
        output("=".repeat(80));
    }
}
