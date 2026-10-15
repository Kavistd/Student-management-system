package com.studentmanagement.backend.provisioning;

/** A provisioning request that was refused. Messages never contain secrets. */
public class ProvisioningException extends RuntimeException {

    public ProvisioningException(String message) {
        super(message);
    }
}
