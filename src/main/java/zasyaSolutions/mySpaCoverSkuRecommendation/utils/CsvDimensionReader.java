package zasyaSolutions.mySpaCoverSkuRecommendation.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import zasyaSolutions.mySpaCoverSkuRecommendation.exception.BadRequestException;
import zasyaSolutions.mySpaCoverSkuRecommendation.model.SpaCoverDimension;

/**
 * Utility class to read spa cover dimensions from CSV files
 * Supports both 2-column (DimA, DimB) and 3-column (DimA, DimB, DimC) formats
 */
public class CsvDimensionReader {

    /**
     * Read dimensions from CSV file.
     * Supported row styles:
     * - 1 column: circle input, maps to DimB=360 and DimC=0
     * - 2 columns: rectangle input, or circle input when DimB is blank/360/same as DimA
     * - 3 columns: rectangle input, or circle input when DimC=360
     */
    public static List<SpaCoverDimension> readDimensions(String csvFilePath) {
        return readDimensions(Path.of(csvFilePath));
    }

    public static List<SpaCoverDimension> readDimensions(Path csvFilePath) {
        List<SpaCoverDimension> dimensions = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(csvFilePath, StandardCharsets.UTF_8)) {
            String line;
            boolean isHeader = true;
            int lineNumber = 0;

            while ((line = br.readLine()) != null) {
                lineNumber++;
                // Skip header row
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] values = line.split(",", -1);
                SpaCoverDimension dimension = createDimension(values, lineNumber);
                if (dimension != null) {
                    dimensions.add(dimension);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Error reading CSV file: " + csvFilePath, e);
        }

        return dimensions;
    }

    private static SpaCoverDimension createDimension(String[] values, int lineNumber) {
        if (countPopulatedValues(values) == 0) {
            return null;
        }

        String dimAValue = getValue(values, 0);
        if (dimAValue.isEmpty()) {
            throw new BadRequestException("CSV row " + lineNumber + " has an empty dimA value");
        }

        int dimA = parseDimensionValue(dimAValue, "dimA", lineNumber);
        String dimBValue = getValue(values, 1);
        String dimCValue = getValue(values, 2);

        boolean hasDimB = !dimBValue.isEmpty();
        boolean hasDimC = !dimCValue.isEmpty();

        if (!hasDimB && !hasDimC) {
            return createCircleDimension(dimA);
        }

        if (!hasDimB && hasDimC) {
            int dimC = parseDimensionValue(dimCValue, "dimC", lineNumber);
            if (dimC == 360) {
                return createCircleDimension(dimA);
            }
            throw new BadRequestException("CSV row " + lineNumber + " requires dimB when dimC is provided for a rectangle row");
        }

        int dimB = parseDimensionValue(dimBValue, "dimB", lineNumber);

        if (!hasDimC) {
            if (dimB == 360 || dimA == dimB) {
                return createCircleDimension(dimA);
            }
            return new SpaCoverDimension(dimA, dimB, 0);
        }

        int dimC = parseDimensionValue(dimCValue, "dimC", lineNumber);
        if (dimC == 360) {
            return createCircleDimension(resolveCircleDimension(dimA, dimB));
        }

        return new SpaCoverDimension(dimA, dimB, dimC);
    }

    private static SpaCoverDimension createCircleDimension(int dimension) {
        return new SpaCoverDimension(dimension, 360, 0);
    }

    private static int resolveCircleDimension(int dimA, int dimB) {
        if (dimB == 360 || dimA == dimB) {
            return dimA;
        }
        return dimA;
    }

    private static int countPopulatedValues(String[] values) {
        int count = 0;
        for (String value : values) {
            if (value != null && !normalizeCell(value).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static String getValue(String[] values, int index) {
        if (index >= values.length || values[index] == null) {
            return "";
        }
        return normalizeCell(values[index]);
    }

    private static int parseDimensionValue(String rawValue, String columnName, int lineNumber) {
        String value = normalizeCell(rawValue);
        if (value.isEmpty()) {
            throw new BadRequestException("CSV row " + lineNumber + " has an empty " + columnName + " value");
        }

        try {
            BigDecimal decimal = new BigDecimal(value);
            return decimal.setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (Exception exception) {
            throw new BadRequestException(
                "CSV row " + lineNumber + " has an invalid " + columnName + " value: '" + value + "'. Expected a number."
            );
        }
    }

    private static String normalizeCell(String rawValue) {
        if (rawValue == null) {
            return "";
        }

        String value = rawValue.replace("\uFEFF", "").trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1).trim();
        }

        return value;
    }
}
