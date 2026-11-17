package com.yoxel.aurinko.mcp.accounts;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectPageRendererTest {

    @Test
    void confirmationEscapesExternalValues() {
        String html = new ConnectPageRenderer().confirmPage("<IMAP>\"&'", List.of("<mail>\"&'@example.com"),
                "https://api.example.com/?q=\"<>&'");

        assertThat(html).contains("lang=\"en\"", "Connect a mailbox to Aurinko", "Continue",
                "This will add an account to the user that already has:",
                "&lt;IMAP&gt;&quot;&amp;&#39;", "&lt;mail&gt;&quot;&amp;&#39;@example.com", "?q=&quot;&lt;&gt;&amp;&#39;")
                .doesNotContain("<IMAP>", "<mail>");
    }

    @Test
    void resultPagesShowSuccessOrFailure() {
        var renderer = new ConnectPageRenderer();
        assertThat(renderer.resultPage(true)).contains("Account connected. You can return to the chat.");
        assertThat(renderer.resultPage(false)).contains("Account was not connected. Please try again from the chat.");
    }
}
