package zasyaSolutions.mySpaCoverSkuRecommendation.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import zasyaSolutions.mySpaCoverSkuRecommendation.main.ConfigReader;
import zasyaSolutions.mySpaCoverSkuRecommendation.model.SpaCoverDimension;

public class PayloadGenerator {

    /**
     * Generate payloads from the first CSV file (3-dimension test case)
     * Path: src/test/resources/testdata/spa_cover_dimensions.csv
     */
    public static List<List<String>> generatePayloads() {
        String csvPath = "src/main/resources/testdata/spa_cover_dimensions.csv";

        List<List<String>> payloads = new ArrayList<>();

        // Step 1: Read CSV
        List<SpaCoverDimension> dimensions = CsvDimensionReader.readDimensions(csvPath);

        // Step 2: Generate payload PER entry
        for (SpaCoverDimension original : dimensions) {

            List<SpaCoverDimension> fallbacks = FallbackDimensionGenerator.generateFallbacks(original);

            List<String> skuPayload = FallbackDimensionGenerator.generateSKUArray(fallbacks);

            System.out.println("Payload for " + original + " with fallback skus = " + skuPayload.size() + ": " + skuPayload);

            payloads.add(skuPayload);
        }

        return payloads;
    }

    /**
     * Generate payloads from the second CSV file (2-dimension test case with static 360)
     * Path: src/test/resources/testdata/spa_cover_dimensions2.csv
     * Format: First dimension varies (e.g., E8), Second dimension is static (360)
     */
    public static List<List<String>> generatePayloadsForStaticSecondDimension() {
        String csvPath = "src/main/resources/testdata/spa_cover_dimensions2.csv";

        List<List<String>> payloads = new ArrayList<>();

        // Step 1: Read CSV
        List<SpaCoverDimension> dimensions = CsvDimensionReader.readDimensions(csvPath);

        // Step 2: Generate payload PER entry with static second dimension logic
        for (SpaCoverDimension original : dimensions) {

            List<SpaCoverDimension> fallbacks = 
                    FallbackDimensionGenerator.generateFallbacksForStaticSecondDimension(original);

            List<String> skuPayload = FallbackDimensionGenerator.generateSKUArray(fallbacks);

            System.out.println("Payload (Static 360) for " + original + " with fallback skus = " + skuPayload.size() + ": " + skuPayload);

            payloads.add(skuPayload);
        }

        return payloads;
    }

    /**
     * Generate combined payloads from both test cases
     * This will include both 3-dimension and 2-dimension (static 360) test cases
     */
    public static List<List<String>> generateCombinedPayloads() {
        List<List<String>> combinedPayloads = new ArrayList<>();

        // Add payloads from first test case
        System.out.println("\n=== Generating Payloads from Test Case 1 (3-Dimension) ===");
        List<List<String>> payloads1 = generatePayloads();
        combinedPayloads.addAll(payloads1);

        // Add payloads from second test case
        System.out.println("\n=== Generating Payloads from Test Case 2 (Static 360) ===");
        List<List<String>> payloads2 = generatePayloadsForStaticSecondDimension();
        combinedPayloads.addAll(payloads2);

        System.out.println("\n=== Total Combined Payloads: " + combinedPayloads.size() + " ===");

        return combinedPayloads;
    }

    public static void main(String[] args) {
        // Test all three methods
        System.out.println("Testing generatePayloads():");
        generatePayloads();

        System.out.println("\n\nTesting generatePayloadsForStaticSecondDimension():");
        generatePayloadsForStaticSecondDimension();

        System.out.println("\n\nTesting generateCombinedPayloads():");
        generateCombinedPayloads();
    }

    public static Object loginCredentials() {
        String loginEmail = ConfigReader.getProperty("login.email");
        String loginPassword = ConfigReader.getProperty("login.password");

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("email", loginEmail);
        credentials.put("password", loginPassword);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonOutput = gson.toJson(credentials);
        System.out.println("Log In Creds : " + jsonOutput);
        return jsonOutput;
    }
}
