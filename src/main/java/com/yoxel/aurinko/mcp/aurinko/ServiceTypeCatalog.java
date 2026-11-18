package com.yoxel.aurinko.mcp.aurinko;

import java.util.Map;

public class ServiceTypeCatalog {

    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "Google", "Gmail and Google Calendar, including Google Workspace; the user signs in with Google.",
            "Office365", "Microsoft 365, Outlook.com, Hotmail and Live mail and calendar; the user signs in with Microsoft.",
            "iCloud", "Apple iCloud Mail and Calendar (@icloud.com, @me.com, @mac.com); the user enters an Apple app-specific password.",
            "IMAP", "any other mailbox by IMAP (Yahoo, Zoho, internet provider mailboxes); the user enters the email and password or an app password.",
            "CalDav", "any other calendar by CalDAV server URL; the user enters the server URL, login and password.",
            "EWS", "Microsoft Exchange on-premises through EWS.");

    private ServiceTypeCatalog() {
    }

    public static String describe(String serviceType) {
        return DESCRIPTIONS.getOrDefault(serviceType, "Aurinko service type " + serviceType);
    }
}
