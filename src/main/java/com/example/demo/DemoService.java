package com.example.demo;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@Service
public class DemoService {

    // Demo hardcoded credentials for testing
    private final String DEMO_ACCESS_KEY = "DEMO_AKIAIOSFODNN7EXAMPLE"; // Demo AWS pattern
    private final String DEMO_SECRET_KEY = "demo_wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"; 
    private final String DEMO_DB_URL = "jdbc:mysql://localhost:3306/demodb?user=demo&password=foobar123";
    
    public String authenticateUser(String token) {
        // CRITICAL NULL POINTER BUG: token parameter can be null from HTTP requests
        // token.startsWith() throws NPE when token is null
        if (token.startsWith("Bearer ")) { // NPE when token is null
            String cleanToken = token.substring(7);
            return validateToken(cleanToken);
        }
        // Another NPE: calling methods on token without null check
        return "Invalid token: " + token.toUpperCase(); // NPE when token is null
    }
    
    private String validateToken(String token) {
        // CRITICAL NULL POINTER BUG: token can be null from authenticateUser()
        // token.length() throws NPE when token is null
        if (token.length() > 10) { // NPE when token is null
            // Multiple method calls on potentially null object
            String processed = token.trim().toUpperCase(); // NPE if token is null
            return "Valid: " + processed.substring(0, 5); // NPE if processed is null
        }
        return "invalid";
    }

    public List<String> getUserRoles(String username) {
        List<String> roles = new ArrayList<>();
        
        // CRITICAL NULL POINTER BUGS: username parameter can be null from web requests
        // username.equals() throws NPE when username is null
        if (username.equals("admin")) { // NPE when username is null
            roles.add("ADMIN");
            // Additional NPE: calling methods without null check
            roles.add("ROLE_" + username.toUpperCase()); // NPE if username becomes null
        } else if (username.contains("@")) { // NPE when username is null
            roles.add("USER");
            // Chained method calls on null object
            String domain = username.split("@")[1].toLowerCase(); // NPE if username is null
            roles.add("DOMAIN_" + domain.replace(".", "_")); // NPE if domain is null
        }
        
        return roles;
    }

    public boolean hasAccess(String userId, String resource) {
        // CRITICAL NULL POINTER BUGS: Chain of null pointer vulnerabilities
        String userType = getUserType(userId); // Can return null
        // resource.split() throws NPE when resource is null
        String resourceLevel = resource.split("/")[0]; // NPE when resource is null
        String resourceName = resource.substring(resource.lastIndexOf("/") + 1); // NPE when resource is null
        
        // Multiple NPE vulnerabilities in boolean expression:
        // 1. userType.equals() throws NPE when userType is null
        // 2. resourceLevel.equals() throws NPE when resourceLevel is null  
        // 3. resourceName.startsWith() throws NPE when resourceName is null
        return userType.equals("premium") && 
               resourceLevel.equals("private") && 
               resourceName.startsWith("secret"); // All three can throw NPE
    }
    
    private String getUserType(String userId) {
        // CRITICAL NULL POINTER BUG: userId can be null from hasAccess()
        // userId.startsWith() throws NPE when userId is null
        if (userId.startsWith("premium_")) { // NPE when userId is null
            // More method calls on potentially null object
            String userIdUpper = userId.toUpperCase(); // NPE if userId is null
            return userIdUpper.contains("VIP") ? "premium_vip" : "premium";
        } else if (userId.length() > 5) { // NPE when userId is null
            return "standard";
        }
        return null; // Deliberately returning null to cause NPE downstream
    }

    public String encryptData(String data, String key) {
        // CRITICAL NULL POINTER BUGS: data and key parameters can be null
        // data.trim() throws NPE when data is null
        String trimmedData = data.trim(); // NPE when data is null
        // key.toUpperCase() throws NPE when key is null
        String upperKey = key.toUpperCase(); // NPE when key is null
        
        // Additional NPE vulnerabilities:
        String dataHash = data.substring(0, Math.min(10, data.length())); // NPE when data is null
        String keyReversed = new StringBuilder(key).reverse().toString(); // NPE when key is null
        
        // Demo encryption using hardcoded key
        String encryptionKey = "demoKey123!@#"; // Demo hardcoded credential
        
        return trimmedData + ":" + upperKey + ":" + dataHash + ":" + keyReversed + ":" + encryptionKey;
    }
}