package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import lombok.RequiredArgsConstructor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

@RequiredArgsConstructor
public class UserSignatureHelper {

    private final AurinkoConfig config;

    public String sign(String userId, long timestampSeconds) {
        try {
            byte[] key = Base64.getUrlDecoder().decode(config.getClientSecret());
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((userId + timestampSeconds).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign Aurinko user", e);
        }
    }
}
