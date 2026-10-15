package com.studentmanagement.backend.provisioning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * One-time command that creates the first ADMIN account. It exists only under
 * the {@code provision-admin} profile, so a normal startup never creates an
 * admin. Usage (see README):
 *
 * <pre>
 * java -jar backend.jar --spring.profiles.active=local,provision-admin \
 *      --admin.email=you@example.com --admin.username=you
 * </pre>
 *
 * The password is prompted for (hidden) and is never logged.
 */
@Component
@Profile("provision-admin")
public class AdminProvisioningRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminProvisioningRunner.class);

    private final AdminProvisioningService provisioningService;
    private final AdminPasswordSource passwordSource;
    private final ConfigurableApplicationContext context;

    @Autowired
    public AdminProvisioningRunner(AdminProvisioningService provisioningService,
                                   ConfigurableApplicationContext context) {
        this(provisioningService, AdminPasswordSource.systemDefault(), context);
    }

    AdminProvisioningRunner(AdminProvisioningService provisioningService,
                            AdminPasswordSource passwordSource,
                            ConfigurableApplicationContext context) {
        this.provisioningService = provisioningService;
        this.passwordSource = passwordSource;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = execute(args);
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /** Returns the process exit code: 0 created, 1 refused or failed. */
    int execute(ApplicationArguments args) {
        char[] password = null;
        try {
            String email = singleOption(args, "admin.email");
            String username = singleOption(args, "admin.username");
            password = passwordSource.readPassword();

            Long id = provisioningService.provisionInitialAdmin(email, username, password);
            log.info("Initial ADMIN account created (user id {}).", id);
            return 0;
        } catch (ProvisioningException ex) {
            log.error("Admin provisioning refused: {}", ex.getMessage());
            return 1;
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private static String singleOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) {
            throw new ProvisioningException("Pass exactly one --" + name + "=... argument.");
        }
        return values.getFirst();
    }
}
