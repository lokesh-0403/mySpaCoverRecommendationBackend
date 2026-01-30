package zasyaSolutions.mySpaCoverSkuRecommendation.auth;



import io.restassured.RestAssured;
import io.restassured.response.Response;
import zasyaSolutions.mySpaCoverSkuRecommendation.main.ConfigReader;
import zasyaSolutions.mySpaCoverSkuRecommendation.utils.PayloadGenerator;

public class AuthManager {
	private static String authToken;

	public static String login() {

		String baseUrl = ConfigReader.getProperty("base.url");
		String loginEndpoint = ConfigReader.getProperty("login.endpoint");
		
		
		Response response = RestAssured.given().baseUri(baseUrl)
				.header("Accept","application/json")
				.contentType("application/json")
				.body(PayloadGenerator.loginCredentials())
				.when().post(loginEndpoint);
		  System.out.println("=== LOGIN RESPONSE ===");
          response.prettyPrint();
         
          System.out.println(response);
		if (response.getStatusCode() != 200 && response.getStatusCode() != 201) {
			throw new RuntimeException("Login failed: " + response.getStatusLine());
		}

		// Try different JSON paths to extract token
		authToken = response.jsonPath().getString("token");
		if (authToken == null || authToken.isEmpty()) {
			authToken = response.jsonPath().getString("data.token");
		}
		if (authToken == null || authToken.isEmpty()) {
			authToken = response.jsonPath().getString("access_token");
		}

		if (authToken == null || authToken.isEmpty()) {
			throw new RuntimeException("Token not found in response!");
		}

		return authToken;
	}

	public static String loginWithEmail() {
		return login();
	}

	public static String getAuthToken() {
		if (authToken == null || authToken.isEmpty()) {
			// Auto-login with default credentials if token is not available
			String email = ConfigReader.getProperty("login.email");
			String password = ConfigReader.getProperty("login.password");
			if (email != null && password != null) {
				return login();
			}
		}
		return authToken;
	}

	public static void setAuthToken(String token) {
		authToken = token;
	}

	public static void clearAuthToken() {
		authToken = null;
	}

	public static String getBearerToken() {
		return "Bearer " + getAuthToken();
	}

	public static String refreshAuthToken() {
		String baseUrl = ConfigReader.getProperty("base.url");
		String refreshEndpoint = ConfigReader.getProperty("refresh.endpoint");

		Response response = RestAssured.given().baseUri(baseUrl).header("Authorization", "Bearer " + authToken).when()
				.post(refreshEndpoint);

		if (response.getStatusCode() == 200) {
			authToken = response.jsonPath().getString("token");
			if (authToken == null || authToken.isEmpty()) {
				authToken = response.jsonPath().getString("data.token");
			}
		} else {
			throw new RuntimeException("Token refresh failed: " + response.getStatusLine());
		}

		return authToken;
	}

	public static void logout() {
		String baseUrl = ConfigReader.getProperty("base.url");
		String logoutEndpoint = ConfigReader.getProperty("logout.endpoint");

		if (authToken == null || authToken.isEmpty()) {
			System.out.println("Logout request failed: No valid token available");
			return;
		}
	}
}
