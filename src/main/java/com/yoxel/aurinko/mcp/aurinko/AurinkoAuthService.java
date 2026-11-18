package com.yoxel.aurinko.mcp.aurinko;

public interface AurinkoAuthService {

    AurinkoLogin exchangeCode(String code) throws AurinkoException;

    long exchangeConnectCode(String code) throws AurinkoException;
}
