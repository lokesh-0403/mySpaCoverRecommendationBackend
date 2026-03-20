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
        COLOR_NAMES.put("1239/1229", "Coffee Brown");
        COLOR_NAMES.put("3132", "Coastal Grey");
        COLOR_NAMES.put("3221", "Mahogany");
        COLOR_NAMES.put("3218", "Mayan Brown");
    }
    
    // Color code priority - these will be the column headers
    private static final List<String> COLOR_CODES = new ArrayList<>(COLOR_NAMES.keySet());
    
    /**
     * Main method to process and organize inventory response
     * Generates both CSV and Excel outputs
     */
    public static void processAndSaveInventory(String responseJson, List<String> payloadSkus, String outputFilePath) {
        if (payloadSkus.isEmpty()) {
            throw new IllegalArgumentException("No payload SKUs provided");
        }

        String referenceSku = payloadSkus.get(0);
        DimensionInfo dimInfo = parseDimensions(referenceSku);
        JsonObject response = JsonParser.parseString(responseJson).getAsJsonObject();

        List<ItemWithSource> allItems = new ArrayList<>();

        JsonArray inventoryArray = response.has("inventory") && response.get("inventory").isJsonArray()
            ? response.getAsJsonArray("inventory")
            : new JsonArray();
        for (JsonElement element : inventoryArray) {
            JsonObject item = element.getAsJsonObject();
            String inHandQtyStr = item.get("inHandQuantity").getAsString();
            int inHandQty = Integer.parseInt(inHandQtyStr);

            if (inHandQty > 0) {
                allItems.add(new ItemWithSource(item, "inventory"));
            }
        }

        JsonArray inboundArray = response.has("inbound") && response.get("inbound").isJsonArray()
            ? response.getAsJsonArray("inbound")
            : new JsonArray();
        for (JsonElement element : inboundArray) {
            JsonObject item = element.getAsJsonObject();
            String inHandQtyStr = item.get("inHandQuantity").getAsString();
            int inHandQty = Integer.parseInt(inHandQtyStr);

            if (inHandQty > 0) {
                allItems.add(new ItemWithSource(item, "inbound"));
            }
        }

        Map<String, SkuWithSource> selectedSkus = allItems.isEmpty()
            ? new LinkedHashMap<>()
            : selectBestSkusPerColor(allItems, dimInfo);

        appendToCSV(selectedSkus, dimInfo, outputFilePath);
        String excelFilePath = outputFilePath.replace(".csv", ".xlsx");
        InventoryExcelGenerator.appendToExcel(selectedSkus, dimInfo, excelFilePath);
    }
    
    /**
     * Select the best SKU for each color variant
     * Priority: Exact match with inHandQuantity > 0, then fallbacks
     * Returns SKU with source information
     */
    private static Map<String, SkuWithSource> selectBestSkusPerColor(List<ItemWithSource> items, DimensionInfo refDim) {
        Map<String, SkuWithSource> selectedSkus = new LinkedHashMap<>();
        
        // Build reference dimension key
        String exactDimKey = refDim.circle
            ? refDim.dimA
            : refDim.dimA + refDim.dimB;

        for (String colorCode : COLOR_CODES) {
            List<ItemWithSource> colorMatches = collectColorMatches(items, colorCode);
            if (colorMatches.isEmpty()) {
                continue;
            }

            List<ItemWithSource> rankedMatches = rankCandidates(colorMatches, refDim, exactDimKey);
            ItemWithSource bestFallback = rankedMatches.get(0);
            String bestSku = bestFallback.item.get("sku").getAsString();
            selectedSkus.put(colorCode, new SkuWithSource(bestSku, bestFallback.source));
        }
        
        return selectedSkus;
    }

    private static List<ItemWithSource> collectColorMatches(List<ItemWithSource> items, String colorCode) {
        List<ItemWithSource> colorMatches = new ArrayList<>();
        for (ItemWithSource itemWithSource : items) {
            JsonObject item = itemWithSource.item;
            String fullSku = item.get("sku").getAsString();
            String itemColor = extractColorCode(fullSku);

            if (matchesColorCode(colorCode, itemColor)) {
                String inHandQtyStr = item.get("inHandQuantity").getAsString();
                int inHandQty = Integer.parseInt(inHandQtyStr);

                if (inHandQty > 0) {
                    colorMatches.add(itemWithSource);
                }
            }
        }
        return colorMatches;
    }

    private static List<ItemWithSource> rankCandidates(List<ItemWithSource> candidates, DimensionInfo refDim, String exactDimKey) {
        List<ItemWithSource> ranked = new ArrayList<>(candidates);
        ranked.sort((item1, item2) -> compareCandidates(item1, item2, refDim, exactDimKey));
        return ranked;
    }

    private static int compareCandidates(ItemWithSource item1, ItemWithSource item2, DimensionInfo refDim, String exactDimKey) {
        int sourceCompare = Integer.compare(sourcePriority(item1), sourcePriority(item2));
        if (sourceCompare != 0) {
            return sourceCompare;
        }

        int exactCompare = Boolean.compare(!isExactDimensionMatch(item1, exactDimKey), !isExactDimensionMatch(item2, exactDimKey));
        if (exactCompare != 0) {
            return exactCompare;
        }

        int largerCompare = Boolean.compare(!isPreferredLarger(item1, refDim), !isPreferredLarger(item2, refDim));
        if (largerCompare != 0) {
            return largerCompare;
        }

        int distanceCompare = Integer.compare(dimensionDistance(item1, refDim), dimensionDistance(item2, refDim));
        if (distanceCompare != 0) {
            return distanceCompare;
        }

        return item1.item.get("sku").getAsString().compareTo(item2.item.get("sku").getAsString());
    }

    private static int sourcePriority(ItemWithSource itemWithSource) {
        return "inventory".equals(itemWithSource.source) ? 0 : 1;
    }

    private static boolean isExactDimensionMatch(ItemWithSource itemWithSource, String exactDimKey) {
        String fullSku = itemWithSource.item.get("sku").getAsString();
        String[] skuParts = fullSku.split("-");
        if (skuParts.length < 2) {
            return false;
        }
        return skuParts[0].equals(exactDimKey);
    }

    private static boolean isPreferredLarger(ItemWithSource itemWithSource, DimensionInfo refDim) {
        DimensionPair referencePair = toReferencePair(refDim);
        DimensionPair candidatePair = parseDimensionPair(itemWithSource.item.get("sku").getAsString().split("-")[0]);

        if (refDim.circle) {
            return candidatePair.dimA >= referencePair.dimA;
        }

        int referenceAverage = (referencePair.dimA + referencePair.dimB) / 2;
        int candidateAverage = (candidatePair.dimA + candidatePair.dimB) / 2;
        return candidateAverage >= referenceAverage;
    }

    private static int dimensionDistance(ItemWithSource itemWithSource, DimensionInfo refDim) {
        DimensionPair referencePair = toReferencePair(refDim);
        DimensionPair candidatePair = parseDimensionPair(itemWithSource.item.get("sku").getAsString().split("-")[0]);

        if (refDim.circle) {
            return Math.abs(candidatePair.dimA - referencePair.dimA);
        }

        int referenceAverage = (referencePair.dimA + referencePair.dimB) / 2;
        int candidateAverage = (candidatePair.dimA + candidatePair.dimB) / 2;
        return Math.abs(candidateAverage - referenceAverage);
    }

    private static DimensionPair toReferencePair(DimensionInfo refDim) {
        if (refDim.circle) {
            return new DimensionPair(dimensionToNumeric(refDim.dimA), 360);
        }
        return new DimensionPair(dimensionToNumeric(refDim.dimA), dimensionToNumeric(refDim.dimB));
    }

    private static boolean matchesColorCode(String configuredColorCode, String actualColorCode) {
        for (String supportedCode : configuredColorCode.split("/")) {
            if (supportedCode.equals(actualColorCode)) {
                return true;
            }
        }
        return false;
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
        DimensionPair referencePair = toReferencePair(refDim);
        int refDimA = referencePair.dimA;
        int refDimB = referencePair.dimB;
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
     * 2. Static 360: "E8-360" → DimA=E8, DimB="" and DimC=360 for display
     */
    private static DimensionInfo parseDimensions(String sku) {
        String[] parts = sku.split("-");
        String firstPart = parts[0];
        
        // Check if this is static 360 format
        // First part will be 2 chars (E8) for static 360
        // First part will be 4 chars (E4E4) for standard
        if (firstPart.length() == 2) {
            // Format: "E8-360" → display DimA=88, DimB="", DimC=360
            String dimA = firstPart;
            String dimB = "";
            String dimC = "360";
            return new DimensionInfo(dimA, dimB, dimC, true);
        }
        
        // Standard format: "E4E4-5"
        if (firstPart.length() >= 4) {
            String dimA = firstPart.substring(0, 2); // E4
            String dimB = firstPart.substring(2, 4); // E4
            String dimC = parts.length > 1 ? parts[1] : "0";
            return new DimensionInfo(dimA, dimB, dimC, false);
        }
        
        // Fallback
        return new DimensionInfo("", "", "0", false);
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
            File parentDirectory = file.getParentFile();
            if (parentDirectory != null && !parentDirectory.exists()) {
                parentDirectory.mkdirs();
            }

            boolean append = file.exists() && file.length() > 0;
            try (FileWriter writer = new FileWriter(file, append)) {
                if (!append) {
                    writer.write("=== INVENTORY REPORT - SKU BREAKDOWN & COLOR CODING ===\n");
                    writer.write("\n");
                    writer.write("SKU FORMAT EXPLANATION:\n");
                    writer.write("Example SKU: E4E4-55-M1-1104, E4-3605-M1-1104\n");
                    writer.write("  - First Part (E4E4): Dimensions A & B\n");
                    writer.write("    * E4 = 84 inches (First Dimension)/ Diameter for the Circular Cover\n");
                    writer.write("    * E4 = 84 inches (Second Dimension)/ Second Dimension is not Available for Circular Cover Sku's\n");
                    writer.write("  - Second Part (55/3605): Third Dimension (5 inches) for Round Rectangle Cover/ Third Dimension (360 inches) for Circular Cover\n");
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
                    writer.write("  1239/1229 = Coffee Brown\n");
                    writer.write("  3132 = Coastal Grey\n");
                    writer.write("  3221 = Mahogany\n");
                    writer.write("  3218 = Mayan Brown\n");
                    writer.write("\n");
                    writer.write("=".repeat(80) + "\n");
                    writer.write("\n");

                    writer.write("DimA,DimB,DimC");
                    for (String colorCode : COLOR_CODES) {
                        String colorName = COLOR_NAMES.get(colorCode);
                        writer.write("," + colorCode + " - " + colorName);
                    }
                    writer.write("\n");
                }

                String dimANumeric = dimensionToNumericString(dimInfo.dimA);
                String dimBNumeric = dimensionToNumericString(dimInfo.dimB);
                writer.write(dimANumeric + "," + dimBNumeric + "," + dimInfo.dimC);

                for (String colorCode : COLOR_CODES) {
                    SkuWithSource skuWithSource = selectedSkus.get(colorCode);

                    if (skuWithSource == null) {
                        writer.write(",custom");
                    } else {
                        String sourcePrefix = skuWithSource.source.equals("inventory") ? "instock" : "inbound";
                        writer.write("," + sourcePrefix + " " + skuWithSource.sku);
                    }
                }

                writer.write("\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error writing CSV file: " + outputFilePath, e);
        }
    }
    
    // Helper classes
    static class DimensionInfo {
        String dimA;
        String dimB;
        String dimC;
        boolean circle;
        
        DimensionInfo(String dimA, String dimB, String dimC, boolean circle) {
            this.dimA = dimA;
            this.dimB = dimB;
            this.dimC = dimC;
            this.circle = circle;
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
