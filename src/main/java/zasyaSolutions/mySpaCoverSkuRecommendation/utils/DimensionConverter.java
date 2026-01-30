package zasyaSolutions.mySpaCoverSkuRecommendation.utils;

import zasyaSolutions.mySpaCoverSkuRecommendation.model.SpaCoverDimension;

/**
 * Utility class to convert SpaCoverDimension to SKU format
 */
public class DimensionConverter {

    /**
     * Convert SpaCoverDimension to SKU format
     * 
     * Handles two cases:
     * 1. Standard case: "E4E4-5" (84x84x5) - converts to letter codes
     * 2. Static 360 case: "E8-360" (88x360) - converts DimA to letter code
     * 
     * @param dimension The dimension to convert
     * @return SKU string
     */
    public static String convertToSKU(SpaCoverDimension dimension) {
        int dimA = dimension.getDimensionA();
        int dimB = dimension.getDimensionB();
        int dimC = dimension.getDimensionC();

        // Check if this is the static 360 case
        if (dimB == 360) {
            // Static 360 format: "E8-360"
            // Convert DimA to letter code, keep DimB as 360
            String codeA = convertToCode(dimA);
            return codeA + "-" + dimB;
        }

        // Standard format: Convert numbers to letter codes and build SKU
        String codeA = convertToCode(dimA);
        String codeB = convertToCode(dimB);
        
        // Standard SKU format: "E4E4-5"
        return codeA + codeB + "-" + dimC;
    }

    /**
     * Convert numeric dimension to code (e.g., 84 -> E4, 88 -> E8)
     * 
     * Conversion rules:
     * - First digit: 6->X, 7->S, 8->E, 9->N
     * - Keep second digit as-is
     * 
     * Examples:
     * 84 -> E4
     * 88 -> E8
     * 90 -> N0
     * 72 -> S2
     */
    private static String convertToCode(int dimension) {
        String dimStr = String.valueOf(dimension);
        
        // Handle single digit (e.g., 5 -> "5")
        if (dimStr.length() == 1) {
            return dimStr;
        }
        
        // Get first and second digit
        char firstDigit = dimStr.charAt(0);
        char secondDigit = dimStr.charAt(1);
        
        // Convert first digit to letter
        char letter;
        switch (firstDigit) {
            case '6': letter = 'X'; break;
            case '7': letter = 'S'; break;
            case '8': letter = 'E'; break;
            case '9': letter = 'N'; break;
            default: letter = firstDigit; // Keep as-is if not in range
        }
        
        return letter + String.valueOf(secondDigit);
    }

    /**
     * Convert code back to numeric dimension (e.g., E4 -> 84, E8 -> 88)
     * 
     * @param code The code to convert
     * @return Numeric dimension
     */
    public static int convertToNumeric(String code) {
        if (code == null || code.isEmpty()) {
            return 0;
        }
        
        // If already numeric, return as-is
        if (code.matches("\\d+")) {
            return Integer.parseInt(code);
        }
        
        // Handle letter codes (e.g., "E4" -> "84")
        if (code.length() == 2) {
            char letter = code.charAt(0);
            char digit = code.charAt(1);
            
            // Convert letter to first digit
            char firstDigit;
            switch (letter) {
                case 'X': firstDigit = '6'; break;
                case 'S': firstDigit = '7'; break;
                case 'E': firstDigit = '8'; break;
                case 'N': firstDigit = '9'; break;
                default: firstDigit = letter; // Keep as-is
            }
            
            String numStr = firstDigit + String.valueOf(digit);
            return Integer.parseInt(numStr);
        }
        
        return 0;
    }
}
