package zasyaSolutions.mySpaCoverSkuRecommendation.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class InventoryResponseOrganizer {
    
    // Color code to name mapping
    private static final Map<String, String> COLOR_NAMES = new LinkedHashMap<>();
    static {
        COLOR_NAMES.put("1104", "Oxford Grey");
        COLOR_NAMES.put("1244", "Brazilian Mahogany");
        COLOR_NAMES.put("1239", "Coffee Brown");
        COLOR_NAMES.put("3132", "Coastal Grey");
        COLOR_NAMES.put("3221", "Mahogany");
        COLOR_NAMES.put("3218", "Mayan Brown");
    }
    
    // Color code priority - these will be the column headers
    private static final List<String> COLOR_CODES = new ArrayList<>(COLOR_NAMES.keySet());
    
    // Track if this is the first write to the file
    private static boolean isFirstWrite = true;
    
    /**
     * Reset the first write flag (call this at the start of your test)
     */
    public static void resetFileState() {
        isFirstWrite = true;
    }
    
    /**
     * Main method to process and organize inventory response
     * Generates both CSV and Excel outputs
     */
    public static void processAndSaveInventory(String responseJson, List<String> payloadSkus, String outputFilePath) {
        try {
            // Take first SKU from payload as reference
            if (payloadSkus.isEmpty()) {
                System.out.println("No payload SKUs to process");
                return;
            }
            
            String referenceSku = payloadSkus.get(0);
            System.out.println("\n=== Processing Reference SKU: " + referenceSku + " ===");
            
            // Parse dimensions from reference SKU
            DimensionInfo dimInfo = parseDimensions(referenceSku);
            System.out.println("Reference - DimA: " + dimInfo.dimA + ", DimB: " + dimInfo.dimB + ", DimC: " + dimInfo.dimC);
            
            JsonObject response = JsonParser.parseString(responseJson).getAsJsonObject();
            
            // Collect items from inventory AND inbound WITH source tracking
            List<ItemWithSource> allItems = new ArrayList<>();
            
            // Add inventory items with inHandQuantity > 0
            JsonArray inventoryArray = response.getAsJsonArray("inventory");
            for (JsonElement element : inventoryArray) {
                JsonObject item = element.getAsJsonObject();
                String inHandQtyStr = item.get("inHandQuantity").getAsString();
                int inHandQty = Integer.parseInt(inHandQtyStr);
                
                if (inHandQty > 0) {
                    allItems.add(new ItemWithSource(item, "inventory"));
                }
            }
            
            // Add inbound items with inHandQuantity > 0
            JsonArray inboundArray = response.getAsJsonArray("inbound");
            if (inboundArray != null) {
                for (JsonElement element : inboundArray) {
                    JsonObject item = element.getAsJsonObject();
                    String inHandQtyStr = item.get("inHandQuantity").getAsString();
                    int inHandQty = Integer.parseInt(inHandQtyStr);
                    
                    if (inHandQty > 0) {
                        allItems.add(new ItemWithSource(item, "inbound"));
                    }
                }
            }
            
            System.out.println("Total items after filtering (inHandQuantity > 0): " + allItems.size());
            
            // Select the best SKU for each color variant
            // If allItems is empty, this will return an empty map, resulting in all "custom" entries
            Map<String, SkuWithSource> selectedSkus;
            
            if (allItems.isEmpty()) {
                System.out.println("⚠ No items with available quantity found! All colors will be marked as 'custom'");
                selectedSkus = new LinkedHashMap<>(); // Empty map = all custom
            } else {
                selectedSkus = selectBestSkusPerColor(allItems, dimInfo);
            }
            
            // Generate CSV output
            appendToCSV(selectedSkus, dimInfo, outputFilePath);
            System.out.println("✓ CSV file: " + outputFilePath);
            
            // Generate Excel file with color coding
            try {
                String excelFilePath = outputFilePath.replace(".csv", ".xlsx");
                InventoryExcelGenerator.appendToExcel(selectedSkus, dimInfo, excelFilePath);
                System.out.println("✓ Excel file: " + excelFilePath);
            } catch (Exception e) {
                System.err.println("⚠ Could not generate Excel file: " + e.getMessage());
                e.printStackTrace();
                System.err.println("  (CSV file is still available)");
            }
            
        } catch (Exception e) {
            System.err.println("Error processing inventory: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Select the best SKU for each color variant
     * Priority: Exact match with inHandQuantity > 0, then fallbacks
     * Returns SKU with source information
     */
    private static Map<String, SkuWithSource> selectBestSkusPerColor(List<ItemWithSource> items, DimensionInfo refDim) {
        Map<String, SkuWithSource> selectedSkus = new LinkedHashMap<>();
        
        // Build reference dimension key
        String exactDimKey;
        if (refDim.dimB.equals("360")) {
            exactDimKey = refDim.dimA; // Just "E8" for static 360
        } else {
            exactDimKey = refDim.dimA + refDim.dimB; // "E4E4" for standard
        }
        
        System.out.println("\n=== Searching for Exact Match: " + exactDimKey + " ===");
        
        // First pass: Look for EXACT matches for each color
        for (String colorCode : COLOR_CODES) {
            for (ItemWithSource itemWithSource : items) {
                JsonObject item = itemWithSource.item;
                String fullSku = item.get("sku").getAsString();
                String[] skuParts = fullSku.split("-");
                
                if (skuParts.length < 2) continue;
                
                String itemDimKey = skuParts[0];
                String itemColor = extractColorCode(fullSku);
                
                // Check if this is exact match for current color
                if (itemDimKey.equals(exactDimKey) && itemColor.equals(colorCode)) {
                    String inHandQtyStr = item.get("inHandQuantity").getAsString();
                    int inHandQty = Integer.parseInt(inHandQtyStr);
                    
                    if (inHandQty > 0) {
                        selectedSkus.put(colorCode, new SkuWithSource(fullSku, itemWithSource.source));
                        System.out.println("  ✓ EXACT MATCH found for " + colorCode + ": " + fullSku + " (qty: " + inHandQty + ", source: " + itemWithSource.source + ")");
                        break;
                    }
                }
            }
        }
        
        System.out.println("\n=== Searching for Fallback Matches (if needed) ===");
        
        // Second pass: Look for fallbacks ONLY for colors not found in exact matches
        for (String colorCode : COLOR_CODES) {
            if (selectedSkus.containsKey(colorCode)) {
                continue;
            }
            
            System.out.println("  → No exact match for " + colorCode + ", searching fallbacks...");
            
            // Find ALL items with this color code
            List<ItemWithSource> colorMatches = new ArrayList<>();
            for (ItemWithSource itemWithSource : items) {
                JsonObject item = itemWithSource.item;
                String fullSku = item.get("sku").getAsString();
                String itemColor = extractColorCode(fullSku);
                
                if (itemColor.equals(colorCode)) {
                    String inHandQtyStr = item.get("inHandQuantity").getAsString();
                    int inHandQty = Integer.parseInt(inHandQtyStr);
                    
                    if (inHandQty > 0) {
                        colorMatches.add(itemWithSource);
                    }
                }
            }
            
            if (colorMatches.isEmpty()) {
                System.out.println("  ✗ No available SKU found for " + colorCode);
                continue;
            }
            
            // Sort matches by proximity to reference dimensions
            List<ItemWithSource> sortedFallbacks = sortFallbacksByProximity(colorMatches, refDim);
            
            // Pick the first (best) fallback
            ItemWithSource bestFallback = sortedFallbacks.get(0);
            String bestSku = bestFallback.item.get("sku").getAsString();
            String inHandQtyStr = bestFallback.item.get("inHandQuantity").getAsString();
            
            selectedSkus.put(colorCode, new SkuWithSource(bestSku, bestFallback.source));
            System.out.println("  ✓ FALLBACK found for " + colorCode + ": " + bestSku + " (qty: " + inHandQtyStr + ", source: " + bestFallback.source + ")");
        }
        
        return selectedSkus;
    }
    
    /**
     * Extract color code from SKU
     * Handles both formats:
     * - Standard: "E4E4-55-M1-1104" → "1104"
     * - Static 360: "88360-360-M1-1104" → "1104"
     */
    private static String extractColorCode(String sku) {
        String[] parts = sku.split("-");
        if (parts.length >= 4) {
            return parts[3]; // Color code is always the 4th part
        }
        return "";
    }
    
    /**
     * Sort fallback items by proximity to reference dimensions
     */
    private static List<ItemWithSource> sortFallbacksByProximity(List<ItemWithSource> items, DimensionInfo refDim) {
        int refDimA = dimensionToNumeric(refDim.dimA);
        int refDimB = dimensionToNumeric(refDim.dimB);
        int refAvg = (refDimA + refDimB) / 2;
        
        List<ItemWithSource> sorted = new ArrayList<>(items);
        
        sorted.sort((item1, item2) -> {
            String sku1 = item1.item.get("sku").getAsString();
            String sku2 = item2.item.get("sku").getAsString();
            
            String[] parts1 = sku1.split("-");
            String[] parts2 = sku2.split("-");
            
            if (parts1.length < 1 || parts2.length < 1) return 0;
            
            String dims1 = parts1[0];
            String dims2 = parts2[0];
            
            // Parse dimensions based on format
            DimensionPair dim1 = parseDimensionPair(dims1);
            DimensionPair dim2 = parseDimensionPair(dims2);
            
            int avgDim1 = (dim1.dimA + dim1.dimB) / 2;
            int avgDim2 = (dim2.dimA + dim2.dimB) / 2;
            
            // Prefer larger dimensions over smaller ones
            boolean isLarger1 = avgDim1 > refAvg;
            boolean isLarger2 = avgDim2 > refAvg;
            
            if (isLarger1 && !isLarger2) return -1;
            if (!isLarger1 && isLarger2) return 1;
            
            // Within same category, prefer closer to reference
            return Integer.compare(Math.abs(avgDim1 - refAvg), Math.abs(avgDim2 - refAvg));
        });
        
        return sorted;
    }
    
    /**
     * Parse dimension pair from SKU prefix
     * Handles:
     * - Standard: "E4E4" → DimA=84, DimB=84
     * - Static 360 from inventory: "E8" (when second part is 360)
     */
    private static DimensionPair parseDimensionPair(String dims) {
        // For static 360 case, dims will be just "E8" (2 chars)
        // We handle this in the calling code by checking full SKU
        
        // Standard format: "E4E4" → DimA=E4, DimB=E4
        if (dims.length() >= 4) {
            String dimA = dims.substring(0, 2);
            String dimB = dims.substring(2, 4);
            return new DimensionPair(dimensionToNumeric(dimA), dimensionToNumeric(dimB));
        }
        
        // For 2-char dims (static 360 case), return with 360 as dimB
        if (dims.length() == 2) {
            return new DimensionPair(dimensionToNumeric(dims), 360);
        }
        
        return new DimensionPair(0, 0);
    }
    
    /**
     * Parse dimensions from payload SKU
     * Handles two formats:
     * 1. Standard: "E4E4-5" → DimA=E4, DimB=E4, DimC=5
     * 2. Static 360: "E8-360" → DimA=E8, DimB=360, DimC="" (empty for display)
     */
    private static DimensionInfo parseDimensions(String sku) {
        String[] parts = sku.split("-");
        String firstPart = parts[0];
        
        // Check if this is static 360 format
        // First part will be 2 chars (E8) for static 360
        // First part will be 4 chars (E4E4) for standard
        if (firstPart.length() == 2) {
            // Format: "E8-360" → DimA=E8, DimB=360, DimC="" (empty)
            String dimA = firstPart; // E8
            String dimB = "360";
            String dimC = ""; // Empty for static 360 case
            return new DimensionInfo(dimA, dimB, dimC);
        }
        
        // Standard format: "E4E4-5"
        if (firstPart.length() >= 4) {
            String dimA = firstPart.substring(0, 2); // E4
            String dimB = firstPart.substring(2, 4); // E4
            String dimC = parts.length > 1 ? parts[1] : "0";
            return new DimensionInfo(dimA, dimB, dimC);
        }
        
        // Fallback
        return new DimensionInfo("", "", "0");
    }
    
    /**
     * Convert dimension code to numeric value for comparison
     * X=6, S=7, E=8, N=9
     */
    private static int dimensionToNumeric(String dim) {
        // If already numeric, return as-is
        if (dim.matches("\\d+")) {
            return Integer.parseInt(dim);
        }
        
        // Replace letters and parse (for internal comparison only)
        String replaced = dim.replace("X", "6")
                             .replace("S", "7")
                             .replace("E", "8")
                             .replace("N", "9");
        try {
            return Integer.parseInt(replaced);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Convert dimension code to numeric string for display (DimA and DimB only)
     * E4 → 84, X6 → 66, etc.
     */
    private static String dimensionToNumericString(String dim) {
        // If already numeric, return as-is
        if (dim.matches("\\d+")) {
            return dim;
        }
        
        // Replace letters with numbers
        String replaced = dim.replace("X", "6")
                             .replace("S", "7")
                             .replace("E", "8")
                             .replace("N", "9");
        return replaced;
    }
    
    /**
     * Append row to CSV (or create with headers if first time)
     */
    private static void appendToCSV(
        Map<String, SkuWithSource> selectedSkus,
        DimensionInfo dimInfo,
        String outputFilePath
    ) {
        try {
            File file = new File(outputFilePath);
            boolean fileExists = file.exists() && !isFirstWrite;
            
            FileWriter writer = new FileWriter(outputFilePath, fileExists);
            
            // Write informational header and column headers only if this is the first write
            if (!fileExists || isFirstWrite) {
                // Write informational header
                writer.write("=== INVENTORY REPORT - SKU BREAKDOWN & COLOR CODING ===\n");
                writer.write("\n");
                writer.write("SKU FORMAT EXPLANATION:\n");
                writer.write("Example SKU: E4E4-55-M1-1104\n");
                writer.write("  - First Part (E4E4): Dimensions A & B\n");
                writer.write("    * E4 = 84 inches (First Dimension)\n");
                writer.write("    * E4 = 84 inches (Second Dimension)\n");
                writer.write("  - Second Part (55): Third Dimension (5 inches)\n");
                writer.write("  - Third Part (M1): Taper specification\n");
                writer.write("  - Fourth Part (1104): Material/Color code\n");
                writer.write("\n");
                writer.write("DIMENSION CODE CONVERSION:\n");
                writer.write("  X = 6 (e.g., X6 = 66 inches)\n");
                writer.write("  S = 7 (e.g., S7 = 77 inches)\n");
                writer.write("  E = 8 (e.g., E8 = 88 inches)\n");
                writer.write("  N = 9 (e.g., N9 = 99 inches)\n");
                writer.write("\n");
                writer.write("STATUS INDICATORS (shown in Excel with colors):\n");
                writer.write("  - 'instock' = Item currently available in warehouse (GREEN in Excel)\n");
                writer.write("  - 'inbound' = Item on the way, arriving soon (YELLOW in Excel)\n");
                writer.write("  - 'custom' = Not available, requires custom order (RED in Excel)\n");
                writer.write("\n");
                writer.write("COLOR CODES:\n");
                writer.write("  1104 = Oxford Grey\n");
                writer.write("  1244 = Brazilian Mahogany\n");
                writer.write("  1239 = Coffee Brown\n");
                writer.write("  3132 = Coastal Grey\n");
                writer.write("  3221 = Mahogany\n");
                writer.write("  3218 = Mayan Brown\n");
                writer.write("\n");
                writer.write("=".repeat(80) + "\n");
                writer.write("\n");
                
                // Write column headers
                writer.write("DimA,DimB,DimC");
                for (String colorCode : COLOR_CODES) {
                    String colorName = COLOR_NAMES.get(colorCode);
                    writer.write("," + colorCode + " - " + colorName);
                }
                writer.write("\n");
                isFirstWrite = false;
            }
            
            // Write data row with numeric DimA and DimB, original DimC
            String dimANumeric = dimensionToNumericString(dimInfo.dimA);
            String dimBNumeric = dimensionToNumericString(dimInfo.dimB);
            writer.write(dimANumeric + "," + dimBNumeric + "," + dimInfo.dimC);
            
            // Write SKU for each color code with source prefix (keep original SKU format)
            for (String colorCode : COLOR_CODES) {
                SkuWithSource skuWithSource = selectedSkus.get(colorCode);
                
                if (skuWithSource == null) {
                    writer.write(",custom");
                } else {
                    // Keep original SKU format (no letter-to-number conversion)
                    String sku = skuWithSource.sku;
                    
                    // Add source prefix: "instock" or "inbound"
                    String sourcePrefix = skuWithSource.source.equals("inventory") ? "instock" : "inbound";
                    writer.write("," + sourcePrefix + " " + sku);
                }
            }
            
            writer.write("\n");
            writer.close();
            
            System.out.println("\n=== CSV Row Added ===");
            System.out.println("Reference Dimensions: " + dimANumeric + " x " + dimBNumeric + " x " + dimInfo.dimC);
            System.out.println("Color variants found: " + selectedSkus.size() + "/" + COLOR_CODES.size());
            
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Helper classes
    static class DimensionInfo {
        String dimA;
        String dimB;
        String dimC;
        
        DimensionInfo(String dimA, String dimB, String dimC) {
            this.dimA = dimA;
            this.dimB = dimB;
            this.dimC = dimC;
        }
    }
    
    static class DimensionPair {
        int dimA;
        int dimB;
        
        DimensionPair(int dimA, int dimB) {
            this.dimA = dimA;
            this.dimB = dimB;
        }
    }
    
    /**
     * Helper class to track item with its source (inventory or inbound)
     */
    static class ItemWithSource {
        JsonObject item;
        String source; // "inventory" or "inbound"
        
        ItemWithSource(JsonObject item, String source) {
            this.item = item;
            this.source = source;
            this.source = source;
        }
    }
    
    /**
     * Helper class to track SKU with its source (inventory or inbound)
     */
    static class SkuWithSource {
        String sku;
        String source; // "inventory" or "inbound"
        
        SkuWithSource(String sku, String source) {
            this.sku = sku;
            this.source = source;
        }
    }
}