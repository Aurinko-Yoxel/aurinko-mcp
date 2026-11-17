package com.yoxel.aurinko.mcp.aurinko;

public interface AurinkoAuthService {

    AurinkoLogin exchangeCode(String code) throws AurinkoException;
}
