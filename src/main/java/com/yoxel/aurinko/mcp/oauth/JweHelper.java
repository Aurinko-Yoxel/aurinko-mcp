package com.yoxel.aurinko.mcp.oauth;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@RequiredArgsConstructor
public class JweHelper {

    private final SecretKey key;
    private final Clock clock;

    public String encrypt(String type, Map<String, Object> data, Duration lifetime) {
        var claims = new JWTClaimsSet.Builder().claim("type", type).claim("data", data);
        if (lifetime != null) {
            claims.expirationTime(Date.from(clock.instant().plus(lifetime)));
        }
        var header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
                .type(JOSEObjectType.JWT)
                .build();
        var jwt = new EncryptedJWT(header, claims.build());
        try {
            jwt.encrypt(new DirectEncrypter(key));
            return jwt.serialize();
        } catch (JOSEException | IllegalArgumentException e) {
            throw new JweException(e);
        }
    }

    public Map<String, Object> decrypt(String value, String expectedType) {
        if (value == null || value.isBlank()) {
            throw new JweException();
        }
        try {
            for (String part : value.split("\\.", -1)) {
                if (!Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(Base64.getUrlDecoder().decode(part)).equals(part)) {
                    throw new JweException();
                }
            }
            var jwt = EncryptedJWT.parse(value);
            var header = jwt.getHeader();
            if (!JWEAlgorithm.DIR.equals(header.getAlgorithm())
                    || !EncryptionMethod.A256GCM.equals(header.getEncryptionMethod())
                    || header.getCompressionAlgorithm() != null) {
                throw new JweException();
            }
            jwt.decrypt(new DirectDecrypter(key));
            var claims = jwt.getJWTClaimsSet();
            var expiration = claims.getExpirationTime();
            if (!expectedType.equals(claims.getStringClaim("type"))
                    || (expiration != null && !expiration.toInstant().isAfter(clock.instant()))) {
                throw new JweException();
            }
            var data = claims.getJSONObjectClaim("data");
            if (data == null) {
                throw new JweException();
            }
            return data;
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            throw new JweException(e);
        }
    }
}
