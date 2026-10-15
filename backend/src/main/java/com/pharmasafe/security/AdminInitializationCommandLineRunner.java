package com.pharmasafe.security;

import com.pharmasafe.service.AdminInitializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializationCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializationCommandLineRunner.class);

    private final AdminInitializationService adminInitializationService;
    private final Environment environment;

    public AdminInitializationCommandLineRunner(AdminInitializationService adminInitializationService,
                                                Environment environment) {
        this.adminInitializationService = adminInitializationService;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        String initUsername = environment.getProperty("INIT_ADMIN_USERNAME");
        String initPassword = environment.getProperty("INIT_ADMIN_PASSWORD");

        if (initUsername != null && !initUsername.isBlank() && initPassword != null && !initPassword.isBlank()) {
            try {
                if (adminInitializationService.hasAdminAccount()) {
                    log.warn("Administrator initialization skipped: an administrator account already exists.");
                } else {
                    adminInitializationService.initializeFirstAdmin(initUsername, initPassword);
                    log.info("PharmaSafe initial administrator successfully created for user: {}", initUsername.trim().toLowerCase());
                }
            } catch (Exception e) {
                log.error("Failed to initialize administrator: {}", e.getMessage());
            }
        }

        String diagUsername = environment.getProperty("DIAGNOSE_USERNAME");
        String diagPassword = environment.getProperty("DIAGNOSE_PASSWORD");
        if (diagUsername != null && !diagUsername.isBlank() && diagPassword != null) {
            boolean match = adminInitializationService.diagnoseUserAuth(diagUsername, diagPassword);
            log.info("Safe auth diagnostic completed for '{}': match={}", diagUsername.trim().toLowerCase(), match);
        }

        String recoverUsername = environment.getProperty("RECOVER_ADMIN_USERNAME");
        String recoverPassword = environment.getProperty("RECOVER_ADMIN_PASSWORD");
        if (recoverUsername != null && !recoverUsername.isBlank() && recoverPassword != null && !recoverPassword.isBlank()) {
            try {
                adminInitializationService.recoverAdminAccount(recoverUsername, recoverPassword);
                log.info("PharmaSafe administrator recovery completed successfully for user: {}", recoverUsername.trim().toLowerCase());
            } catch (Exception e) {
                log.error("Failed to recover administrator: {}", e.getMessage());
                throw new RuntimeException("Administrator recovery failed: " + e.getMessage(), e);
            }
        }
    }
}
