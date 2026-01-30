package zasyaSolutions.mySpaCoverSkuRecommendation.main;

import java.lang.reflect.Method;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;


import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import zasyaSolutions.mySpaCoverSkuRecommendation.auth.AuthManager;

public class BasePage {

	 protected static String token;
	 protected static String authToken;
	 public RequestSpecification requestSpec;
	 
	 public static String baseUrl;
	 public static String inventoryEndpoint;

	    @BeforeClass
	    public void initializeBasePage() {

	        this.baseUrl = ConfigReader.getProperty("base.url");
	        this.inventoryEndpoint = ConfigReader.getProperty("inventory.endpoint");
	        RestAssured.baseURI = baseUrl;
	        output("Base URL configured: " +  RestAssured.baseURI);
	        
	        
	    }
	    
	    @BeforeMethod
	    public void setup(Method method) {
	
     // Normal setup logic goes here
     System.out.println("Running normal setup for method: " + method.getName());

		 
	        // Load configuration
	        RestAssured.baseURI = this.baseUrl;
	        
	        output("Base URL configured: " + baseUrl);   
	            authToken = AuthManager.login();
	         
	            output("Token obtained: " + authToken.substring(0, Math.min(20, authToken.length())) + "...");

	            requestSpec = RestAssured.given()
	                .header("Content-Type", "application/json")
	                .header("Authorization", "Bearer " + authToken)
	            .header("X-Webhook-Key","22217870c81aa09243577ec0bc5eb0cd19f4fb4a70e067a0e65993e1ee7769fa");
	        
	        output("Request specification configured");
	    }
	 
	 
	 public  String baseURI() {
		 return RestAssured.baseURI = this.baseUrl;
	 }
	 public void output(Object value) {
		 System.out.println(value);
	 }

	    public static void setToken(String authToken) {
	        token = authToken;
	    }

	 
	    public static String getToken() {
	    	return token;
	    }
	
}
