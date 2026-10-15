package com.pharmasafe.service;

import com.pharmasafe.dto.HealthResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class HealthService {

    @Autowired(required = false)
    private DataSource dataSource;

    public HealthResponseDto checkHealth() {
        Map<String, Object> details = new HashMap<>();
        String dbStatus = "UNKNOWN";

        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(2)) {
                    dbStatus = "UP";
                    details.put("databaseProduct", conn.getMetaData().getDatabaseProductName());
                    details.put("databaseVersion", conn.getMetaData().getDatabaseProductVersion());
                    details.put("databaseName", conn.getCatalog());
                    details.put("databaseUser", conn.getMetaData().getUserName());
                } else {
                    dbStatus = "DOWN";
                }
            } catch (Exception e) {
                dbStatus = "DOWN: " + e.getMessage();
            }
        } else {
            dbStatus = "NOT_CONFIGURED";
        }

        details.put("database", dbStatus);
        details.put("javaVersion", System.getProperty("java.version"));
        details.put("osName", System.getProperty("os.name"));

        String overallStatus = "UP".equals(dbStatus) ? "UP" : "DEGRADED";

        return new HealthResponseDto(
                overallStatus,
                "PharmaSafe Pharmacovigilance Case Management",
                "1.0.0-STAGE1",
                Instant.now(),
                details
        );
    }
}
