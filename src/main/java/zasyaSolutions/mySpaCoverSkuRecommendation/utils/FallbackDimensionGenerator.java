package zasyaSolutions.mySpaCoverSkuRecommendation.utils;

import java.util.ArrayList;
import java.util.List;

import zasyaSolutions.mySpaCoverSkuRecommendation.model.SpaCoverDimension;



/**
 * Generates fallback dimensions for spa covers
 */
public class FallbackDimensionGenerator {

    /**
     * Generate fallbacks for standard 3-dimension case (DimA, DimB, DimC all vary)
     * Original logic from commented code - for the first test case
     */
    public static List<SpaCoverDimension> generateFallbacks(SpaCoverDimension original) {
        List<SpaCoverDimension> fallbackAttempts = new ArrayList<>();

        int altDimA = original.getDimensionA();
        int altDimB = original.getDimensionB();
        int altDimC = original.getDimensionC();

        fallbackAttempts.addAll(
            generateStandardFallbacksNonProduct(altDimA, altDimB, altDimC)
        );

        // Put original dimension at 0th index
        fallbackAttempts.add(0, original);

        return fallbackAttempts;
    }

    /**
     * Generate fallbacks for 2-dimension case with STATIC second dimension (360)
     * Only first dimension varies with +1/-1 logic
     * Second dimension remains constant at 360
     * 
     * Example: If input is DimA=88, DimB=360, DimC=0
     * - Original: 88-360
     * - +1: 89-360
     * - -1: 87-360
     */
    public static List<SpaCoverDimension> generateFallbacksForStaticSecondDimension(SpaCoverDimension original) {
        List<SpaCoverDimension> fallbacks = new ArrayList<>();

        int dimA = original.getDimensionA();
        int dimB = original.getDimensionB(); // This should be 360 and remains static
        int dimC = original.getDimensionC(); // Should be 0 for static 360 case

        // Add original first
        fallbacks.add(original);

        // Only vary the first dimension (DimA) with +1 and -1
        // DimB (360) remains constant
        // DimC remains at 0
        fallbacks.add(new SpaCoverDimension(dimA + 1, dimB, dimC)); // +1
        fallbacks.add(new SpaCoverDimension(dimA - 1, dimB, dimC)); // -1

        System.out.println("  Generated fallbacks for static 360 case:");
        for (SpaCoverDimension fb : fallbacks) {
            System.out.println("    - " + fb.getDimensionA() + "-" + fb.getDimensionB());
        }

        return fallbacks;
    }

    /**
     * Standard fallback generation from original commented code
     */
    private static List<SpaCoverDimension> generateStandardFallbacksNonProduct(
            int altDimA, int altDimB, int altDimC) {

        List<SpaCoverDimension> fallbacks = new ArrayList<>();

        // Define algebraic offsets
        int[] aOffsets = {-1, 0, 1, 2};
        int[] bOffsets = {-1, 0, 1, 2};
        
        // Conditional C offsets based on altDimC value
        int[] cOffsets;
        if (altDimC == 0) {
            cOffsets = new int[]{0, 1};  // When C=0, only allow 0 and +1
        } else {
            cOffsets = new int[]{-1, 0, 1};  // When C>0, allow -1, 0, +1
        }

        // Forbidden offset combinations (exact matches to exclude)
        int[][] forbidden = {
            {-1,  2, -1},
            {-1,  2,  0},
            { 2, -1, -1},
            {-1,  0,  1},
            { 2, -1,  0},
            { 2, -1,  1}
        };

        for (int aOff : aOffsets) {
            for (int bOff : bOffsets) {
                for (int cOff : cOffsets) {

                    // Skip if this exact combination is forbidden
                    boolean isForbidden = false;
                    for (int[] f : forbidden) {
                        if (aOff == f[0] && bOff == f[1] && cOff == f[2]) {
                            isForbidden = true;
                            break;
                        }
                    }
                    if (isForbidden) continue;

                    // Calculate new dimensions
                    int newA = altDimA + aOff;
                    int newB = altDimB + bOff;
                    int newC = altDimC + cOff;

                    // Skip invalid dimensions (A and B must be positive, C can be 0 or positive)
                    if (newA <= 0 || newB <= 0 || newC < 0) continue;

                    fallbacks.add(new SpaCoverDimension(newA, newB, newC));
                }
            }
        }

        return fallbacks;
    }

    /**
     * Generate SKU array from dimension list
     * This method uses DimensionConverter to convert SpaCoverDimension to SKU string
     */
    public static List<String> generateSKUArray(List<SpaCoverDimension> dimensions) {
        List<String> skuArray = new ArrayList<>();
        for (SpaCoverDimension dim : dimensions) {
            skuArray.add(DimensionConverter.convertToSKU(dim));
        }
        return skuArray;
    }
}
