package com.studentmanagement.backend.provisioning;

import java.io.Console;
import java.util.Arrays;

/**
 * Where the new admin's password comes from: a hidden, confirmed console
 * prompt, or, when no console is attached, the ADMIN_BOOTSTRAP_PASSWORD
 * environment variable (unset it after the run). Never a command-line
 * argument, which would land in shell history and process listings.
 */
public interface AdminPasswordSource {

    char[] readPassword();

    static AdminPasswordSource systemDefault() {
        return forEnvVar("ADMIN_BOOTSTRAP_PASSWORD", "New ADMIN password (min %d characters): ");
    }

    /**
     * Password source for the reset-admin-password profile.
     * Reads from the console (hidden, confirmed) or from ADMIN_RESET_PASSWORD
     * when no console is attached. Using a distinct env-var name prevents
     * accidentally reusing a bootstrap secret for a reset.
     */
    static AdminPasswordSource forReset() {
        return forEnvVar("ADMIN_RESET_PASSWORD", "New ADMIN password (min %d characters): ");
    }

    private static AdminPasswordSource forEnvVar(String envVar, String prompt) {
        return () -> {
            Console console = System.console();
            if (console != null) {
                char[] first = console.readPassword(prompt,
                        AdminProvisioningService.MIN_PASSWORD_LENGTH);
                char[] second = console.readPassword("Confirm password: ");
                if (first == null || second == null || !Arrays.equals(first, second)) {
                    if (first != null) Arrays.fill(first, '\0');
                    if (second != null) Arrays.fill(second, '\0');
                    throw new ProvisioningException("Passwords did not match; nothing was changed.");
                }
                Arrays.fill(second, '\0');
                return first;
            }

            String fromEnv = System.getenv(envVar);
            if (fromEnv != null && !fromEnv.isEmpty()) {
                return fromEnv.toCharArray();
            }

            throw new ProvisioningException(
                    "No console is attached. Run from a terminal to be prompted, or set "
                            + envVar + " for this one run.");
        };
    }
}
