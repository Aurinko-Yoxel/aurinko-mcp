package com.yoxel.aurinko.mcp.tools;

import java.util.regex.Pattern;

public class EmailAddressValidator {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_LOCAL_LENGTH = 64;
    private static final Pattern ADDRESS = Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
                    + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+");

    private EmailAddressValidator() {
    }

    public static boolean isValid(String value) {
        return value != null && value.length() <= MAX_EMAIL_LENGTH
                && value.indexOf('@') <= MAX_LOCAL_LENGTH && ADDRESS.matcher(value).matches();
    }
}
