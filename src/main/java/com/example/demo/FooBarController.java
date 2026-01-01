package com.example.demo;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api")
public class FooBarController {

    // Demo hardcoded credentials - should be detected by analysis
    private static final String DEMO_API_KEY = "demo-key-1234567890abcdef"; 
    private static final String FOOBAR_PASSWORD = "foobar123"; // Demo hardcoded password
    private static final String DEMO_TOKEN = "Bearer demo-jwt-token-abcd1234"; // Demo JWT token
    
    private Map<String, String> userDatabase = new HashMap<>();

    @GetMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        // Demo hardcoded credentials check
        if ("admin".equals(username) && "password123".equals(password)) {
            return "Login successful with API key: " + DEMO_API_KEY;
        }
        return "Login failed";
    }

    @PostMapping("/user")
    public String createUser(@RequestBody Map<String, String> userData) {
        String username = userData.get("username");
        // CRITICAL NULL POINTER BUG: userData.get("email") can return null
        // This will throw NullPointerException when email field is missing from request
        String email = userData.get("email").toLowerCase(); // NPE when email is null
        
        // Another NPE: username can also be null
        userDatabase.put(username.trim(), email); // NPE if username is null
        return "User created: " + username.toUpperCase(); // NPE if username is null
    }

    @GetMapping("/user/{id}")
    public String getUser(@PathVariable String id) {
        // CRITICAL NULL POINTER BUG: userDatabase.get() returns null for non-existent keys
        String user = userDatabase.get(id);
        // This will throw NullPointerException when user ID doesn't exist in database
        return "User: " + user.toUpperCase(); // NPE when user is null
    }

    @GetMapping("/config")
    public Map<String, String> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("db_password", FOOBAR_PASSWORD); // Exposing demo password
        config.put("api_key", DEMO_API_KEY); // Exposing demo API key
        config.put("secret", DEMO_TOKEN); // Exposing demo JWT token
        return config;
    }

    @PostMapping("/process")
    public String processData(@RequestBody Map<String, Object> data) {
        // CRITICAL NULL POINTER BUGS: Map.get() returns null for missing keys
        String name = (String) data.get("name");
        Integer age = (Integer) data.get("age");
        
        // Multiple NPE vulnerabilities:
        // 1. name.trim() throws NPE when name is null
        String processedName = name.trim().toUpperCase(); 
        // 2. Auto-unboxing throws NPE when age is null (Integer -> int)
        int processedAge = age + 10; 
        // 3. String concatenation with null references
        String description = data.get("description").toString(); // NPE if description missing
        
        return "Processed: " + processedName + ", Age: " + processedAge + ", Desc: " + description;
    }

    @GetMapping("/calculate")
    public String calculate(@RequestParam String value) {
        // CRITICAL NULL POINTER BUG: @RequestParam can be null if parameter not provided
        // value.trim() will throw NPE when value is null
        String cleanValue = value.trim(); 
        Double number = Double.parseDouble(cleanValue);
        
        // Method call on potentially null object
        return "Result: " + number.toString(); // NPE if parseDouble fails and returns null
    }

    @GetMapping("/timestamp")
    public Map<String, String> getTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> timestamps = new HashMap<>();
        
        // CRITICAL DATE FORMAT BUG: YYYY returns ISO week year instead of calendar year
        // This causes wrong dates around year boundaries (Dec 31 / Jan 1)
        // Example: Dec 31, 2023 with YYYY pattern returns "2024" (next week year)
        DateTimeFormatter weekYearBug1 = DateTimeFormatter.ofPattern("dd/MM/YYYY HH:mm:ss"); // BUG: Returns wrong year
        DateTimeFormatter weekYearBug2 = DateTimeFormatter.ofPattern("YYYY-MM-dd"); // BUG: Returns wrong year
        DateTimeFormatter reportDateBug = DateTimeFormatter.ofPattern("YYYY/MM/dd 'Report'"); // BUG: Wrong in reports
        DateTimeFormatter logTimeBug = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss.SSS"); // BUG: Wrong in logs
        
        // Correct patterns use lowercase yyyy for calendar year
        DateTimeFormatter correctFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        
        // These will return INCORRECT years around Dec 31 / Jan 1:
        timestamps.put("user_registration_date", now.format(weekYearBug1)); // Wrong year in user data
        timestamps.put("file_creation_date", now.format(weekYearBug2)); // Wrong year in file metadata
        timestamps.put("daily_report_date", now.format(reportDateBug)); // Wrong year in reports
        timestamps.put("log_timestamp", now.format(logTimeBug)); // Wrong year in application logs
        
        // This returns correct year:
        timestamps.put("correct_date", now.format(correctFormatter));
        
        return timestamps;
    }
}