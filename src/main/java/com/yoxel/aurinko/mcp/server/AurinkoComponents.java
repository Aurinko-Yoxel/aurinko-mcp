package com.yoxel.aurinko.mcp.server;

import com.yoxel.aurinko.AurinkoService;
import com.yoxel.aurinko.mcp.aurinko.AccountAccessValidator;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAccountServiceImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoAuthServiceImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoCalendarService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoCalendarServiceImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoClientFactory;
import com.yoxel.aurinko.mcp.aurinko.AurinkoClientFactoryImpl;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEmailService;
import com.yoxel.aurinko.mcp.aurinko.AurinkoEmailServiceImpl;
import com.yoxel.aurinko.mcp.config.AurinkoConfig;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AurinkoComponents {

    AurinkoService aurinkoService;
    AurinkoAccountService accountService;
    AurinkoAuthService authService;
    AurinkoClientFactory clientFactory;
    AurinkoEmailService emailService;
    AurinkoCalendarService calendarService;
    AccountAccessValidator accountAccessValidator;

    public static AurinkoComponents create(AurinkoConfig config) {
        var service = AurinkoService.createWithAppAuth(config.getUrl(), config.getClientId(), config.getClientSecret());
        return create(config, service, new AurinkoAccountServiceImpl(service));
    }

    public static AurinkoComponents create(AurinkoConfig config, AurinkoService service, AurinkoAccountService accountService) {
        var clientFactory = new AurinkoClientFactoryImpl(config);
        return new AurinkoComponents(service, accountService, new AurinkoAuthServiceImpl(service), clientFactory,
                new AurinkoEmailServiceImpl(clientFactory), new AurinkoCalendarServiceImpl(clientFactory),
                new AccountAccessValidator(accountService));
    }
}
