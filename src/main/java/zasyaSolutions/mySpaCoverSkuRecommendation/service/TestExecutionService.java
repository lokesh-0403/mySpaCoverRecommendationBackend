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

@Service
public class TestExecutionService {

    private static final String UPLOAD_DIR = "uploads/";
    private static final String RESULTS_DIR = "results/";
    
    // Track execution status
    private Map<String, Map<String, Object>> executionStatusMap = new ConcurrentHashMap<>();

//    public TestExecutionService() {
//        // Create directories if they don't exist
//        new File(UPLOAD_DIR).mkdirs();
//        new File(RESULTS_DIR).mkdirs();
//    }
//    
    
//    
//    public TestExecutionService() {
//        System.out.println("WORKING DIRECTORY = " + System.getProperty("user.dir"));
//
//        boolean uploadCreated = new File(UPLOAD_DIR).mkdirs();
//        boolean resultCreated = new File(RESULTS_DIR).mkdirs();
//
//        System.out.println("uploads created: " + uploadCreated);
//        System.out.println("results created: " + resultCreated);
//    }

    
    
    public TestExecutionService() {
        try {
            System.out.println("WORKING DIRECTORY = " + System.getProperty("user.dir"));

            Files.createDirectories(Paths.get(UPLOAD_DIR));
            Files.createDirectories(Paths.get(RESULTS_DIR));

            System.out.println("uploads directory ready");
            System.out.println("results directory ready");

        } catch (IOException e) {
            throw new RuntimeException("Failed to create required directories", e);
        }
    }

    
    
    
    

    public String saveUploadedFile(MultipartFile file) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String fileName = fileId + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(UPLOAD_DIR + fileName);
        
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        return fileId;
    }

    public String executeTestsWithFile(String fileId) throws Exception {
        // Set status to running
        Map<String, Object> status = new HashMap<>();
        status.put("status", "running");
        status.put("startTime", new Date());
        executionStatusMap.put(fileId, status);
        
        // Find uploaded file
        Path inputFilePath = findFileById(UPLOAD_DIR, fileId);
        if (inputFilePath == null) {
            throw new Exception("Input file not found for ID: " + fileId);
        }
        
        // Prepare result file path
        String resultFileId = UUID.randomUUID().toString();
        String resultFileName = resultFileId + "_results.csv";
        Path resultFilePath = Paths.get(RESULTS_DIR + resultFileName);
        
        // Run tests in a separate thread to avoid blocking
        new Thread(() -> {
            try {
                runTestNGTests(inputFilePath.toString(), resultFilePath.toString());
                
                // Update status
                Map<String, Object> completedStatus = new HashMap<>();
                completedStatus.put("status", "completed");
                completedStatus.put("endTime", new Date());
                completedStatus.put("resultFileId", resultFileId);
                executionStatusMap.put(fileId, completedStatus);
                
            } catch (Exception e) {
                Map<String, Object> errorStatus = new HashMap<>();
                errorStatus.put("status", "failed");
                errorStatus.put("error", e.getMessage());
                errorStatus.put("endTime", new Date());
                executionStatusMap.put(fileId, errorStatus);
            }
        }).start();
        
        return resultFileId;
    }

    private void runTestNGTests(String inputFilePath, String resultFilePath) {
        // Create TestNG suite programmatically
        XmlSuite suite = new XmlSuite();
        suite.setName("API Automation Suite");
        
        XmlTest test = new XmlTest(suite);
        test.setName("API Tests");
        
        // Add your test class here
        List<XmlClass> classes = new ArrayList<>();
        classes.add(new XmlClass("com.automation.tests.YourApiTest")); // Replace with your actual test class
        test.setXmlClasses(classes);
        
        // Set parameters for input and output files
        Map<String, String> parameters = new HashMap<>();
        parameters.put("inputFile", inputFilePath);
        parameters.put("outputFile", resultFilePath);
        test.setParameters(parameters);
        
        // Create TestNG instance and run
        TestNG testng = new TestNG();
        List<XmlSuite> suites = new ArrayList<>();
        suites.add(suite);
        testng.setXmlSuites(suites);
        testng.run();
    }

    public Path getResultFilePath(String fileId) throws IOException {
        Path resultPath = findFileById(UPLOAD_DIR, fileId);
        if (resultPath == null) {
            throw new IOException("Result file not found for ID: " + fileId);
        }
        return resultPath;
    }

    public Map<String, Object> getExecutionStatus(String fileId) {
        Map<String, Object> status = executionStatusMap.get(fileId);
        if (status == null) {
            Map<String, Object> notFound = new HashMap<>();
            notFound.put("status", "not_found");
            return notFound;
        }
        return status;
    }

    private Path findFileById(String directory, String fileId) throws IOException {
        File dir = new File(directory);
        File[] files = dir.listFiles((d, name) -> name.startsWith(fileId));
        
        if (files != null && files.length > 0) {
            return files[0].toPath();
        }
        return null;
    }
}