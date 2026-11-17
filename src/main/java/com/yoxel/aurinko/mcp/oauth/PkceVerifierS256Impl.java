package com.yoxel.aurinko.mcp.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;

public class PkceVerifierS256Impl implements PkceVerifier {

    private static final Pattern CODE_VERIFIER = Pattern.compile("[A-Za-z0-9._~-]{43,128}");

    @Override
    public boolean verify(String codeVerifier, String codeChallenge) {
        if (codeVerifier == null || codeChallenge == null || !CODE_VERIFIER.matcher(codeVerifier).matches()) {
            return false;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            byte[] challenge = Base64.getUrlEncoder().withoutPadding().encode(digest);
            return MessageDigest.isEqual(challenge, codeChallenge.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
