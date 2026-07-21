package com.example.primenestprop.common;

import org.springframework.http.HttpStatus;

public final class PasswordPolicy {
    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null
                || password.length() < 10
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[0-9].*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 10 characters and include uppercase, number, and symbol");
        }
    }
}
