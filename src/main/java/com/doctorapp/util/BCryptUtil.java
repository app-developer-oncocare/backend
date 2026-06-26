package com.doctorapp.util;

import org.mindrot.jbcrypt.BCrypt;

public class BCryptUtil {
    
    // Hash a password using BCrypt
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    // Verify a password against a hash
    public static boolean checkPassword(String password, String hashed) {
        if (hashed == null || !hashed.startsWith("$2a$")) {
            return false;
        }
        return BCrypt.checkpw(password, hashed);
    }
}
