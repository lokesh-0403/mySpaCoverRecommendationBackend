package zasyaSolutions.mySpaCoverSkuRecommendation.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Excel generator for inventory with color-coded status
 */
public class InventoryExcelGenerator {
    
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
    
    private static final List<String> COLOR_CODES = new ArrayList<>(COLOR_NAMES.keySet());
    
    /**
     * Append row to Excel file with color formatting
     */
    public static void appendToExcel(
        Map<String, InventoryResponseOrganizer.SkuWithSource> selectedSkus,
        InventoryResponseOrganizer.DimensionInfo dimInfo,
        String outputFilePath
    ) {
        Workbook workbook = null;
        try {
            Sheet sheet;
            int lastRowNum = 0;
            int dataStartRow = 31;
            File file = new File(outputFilePath);

            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    workbook = new XSSFWorkbook(fis);
                }
                sheet = workbook.getSheetAt(0);
                lastRowNum = sheet.getLastRowNum();
            } else {
                workbook = new XSSFWorkbook();
                sheet = workbook.createSheet("Inventory");
                createHeaderRow(workbook, sheet);
                lastRowNum = dataStartRow;
            }

            Row dataRow = sheet.createRow(lastRowNum + 1);

            CellStyle instockStyle = createInstockStyle(workbook);
            CellStyle inboundStyle = createInboundStyle(workbook);
            CellStyle customStyle = createCustomStyle(workbook);
            CellStyle dimensionStyle = createDimensionStyle(workbook);

            Cell dimACell = dataRow.createCell(0);
            dimACell.setCellValue(dimensionToNumericString(dimInfo.dimA));
            dimACell.setCellStyle(dimensionStyle);

            Cell dimBCell = dataRow.createCell(1);
            dimBCell.setCellValue(dimensionToNumericString(dimInfo.dimB));
            dimBCell.setCellStyle(dimensionStyle);

            Cell dimCCell = dataRow.createCell(2);
            dimCCell.setCellValue(dimInfo.dimC);
            dimCCell.setCellStyle(dimensionStyle);

