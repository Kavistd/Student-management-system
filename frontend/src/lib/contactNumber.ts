/** Same rule as the backend's ContactNumber: 7 to 15 digits; spaces, +, - and brackets allowed. */
export const PHONE_PATTERN = /^(?=(?:\D*\d){7,15}\D*$)\s*\+?[0-9 ()-]+$/;

export const PHONE_HINT =
  "Enter a valid contact number (7 to 15 digits; spaces, +, - and brackets are allowed).";
