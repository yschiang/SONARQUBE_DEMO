package com.example.demo;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DateFormatDemo {

    /**
     * CRITICAL DATE FORMATTING BUG DEMONSTRATION
     * 
     * The YYYY pattern represents ISO week year, not calendar year.
     * This causes incorrect dates around year boundaries:
     * - December 31, 2023 with YYYY returns "2024" (wrong year)
     * - January 1, 2024 with YYYY might return "2023" (wrong year)
     * 
     * Impact: Wrong dates in financial reports, user records, file timestamps, logs
     */

    @GetMapping("/financial-report")
    public Map<String, Object> generateFinancialReport() {
        LocalDateTime reportTime = LocalDateTime.now();
        Map<String, Object> report = new HashMap<>();
        
        // BUG: Using YYYY for financial year-end reports
        // This will show WRONG YEAR in December financial reports
        DateTimeFormatter fiscalYearFormat = DateTimeFormatter.ofPattern("'FY' YYYY"); // WRONG: should be yyyy
        DateTimeFormatter reportDateFormat = DateTimeFormatter.ofPattern("YYYY-MM-dd 'Financial Report'"); // WRONG: should be yyyy
        
        report.put("fiscal_year", reportTime.format(fiscalYearFormat)); // Wrong year near Dec 31
        report.put("report_date", reportTime.format(reportDateFormat)); // Wrong year near Dec 31
        report.put("quarter", "Q4 " + reportTime.format(DateTimeFormatter.ofPattern("YYYY"))); // WRONG: should be yyyy
        
        return report;
    }

    @GetMapping("/user-activity-log")
    public Map<String, String> logUserActivity(@RequestParam String userId) {
        LocalDateTime activityTime = LocalDateTime.now();
        Map<String, String> activityLog = new HashMap<>();
        
        // BUG: Using YYYY in user activity logging
        // This will create WRONG TIMESTAMPS in logs around year boundaries
        DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss.SSS '[USER_ACTIVITY]'"); // WRONG: should be yyyy
        DateTimeFormatter fileNameFormat = DateTimeFormatter.ofPattern("'activity_log_' YYYY_MM_dd '.txt'"); // WRONG: should be yyyy
        
        String logEntry = activityTime.format(logFormat) + " User: " + userId + " - Action: LOGIN";
        String fileName = activityTime.format(fileNameFormat);
        
        activityLog.put("log_entry", logEntry); // Wrong timestamp in logs
        activityLog.put("log_file", fileName); // Wrong filename with wrong year
        
        return activityLog;
    }

    @PostMapping("/backup-schedule")
    public Map<String, String> scheduleBackup(@RequestBody Map<String, String> backupConfig) {
        LocalDateTime scheduleTime = LocalDateTime.now();
        Map<String, String> schedule = new HashMap<>();
        
        // BUG: Using YYYY in backup scheduling and file naming
        // This will create WRONG BACKUP SCHEDULES and WRONG FILENAMES
        DateTimeFormatter backupDateFormat = DateTimeFormatter.ofPattern("'backup_' YYYY-MM-dd '_' HH-mm-ss"); // WRONG: should be yyyy
        DateTimeFormatter scheduleFormat = DateTimeFormatter.ofPattern("'Next backup: ' YYYY/MM/dd"); // WRONG: should be yyyy
        DateTimeFormatter auditFormat = DateTimeFormatter.ofPattern("'Scheduled on: ' YYYY-MM-dd 'at' HH:mm"); // WRONG: should be yyyy
        
        String backupFileName = scheduleTime.format(backupDateFormat) + ".sql";
        String nextBackup = scheduleTime.plusDays(1).format(scheduleFormat);
        String auditEntry = scheduleTime.format(auditFormat);
        
        schedule.put("backup_file", backupFileName); // Wrong year in backup filename
        schedule.put("next_backup", nextBackup); // Wrong year in schedule
        schedule.put("audit_log", auditEntry); // Wrong year in audit trail
        
        return schedule;
    }

    @GetMapping("/certificate-expiry")
    public Map<String, String> checkCertificateExpiry() {
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime expiryTime = currentTime.plusDays(365);
        Map<String, String> certInfo = new HashMap<>();
        
        // BUG: Using YYYY in certificate expiry dates
        // This will show WRONG EXPIRY DATES for certificates issued near year-end
        DateTimeFormatter expiryFormat = DateTimeFormatter.ofPattern("'Expires: ' YYYY-MM-dd"); // WRONG: should be yyyy
        DateTimeFormatter issuedFormat = DateTimeFormatter.ofPattern("'Issued: ' YYYY-MM-dd"); // WRONG: should be yyyy
        DateTimeFormatter alertFormat = DateTimeFormatter.ofPattern("'Alert: Certificate expires on ' YYYY/MM/dd"); // WRONG: should be yyyy
        
        certInfo.put("issued_date", currentTime.format(issuedFormat)); // Wrong issue date
        certInfo.put("expiry_date", expiryTime.format(expiryFormat)); // Wrong expiry date
        certInfo.put("alert_message", expiryTime.format(alertFormat)); // Wrong alert date
        
        return certInfo;
    }

    @PostMapping("/invoice-generation")
    public Map<String, Object> generateInvoice(@RequestBody Map<String, Object> invoiceData) {
        LocalDateTime invoiceTime = LocalDateTime.now();
        LocalDateTime dueTime = invoiceTime.plusDays(30);
        Map<String, Object> invoice = new HashMap<>();
        
        // BUG: Using YYYY in invoice dates - CRITICAL for accounting and legal compliance
        // This will generate INVOICES WITH WRONG DATES near year boundaries
        DateTimeFormatter invoiceDateFormat = DateTimeFormatter.ofPattern("'Invoice Date: ' YYYY-MM-dd"); // WRONG: should be yyyy
        DateTimeFormatter dueDateFormat = DateTimeFormatter.ofPattern("'Due Date: ' YYYY/MM/dd"); // WRONG: should be yyyy
        DateTimeFormatter invoiceIdFormat = DateTimeFormatter.ofPattern("'INV-' YYYY MM dd '-' HHmmss"); // WRONG: should be yyyy
        
        String invoiceId = invoiceTime.format(invoiceIdFormat);
        String invoiceDate = invoiceTime.format(invoiceDateFormat);
        String dueDate = dueTime.format(dueDateFormat);
        
        invoice.put("invoice_id", invoiceId); // Wrong year in invoice ID
        invoice.put("invoice_date", invoiceDate); // Wrong invoice date - legal issue!
        invoice.put("due_date", dueDate); // Wrong due date - payment confusion!
        invoice.put("amount", invoiceData.get("amount"));
        
        return invoice;
    }

    @GetMapping("/compliance-report")
    public Map<String, String> generateComplianceReport() {
        LocalDateTime reportTime = LocalDateTime.now();
        Map<String, String> complianceData = new HashMap<>();
        
        // BUG: Using YYYY in compliance and regulatory reports
        // This will cause WRONG DATES in official compliance documentation
        DateTimeFormatter complianceFormat = DateTimeFormatter.ofPattern("'Compliance Report for ' YYYY"); // WRONG: should be yyyy
        DateTimeFormatter auditFormat = DateTimeFormatter.ofPattern("'Last Audit: ' YYYY-MM-dd"); // WRONG: should be yyyy
        DateTimeFormatter nextReviewFormat = DateTimeFormatter.ofPattern("'Next Review: ' YYYY/MM/dd"); // WRONG: should be yyyy
        
        LocalDateTime lastAudit = reportTime.minusDays(90);
        LocalDateTime nextReview = reportTime.plusDays(90);
        
        complianceData.put("report_period", reportTime.format(complianceFormat)); // Wrong compliance year
        complianceData.put("last_audit", lastAudit.format(auditFormat)); // Wrong audit date
        complianceData.put("next_review", nextReview.format(nextReviewFormat)); // Wrong review date
        
        return complianceData;
    }

    @GetMapping("/correct-examples")
    public Map<String, String> showCorrectDateFormats() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> examples = new HashMap<>();
        
        // CORRECT usage: Use lowercase yyyy for calendar year
        DateTimeFormatter correctFormat1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter correctFormat2 = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter correctFormat3 = DateTimeFormatter.ofPattern("'Report for ' yyyy");
        
        examples.put("correct_timestamp", now.format(correctFormat1));
        examples.put("correct_date", now.format(correctFormat2));
        examples.put("correct_report", now.format(correctFormat3));
        
        return examples;
    }
}