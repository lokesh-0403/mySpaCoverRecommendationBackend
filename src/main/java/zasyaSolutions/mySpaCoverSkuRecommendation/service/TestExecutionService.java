package zasyaSolutions.mySpaCoverSkuRecommendation.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Service
public class TestExecutionService {

    // Target CSV file that will be replaced
    private static final String TARGET_CSV_PATH = "/Users/yeshsharma/Desktop/mySpaCoverSkuRecommendation/src/main/resources/testdata/spa_cover_dimensions.csv";
    
    // Directory where test results (Excel files) are generated
    private static final String RESULTS_BASE_DIR = "/Users/yeshsharma/Desktop/mySpaCoverSkuRecommendation/";
    
    // Track execution status
    private Map<String, Map<String, Object>> executionStatusMap = new ConcurrentHashMap<>();
    
    // Track result file paths
    private Map<String, String> resultFileMap = new ConcurrentHashMap<>();

    public TestExecutionService() {
        try {
            System.out.println("WORKING DIRECTORY = " + System.getProperty("user.dir"));
            
            // Ensure target directory exists
            Path targetDir = Paths.get(TARGET_CSV_PATH).getParent();
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
                System.out.println("Created target directory: " + targetDir);
            }
            
            System.out.println("TestExecutionService initialized successfully");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize TestExecutionService", e);
        }
    }

    /**
     * Replace the target CSV file with the uploaded file
     */
    public String replaceTargetCsvFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Uploaded file is empty");
        }
        
        // Validate file type
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
            throw new IOException("Only CSV files are allowed");
        }
        
        // Generate a unique file ID for tracking
        String fileId = UUID.randomUUID().toString();
        
        try {
            // Create backup of existing file (optional)
            Path targetPath = Paths.get(TARGET_CSV_PATH);
            if (Files.exists(targetPath)) {
                Path backupPath = Paths.get(TARGET_CSV_PATH + ".backup_" + System.currentTimeMillis());
                Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Created backup: " + backupPath);
            }
            
            // Replace the target CSV file
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Replaced target CSV file: " + TARGET_CSV_PATH);
            
            // Initialize status
            Map<String, Object> status = new HashMap<>();
            status.put("status", "uploaded");
            status.put("uploadTime", new Date());
            status.put("fileName", originalFilename);
            executionStatusMap.put(fileId, status);
            
            return fileId;
            
        } catch (IOException e) {
            throw new IOException("Failed to replace CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Execute TestNG tests asynchronously
     */
    public CompletableFuture<String> executeTestsAsync(String fileId) {
        // Update status to running
        Map<String, Object> status = executionStatusMap.getOrDefault(fileId, new HashMap<>());
        status.put("status", "running");
        status.put("startTime", new Date());
        executionStatusMap.put(fileId, status);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Run TestNG tests
                runTestNGTests(fileId);
                
                // Find the generated Excel report
                String resultFilePath = findLatestExcelReport();
                
                if (resultFilePath == null) {
                    throw new RuntimeException("No Excel report generated");
                }
                
                // Store the result file path
                resultFileMap.put(fileId, resultFilePath);
                
                // Update status
                Map<String, Object> completedStatus = new HashMap<>();
                completedStatus.put("status", "completed");
                completedStatus.put("endTime", new Date());
                completedStatus.put("resultFilePath", resultFilePath);
                executionStatusMap.put(fileId, completedStatus);
                
                System.out.println("Tests completed successfully. Result file: " + resultFilePath);
                return resultFilePath;
                
            } catch (Exception e) {
                Map<String, Object> errorStatus = new HashMap<>();
                errorStatus.put("status", "failed");
                errorStatus.put("error", e.getMessage());
                errorStatus.put("endTime", new Date());
                executionStatusMap.put(fileId, errorStatus);
                
                System.err.println("Test execution failed: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Test execution failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Execute TestNG tests synchronously (blocking)
     */
    public String executeTests(String fileId) throws Exception {
        // Update status to running
        Map<String, Object> status = executionStatusMap.getOrDefault(fileId, new HashMap<>());
        status.put("status", "running");
        status.put("startTime", new Date());
        executionStatusMap.put(fileId, status);
        
        try {
            // Run TestNG tests
            runTestNGTests(fileId);
            
            // Find the generated Excel report
            String resultFilePath = findLatestExcelReport();
            
            if (resultFilePath == null) {
                throw new RuntimeException("No Excel report generated");
            }
            
            // Store the result file path
            resultFileMap.put(fileId, resultFilePath);
            
            // Update status
            Map<String, Object> completedStatus = new HashMap<>();
            completedStatus.put("status", "completed");
            completedStatus.put("endTime", new Date());
            completedStatus.put("resultFilePath", resultFilePath);
            executionStatusMap.put(fileId, completedStatus);
            
            System.out.println("Tests completed successfully. Result file: " + resultFilePath);
            return resultFilePath;
            
        } catch (Exception e) {
            Map<String, Object> errorStatus = new HashMap<>();
            errorStatus.put("status", "failed");
            errorStatus.put("error", e.getMessage());
            errorStatus.put("endTime", new Date());
            executionStatusMap.put(fileId, errorStatus);
            
            System.err.println("Test execution failed: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Test execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Run TestNG tests programmatically
     */
    private void runTestNGTests(String fileId) {
        System.out.println("Starting TestNG execution for fileId: " + fileId);
        
        // Create TestNG suite programmatically
        XmlSuite suite = new XmlSuite();
        suite.setName("Spa Cover Inventory API Test Suite");
        
        XmlTest test = new XmlTest(suite);
        test.setName("Inventory API Tests");
        
        // Add the test class
        List<XmlClass> classes = new ArrayList<>();
        classes.add(new XmlClass("zasyaSolutions.mySpaCoverSkuRecommendation.tests.SpaCoverInventoryAPITest"));
        test.setXmlClasses(classes);
        
        // Create TestNG instance and run
        TestNG testng = new TestNG();
        testng.setVerbose(2); // Set verbose level
        
        List<XmlSuite> suites = new ArrayList<>();
        suites.add(suite);
        testng.setXmlSuites(suites);
        
        // Run the tests
        System.out.println("Executing TestNG suite...");
        testng.run();
        
        System.out.println("TestNG execution completed");
    }

    /**
     * Find the latest generated Excel report
     */
    private String findLatestExcelReport() {
        File baseDir = new File(RESULTS_BASE_DIR);
        
        // Look for Excel files matching the pattern: inventory_report_combined_*.xlsx
        File[] excelFiles = baseDir.listFiles((dir, name) -> 
            name.startsWith("inventory_report_") && name.endsWith(".xlsx")
        );
        
        if (excelFiles == null || excelFiles.length == 0) {
            System.out.println("No Excel report found in directory: " + RESULTS_BASE_DIR);
            return null;
        }
        
        // Find the most recently modified file
        File latestFile = null;
        long latestModified = 0;
        
        for (File file : excelFiles) {
            if (file.lastModified() > latestModified) {
                latestModified = file.lastModified();
                latestFile = file;
            }
        }
        
        if (latestFile != null) {
            System.out.println("Found latest Excel report: " + latestFile.getAbsolutePath());
            return latestFile.getAbsolutePath();
        }
        
        return null;
    }

    /**
     * Get the result file path for a given fileId
     */
    public Path getResultFilePath(String fileId) throws IOException {
        String filePath = resultFileMap.get(fileId);
        
        if (filePath == null) {
            // Try to find the latest report as fallback
            filePath = findLatestExcelReport();
        }
        
        if (filePath == null) {
            throw new IOException("Result file not found for ID: " + fileId);
        }
        
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Result file does not exist: " + filePath);
        }
        
        // Verify it's actually an Excel file
        if (!filePath.toLowerCase().endsWith(".xlsx")) {
            throw new IOException("Result file is not an Excel file: " + filePath);
        }
        
        System.out.println("Returning result file path: " + path.toAbsolutePath());
        return path;
    }

    /**
     * Get execution status for a given fileId
     */
    public Map<String, Object> getExecutionStatus(String fileId) {
        Map<String, Object> status = executionStatusMap.get(fileId);
        if (status == null) {
            Map<String, Object> notFound = new HashMap<>();
            notFound.put("status", "not_found");
            notFound.put("message", "No execution record found for this ID");
            return notFound;
        }
        return new HashMap<>(status); // Return a copy to prevent external modification
    }
    
    /**
     * Clean up old backup files (optional maintenance method)
     */
    public void cleanupOldBackups(int daysToKeep) {
        try {
            Path targetDir = Paths.get(TARGET_CSV_PATH).getParent();
            long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24L * 60 * 60 * 1000);
            
            Files.list(targetDir)
                .filter(path -> path.getFileName().toString().contains(".backup_"))
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        System.out.println("Deleted old backup: " + path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete backup: " + path);
                    }
                });
        } catch (IOException e) {
            System.err.println("Error during backup cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Delete all generated files for a specific fileId
     * This includes backup CSV files and generated Excel reports
     */
    public Map<String, Object> deleteFilesForId(String fileId) {
        Map<String, Object> result = new HashMap<>();
        List<String> deletedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            // 1. Delete the result Excel file if it exists
            String resultFilePath = resultFileMap.get(fileId);
            if (resultFilePath != null) {
                Path resultPath = Paths.get(resultFilePath);
                if (Files.exists(resultPath)) {
                    try {
                        Files.delete(resultPath);
                        deletedFiles.add(resultFilePath);
                        System.out.println("Deleted Excel report: " + resultFilePath);
                    } catch (IOException e) {
                        errors.add("Failed to delete Excel report: " + resultFilePath + " - " + e.getMessage());
                        System.err.println("Failed to delete Excel report: " + resultFilePath);
                    }
                }
                resultFileMap.remove(fileId);
            }
            
            // 2. Remove execution status
            executionStatusMap.remove(fileId);
            
            result.put("status", "success");
            result.put("deletedFiles", deletedFiles);
            result.put("deletedCount", deletedFiles.size());
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            result.put("message", "Cleaned up " + deletedFiles.size() + " file(s) for fileId: " + fileId);
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            result.put("deletedFiles", deletedFiles);
            result.put("errors", errors);
        }
        
        return result;
    }
    
    /**
     * Delete all generated Excel reports (cleanup all results)
     */
    public Map<String, Object> deleteAllGeneratedReports() {
        Map<String, Object> result = new HashMap<>();
        List<String> deletedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            File baseDir = new File(RESULTS_BASE_DIR);
            
            // Find all Excel reports
            File[] excelFiles = baseDir.listFiles((dir, name) -> 
                name.startsWith("inventory_report_") && name.endsWith(".xlsx")
            );
            
            if (excelFiles != null) {
                for (File file : excelFiles) {
                    try {
                        String filePath = file.getAbsolutePath();
                        Files.delete(file.toPath());
                        deletedFiles.add(filePath);
                        System.out.println("Deleted Excel report: " + filePath);
                    } catch (IOException e) {
                        errors.add("Failed to delete: " + file.getAbsolutePath() + " - " + e.getMessage());
                        System.err.println("Failed to delete: " + file.getAbsolutePath());
                    }
                }
            }
            
            // Clear all tracking maps
            resultFileMap.clear();
            executionStatusMap.clear();
            
            result.put("status", "success");
            result.put("deletedFiles", deletedFiles);
            result.put("deletedCount", deletedFiles.size());
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            result.put("message", "Cleaned up " + deletedFiles.size() + " Excel report(s)");
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            result.put("deletedFiles", deletedFiles);
            result.put("errors", errors);
        }
        
        return result;
    }
    
    /**
     * Delete all backup CSV files
     */
    public Map<String, Object> deleteAllBackups() {
        Map<String, Object> result = new HashMap<>();
        List<String> deletedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            Path targetDir = Paths.get(TARGET_CSV_PATH).getParent();
            
            Files.list(targetDir)
                .filter(path -> path.getFileName().toString().contains(".backup_"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        deletedFiles.add(path.toString());
                        System.out.println("Deleted backup: " + path);
                    } catch (IOException e) {
                        errors.add("Failed to delete backup: " + path + " - " + e.getMessage());
                        System.err.println("Failed to delete backup: " + path);
                    }
                });
            
            result.put("status", "success");
            result.put("deletedFiles", deletedFiles);
            result.put("deletedCount", deletedFiles.size());
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            result.put("message", "Cleaned up " + deletedFiles.size() + " backup file(s)");
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            result.put("deletedFiles", deletedFiles);
            result.put("errors", errors);
        }
        
        return result;
    }
    
    /**
     * Comprehensive cleanup - delete everything
     * Includes: Excel reports, backup CSV files, and clears all tracking data
     */
    public Map<String, Object> cleanupAll() {
        Map<String, Object> result = new HashMap<>();
        List<String> deletedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            // 1. Delete all Excel reports
            Map<String, Object> reportsResult = deleteAllGeneratedReports();
            if (reportsResult.containsKey("deletedFiles")) {
                deletedFiles.addAll((List<String>) reportsResult.get("deletedFiles"));
            }
            if (reportsResult.containsKey("errors")) {
                errors.addAll((List<String>) reportsResult.get("errors"));
            }
            
            // 2. Delete all backups
            Map<String, Object> backupsResult = deleteAllBackups();
            if (backupsResult.containsKey("deletedFiles")) {
                deletedFiles.addAll((List<String>) backupsResult.get("deletedFiles"));
            }
            if (backupsResult.containsKey("errors")) {
                errors.addAll((List<String>) backupsResult.get("errors"));
            }
            
            result.put("status", "success");
            result.put("deletedFiles", deletedFiles);
            result.put("deletedCount", deletedFiles.size());
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            result.put("message", "Complete cleanup finished. Deleted " + deletedFiles.size() + " file(s)");
            
            System.out.println("Complete cleanup finished. Deleted " + deletedFiles.size() + " file(s)");
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            result.put("deletedFiles", deletedFiles);
            result.put("errors", errors);
        }
        
        return result;
    }
}