package zasyaSolutions.mySpaCoverSkuRecommendation.tests;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class YourApiTest {

    private String inputFilePath;
    private String outputFilePath;
    private List<String[]> inputData;
    private List<String[]> outputData;

    @Parameters({"inputFile", "outputFile"})
    @BeforeClass
    public void setup(String inputFile, String outputFile) {
        this.inputFilePath = inputFile;
        this.outputFilePath = outputFile;
        this.outputData = new ArrayList<>();
        
        // Read input file
        readInputFile();
    }

    private void readInputFile() {
        inputData = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath))) {
            String line;
            boolean isHeader = true;
            
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (isHeader) {
                    // Store header if needed
                    isHeader = false;
                    continue;
                }
                inputData.add(values);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @DataProvider(name = "testData")
    public Object[][] getTestData() {
        Object[][] data = new Object[inputData.size()][1];
        for (int i = 0; i < inputData.size(); i++) {
            data[i][0] = inputData.get(i);
        }
        return data;
    }

    @Test(dataProvider = "testData")
    public void testApiWithData(String[] rowData) {
        // Prepare API payload from row data
        String payload = prepareApiPayload(rowData);
        
        // Make API call
        Response response = RestAssured
            .given()
                .contentType("application/json")
                .body(payload)
            .when()
                .post("YOUR_API_ENDPOINT") // Replace with your actual API endpoint
            .then()
                .extract()
                .response();
        
        // Process and filter response
        String filteredResult = filterResponse(response);
        
        // Store result for output
        synchronized (outputData) {
            outputData.add(new String[]{
                rowData[0], // Original data column 1
                rowData[1], // Original data column 2
                filteredResult, // Processed result
                String.valueOf(response.getStatusCode())
            });
        }
    }

    private String prepareApiPayload(String[] rowData) {
        // Your existing logic to prepare API payload
        // Example:
        return String.format("{\"field1\": \"%s\", \"field2\": \"%s\"}", 
            rowData[0], rowData[1]);
    }

    private String filterResponse(Response response) {
        // Your existing logic to filter response
        // Example:
        String responseBody = response.getBody().asString();
        // Apply your filtering logic here
        return responseBody; // Return filtered data
    }

    @org.testng.annotations.AfterClass
    public void writeResults() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFilePath))) {
            // Write header
            writer.println("Input1,Input2,Result,StatusCode");
            
            // Write data
            for (String[] row : outputData) {
                writer.println(String.join(",", row));
            }
            
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}