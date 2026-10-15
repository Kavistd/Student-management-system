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
 * One-time CLI command that resets the password of an existing ADMIN account.
 * Active only under the {@code reset-admin-password} profile so a normal
 * startup never triggers it.
 *
 * <p>Usage:
 * <pre>
 * java -jar backend.jar \
 *      --spring.profiles.active=local,reset-admin-password \
 *      --admin.email=you@example.com
 * </pre>
 *
 * The new password is prompted for interactively (hidden, confirmed) or read
 * from the {@code ADMIN_RESET_PASSWORD} environment variable when no console
 * is attached. It is never accepted as a command-line argument.
 */
@Component
@Profile("reset-admin-password")
public class AdminPasswordResetRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordResetRunner.class);

    private final AdminPasswordResetService resetService;
    private final AdminPasswordSource passwordSource;
    private final ConfigurableApplicationContext context;

    @Autowired
    public AdminPasswordResetRunner(AdminPasswordResetService resetService,
                                    ConfigurableApplicationContext context) {
        this(resetService, AdminPasswordSource.forReset(), context);
    }

    AdminPasswordResetRunner(AdminPasswordResetService resetService,
                             AdminPasswordSource passwordSource,
                             ConfigurableApplicationContext context) {
        this.resetService = resetService;
        this.passwordSource = passwordSource;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = execute(args);
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /** Returns 0 on success, 1 on any refusal or failure. */
    int execute(ApplicationArguments args) {
        char[] password = null;
        try {
            String email = singleOption(args, "admin.email");
            password = passwordSource.readPassword();

            Long id = resetService.resetAdminPassword(email, password);
            log.info("ADMIN password reset successfully (user id {}).", id);
            return 0;
        } catch (ProvisioningException ex) {
            log.error("Admin password reset refused: {}", ex.getMessage());
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
