package com.studentmanagement.backend.util;

/** The contact-number rule shared by sign-up and the student's own profile edits. */
public final class ContactNumber {
    private ContactNumber() {}

    /** 7 to 15 digits; spaces, hyphens, brackets and a leading + are allowed. */
    public static final String PATTERN = "^(?=(?:\\D*\\d){7,15}\\D*$)\\s*\\+?[0-9 ()-]+$";

    public static final String MESSAGE =
            "Enter a valid contact number (7 to 15 digits; spaces, +, - and brackets are allowed)";

    /** Trims and collapses repeated whitespace before storing. */
    public static String normalize(String phone) {
        return phone.trim().replaceAll("\\s+", " ");
    }
}
