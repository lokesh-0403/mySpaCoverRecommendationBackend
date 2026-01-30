package zasyaSolutions.mySpaCoverSkuRecommendation.utils;



import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import zasyaSolutions.mySpaCoverSkuRecommendation.model.SpaCoverDimension;

/**
 * Utility class to read spa cover dimensions from CSV files
 * Supports both 2-column (DimA, DimB) and 3-column (DimA, DimB, DimC) formats
 */
public class CsvDimensionReader {

    /**
     * Read dimensions from CSV file
     * Handles both formats:
     * - 2 columns: DimA, DimB (for static 360 case, DimC defaults to 0)
     * - 3 columns: DimA, DimB, DimC (for standard case)
     */
    public static List<SpaCoverDimension> readDimensions(String csvFilePath) {
        List<SpaCoverDimension> dimensions = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                // Skip header row
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] values = line.split(",");

                // Parse based on number of columns
                if (values.length >= 2) {
                    int dimA = Integer.parseInt(values[0].trim());
                    int dimB = Integer.parseInt(values[1].trim());
                    
                    // Check if DimC exists and is not empty
                    int dimC = 0; // Default to 0 for 2-column format
                    if (values.length >= 3 && !values[2].trim().isEmpty()) {
                        dimC = Integer.parseInt(values[2].trim());
                    }

                    dimensions.add(new SpaCoverDimension(dimA, dimB, dimC));
                    
                    System.out.println("Read dimension: DimA=" + dimA + ", DimB=" + dimB + ", DimC=" + dimC);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Error reading CSV file: " + csvFilePath, e);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Error parsing dimension values from CSV: " + e.getMessage(), e);
        }

        System.out.println("Total dimensions read: " + dimensions.size());
        return dimensions;
    }
}