            int colIndex = 3;
            for (String colorCode : COLOR_CODES) {
                Cell cell = dataRow.createCell(colIndex++);
                InventoryResponseOrganizer.SkuWithSource skuWithSource = selectedSkus.get(colorCode);

                if (skuWithSource == null) {
                    cell.setCellValue("custom");
                    cell.setCellStyle(customStyle);
                    continue;
                }

                cell.setCellValue(skuWithSource.sku);
                if (skuWithSource.source.equals("inventory")) {
                    cell.setCellStyle(instockStyle);
                } else {
                    cell.setCellStyle(inboundStyle);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
                workbook.write(fos);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error writing Excel file: " + outputFilePath, e);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    throw new IllegalStateException("Error closing Excel workbook: " + outputFilePath, e);
                }
            }
        }
    }
    
    /**
     * Create header row with formatting and informational section
     */
    private static void createHeaderRow(Workbook workbook, Sheet sheet) {
        CellStyle infoStyle = createInfoStyle(workbook);
        CellStyle infoTitleStyle = createInfoTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        
        int currentRow = 0;
        
        // Title row - span across info columns (A-F)
        Row titleRow = sheet.createRow(currentRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("INVENTORY REPORT - SKU BREAKDOWN & COLOR CODING");
        titleCell.setCellStyle(infoTitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));
        
        // Empty row
        currentRow++;
        
        // SKU Format Explanation
        Row formatTitleRow = sheet.createRow(currentRow++);
        Cell formatTitleCell = formatTitleRow.createCell(0);
        formatTitleCell.setCellValue("SKU FORMAT EXPLANATION:");
        formatTitleCell.setCellStyle(infoTitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        
        Row exampleRow = sheet.createRow(currentRow++);
        Cell exampleCell = exampleRow.createCell(0);
        exampleCell.setCellValue("Example SKU: E4E4-55-M1-1104");
        exampleCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        
        Row dim1Row = sheet.createRow(currentRow++);
        Cell dim1Cell = dim1Row.createCell(0);
        dim1Cell.setCellValue("  • First Part (E4E4): Dimensions A & B → E4 = 84 inches (1st Dim), E4 = 84 inches (2nd Dim)");
        dim1Cell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        
        Row dim2Row = sheet.createRow(currentRow++);
        Cell dim2Cell = dim2Row.createCell(0);
        dim2Cell.setCellValue("  • Second Part (55): Third Dimension → 5 inches");
        dim2Cell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        
        Row taperRow = sheet.createRow(currentRow++);
        Cell taperCell = taperRow.createCell(0);
        taperCell.setCellValue("  • Third Part (M1): Taper specification");
        taperCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        
        Row colorRow = sheet.createRow(currentRow++);
        Cell colorCell = colorRow.createCell(0);
        colorCell.setCellValue("  • Fourth Part (1104): Material/Color code");
        colorCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        
        // Empty row
        currentRow++;
        
        // Dimension Code Conversion
        Row conversionTitleRow = sheet.createRow(currentRow++);
        Cell conversionTitleCell = conversionTitleRow.createCell(0);
        conversionTitleCell.setCellValue("DIMENSION CODE CONVERSION:");
        conversionTitleCell.setCellStyle(infoTitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        
        String[] conversions = {
            "X = 6 (e.g., X6 = 66 inches)",
            "S = 7 (e.g., S7 = 77 inches)",
            "E = 8 (e.g., E8 = 88 inches)",
            "N = 9 (e.g., N9 = 99 inches)"
        };
        
        for (String conversion : conversions) {
            Row convRow = sheet.createRow(currentRow++);
            Cell convCell = convRow.createCell(0);
            convCell.setCellValue("  • " + conversion);
            convCell.setCellStyle(infoStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        }
        
        // Empty row
        currentRow++;
        
        // Status Indicators with color examples
        Row statusTitleRow = sheet.createRow(currentRow++);
        Cell statusTitleCell = statusTitleRow.createCell(0);
        statusTitleCell.setCellValue("STATUS INDICATORS:");
        statusTitleCell.setCellStyle(infoTitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        
        // Instock example
        Row instockRow = sheet.createRow(currentRow++);
        Cell instockLabelCell = instockRow.createCell(0);
        instockLabelCell.setCellValue("  • 'instock' = Item currently available in warehouse");
        instockLabelCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 4));
        Cell instockExampleCell = instockRow.createCell(5);
        instockExampleCell.setCellValue("INSTOCK");
        instockExampleCell.setCellStyle(createInstockStyle(workbook));
        
        // Inbound example
        Row inboundRow = sheet.createRow(currentRow++);
        Cell inboundLabelCell = inboundRow.createCell(0);
        inboundLabelCell.setCellValue("  • 'inbound' = Item on the way, arriving soon");
        inboundLabelCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 4));
        Cell inboundExampleCell = inboundRow.createCell(5);
        inboundExampleCell.setCellValue("INBOUND");
        inboundExampleCell.setCellStyle(createInboundStyle(workbook));
        
        // Custom example
        Row customRow = sheet.createRow(currentRow++);
        Cell customLabelCell = customRow.createCell(0);
        customLabelCell.setCellValue("  • 'custom' = Not available, requires custom order");
        customLabelCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 4));
        Cell customExampleCell = customRow.createCell(5);
        customExampleCell.setCellValue("CUSTOM");
        customExampleCell.setCellStyle(createCustomStyle(workbook));
        
        // Empty row
        currentRow++;
        
        // Color Codes
        Row colorCodesTitleRow = sheet.createRow(currentRow++);
        Cell colorCodesTitleCell = colorCodesTitleRow.createCell(0);
        colorCodesTitleCell.setCellValue("COLOR CODES:");
        colorCodesTitleCell.setCellStyle(infoTitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        
        Map<String, String> colorCodes = new LinkedHashMap<>();
        colorCodes.put("1104", "Oxford Grey");
        colorCodes.put("1244", "Brazilian Mahogany");
        colorCodes.put("1239/1229", "Coffee Brown");
        colorCodes.put("3132", "Coastal Grey");
        colorCodes.put("3221", "Mahogany");
        colorCodes.put("3218", "Mayan Brown");
        
        for (Map.Entry<String, String> entry : colorCodes.entrySet()) {
            Row ccRow = sheet.createRow(currentRow++);
            Cell ccCell = ccRow.createCell(0);
            ccCell.setCellValue("  • " + entry.getKey() + " = " + entry.getValue());
            ccCell.setCellStyle(infoStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));
        }
        
        // Empty rows before data table
        currentRow++;
        currentRow++;
        
        // Data table header row - now all columns can be narrow
        Row headerRow = sheet.createRow(currentRow);
        
        // Dimension headers
        Cell dimAHeader = headerRow.createCell(0);
        dimAHeader.setCellValue("DimA");
        dimAHeader.setCellStyle(headerStyle);
        
        Cell dimBHeader = headerRow.createCell(1);
        dimBHeader.setCellValue("DimB");
        dimBHeader.setCellStyle(headerStyle);
        
        Cell dimCHeader = headerRow.createCell(2);
        dimCHeader.setCellValue("DimC");
        dimCHeader.setCellStyle(headerStyle);
        
        // Color variant headers
        int colIndex = 3;
        for (String colorCode : COLOR_CODES) {
            Cell header = headerRow.createCell(colIndex++);
            String colorName = COLOR_NAMES.get(colorCode);
            header.setCellValue(colorCode + " - " + colorName);
            header.setCellStyle(headerStyle);
        }
        
        // Set column widths - optimized for full screen view
        // Dimension columns (A, B, C) - moderate width
        sheet.setColumnWidth(0, 15 * 256);  // DimA
        sheet.setColumnWidth(1, 15 * 256);  // DimB
        sheet.setColumnWidth(2, 15 * 256);  // DimC
        
        // Color columns (D-I) - wider to show full color names and fill screen
        for (int i = 3; i < 9; i++) {
            sheet.setColumnWidth(i, 30 * 256); // 30 character width for color columns
        }
    }
    
    /**
     * Create style for informational text
     */
    private static CellStyle createInfoStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Arial");
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }
    
    /**
     * Create style for informational section titles
     */
    private static CellStyle createInfoTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setFontName("Arial");
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    /**
     * Create header style (bold, centered, light gray background)
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setFontName("Arial");
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Add borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }
    
    /**
     * Create style for dimension cells (neutral)
     */
    private static CellStyle createDimensionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Arial");
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Add borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }
    
    /**
     * Create style for instock items (green background)
     */
    private static CellStyle createInstockStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Arial");
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Light green background
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Add borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }
    
    /**
     * Create style for inbound items (yellow background)
     */
    private static CellStyle createInboundStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Arial");
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Light yellow background
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Add borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }
    
    /**
     * Create style for custom items (light red background)
     */
    private static CellStyle createCustomStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Arial");
        font.setColor(IndexedColors.BLACK.getIndex());
        font.setItalic(true);
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Light coral/red background
        style.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Add borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
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
}
