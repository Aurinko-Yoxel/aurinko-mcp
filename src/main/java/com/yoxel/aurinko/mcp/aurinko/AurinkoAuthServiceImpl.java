package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.AurinkoService;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class AurinkoAuthServiceImpl implements AurinkoAuthService {

    private final AurinkoService service;

    @Override
    public long exchangeConnectCode(String code) throws AurinkoException {
        try {
            if (code == null || code.isBlank()) {
                throw new AurinkoException();
            }
            var result = service.auth.exchangeCode(URLEncoder.encode(code, StandardCharsets.UTF_8).replace("+", "%20"));
            if (result == null || result.getAccountId() <= 0) {
                throw new AurinkoException();
            }
            return result.getAccountId();
        } catch (AurinkoException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new AurinkoException(e);
        }
    }

    @Override
    public AurinkoLogin exchangeCode(String code) throws AurinkoException {
        try {
            if (code == null || code.isBlank()) {
                throw new AurinkoException();
            }
            var result = service.auth.exchangeCode(URLEncoder.encode(code, StandardCharsets.UTF_8).replace("+", "%20"));
            if (result == null || result.getUserId() == null || result.getUserId().isBlank()
                    || result.getUserSession() == null || result.getUserSession().isBlank()) {
                throw new AurinkoException();
            }
            return new AurinkoLogin(result.getUserId(), result.getUserSession(), result.getAccountId());
        } catch (AurinkoException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new AurinkoException(e);
        }
    }
}
