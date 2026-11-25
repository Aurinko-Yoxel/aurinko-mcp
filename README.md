# Aurinko MCP Server

> **Early preview:** This project is at a very early stage of development. Features, configuration, and APIs may change without notice. Expect bugs and incomplete functionality; it is not yet recommended for production use. Feedback and contributions are welcome.

**Bring existing mailboxes and calendars to AI assistants — without replacing your mail infrastructure.**

An open-source, self-hostable **Model Context Protocol (MCP) gateway** powered by the [Aurinko Unified Mailbox API](https://www.aurinko.io/). Connect existing IMAP mailboxes and CalDAV calendars to MCP-compatible assistants using a single HTTP endpoint and user-specific authorization.

**Built for email hosting providers, ISPs, SaaS platforms, and developers.** Offer an AI-accessible mailbox experience to your users while keeping your existing mail servers and account infrastructure.

> **Aurinko account required.** This gateway is open source, but it is **not a standalone IMAP or CalDAV server**. To run it, you must create an **Aurinko account**, configure an **Aurinko application** with End User Sessions and login providers, and provide that application's client ID and secret. The gateway uses Aurinko for mailbox connections, user sessions, and unified email/calendar API calls. Aurinko service usage may be subject to its pricing and terms.

[Get started](#quick-start) · [For hosting providers](#for-isps-and-email-hosting-providers) · [Available tools](#what-users-can-do-today) · [Architecture](#how-it-works) · [Technical reference](#technical-reference)

## Why this exists

Your customers already have mailboxes. They shouldn't need to migrate to a new email platform to use AI assistants with their email and calendars.

With Aurinko MCP Server, you can:

- **Keep existing infrastructure.** Connect existing IMAP mailboxes and CalDAV calendars through Aurinko.
- **Host your own MCP endpoint.** Deploy a lightweight Java service on your infrastructure or preferred hosting platform.
- **Support multiple users.** Each person signs in and accesses their own Aurinko-connected accounts, not a shared administrator mailbox.
- **Offer account self-service.** Users can connect and disconnect supported mailboxes and calendars through browser-assisted flows.
- **Customize what assistants see.** Override tool descriptions or disable selected tools through YAML without recompiling.

### What users can do today

| Capability | MCP tool | Status |
| --- | --- | --- |
| List their connected mailboxes and calendars | `list_accounts` | Available |
| Search email | `search_messages` | Available |
| Read an email message | `get_message` | Available |
| List calendar events | `list_events` | Available |
| Create a calendar event (without attendees) | `create_event` | Available |
| Connect a supported mailbox or calendar | `connect_account` | Available |
| Disconnect an additional account | `disconnect_account` | Available |
| Test connectivity | `ping` | Available |
| Search or edit CardDAV contacts | — | **Not implemented in this gateway yet** |

The current server supports Google, Microsoft 365, iCloud, IMAP, and CalDAV account connection types by default. **CardDAV support is a potential extension, not a feature of the current MCP toolset.** Availability of individual tools depends on the connected account's capabilities and granted scopes.

## Quick start

### 1. Create and configure an Aurinko application

1. Create an account with [Aurinko](https://www.aurinko.io/) and create an application in the Aurinko developer console.
2. Enable **End user sessions** (`EndUserSessions`) for the application.
3. Configure **Google and/or Microsoft login**, including the corresponding provider OAuth application credentials required by Aurinko. These are the currently implemented user sign-in options; connecting an IMAP account is a separate step after sign-in.
4. Copy the application's **client ID** and **client secret**.
5. Add these allowed return URLs to the Aurinko application (replace the domain with your deployment URL):

   - `https://mcp.example.com/oauth/aurinko/callback`
   - `https://mcp.example.com/accounts/callback`

For more detail, see the [Aurinko user OAuth flow](https://docs.aurinko.io/authentication/oauth-flow/user-oauth-flow).

### 2. Build and run locally

Requires **Java 17+**, **Maven 3.9+**, and build-time access to Maven Central and JitPack.

```sh
mvn clean package
export TOKEN_KEY="$(openssl rand -base64 32)"
export PUBLIC_URL=http://127.0.0.1:8080
export AURINKO_CLIENT_ID='<your Aurinko application client ID>'
export AURINKO_CLIENT_SECRET='<your Aurinko application secret>'
java -jar target/aurinko-mcp-0.1.0-SNAPSHOT.jar
```

The MCP endpoint is `http://127.0.0.1:8080/mcp`. **For this local flow, allow the matching `http://127.0.0.1:8080` callback URLs in your Aurinko application.** Store `TOKEN_KEY` securely and reuse it across restarts; changing it invalidates previously issued tokens and registration state.

### 3. Connect an MCP client and sign in

Configure an MCP client that supports **remote Streamable HTTP and OAuth** to use your server's `/mcp` URL. The server publishes OAuth discovery metadata, supports public client registration and S256 PKCE, and redirects users to a Google or Microsoft sign-in through Aurinko. After signing in, users can call `connect_account` to add an IMAP mailbox or CalDAV calendar via a browser link.

Client setup varies by assistant and plan. **ChatGPT, Claude, and Gemini compatibility should be validated individually**; this README does not claim identical remote MCP support across all three.

### 4. Deploy with Docker

```sh
docker build -t aurinko-mcp .

# Set and securely store these values before running:
export TOKEN_KEY='<stored base64-encoded 32-byte key>'
export PUBLIC_URL=https://mcp.example.com
export AURINKO_CLIENT_ID='<your Aurinko application client ID>'
export AURINKO_CLIENT_SECRET='<your Aurinko application secret>'

docker run --rm --name aurinko-mcp -p 3000:3000 \
  -e TOKEN_KEY \
  -e PUBLIC_URL \
  -e AURINKO_CLIENT_ID \
  -e AURINKO_CLIENT_SECRET \
  aurinko-mcp
```

The container listens on port **3000**. Place it behind an HTTPS reverse proxy, point `PUBLIC_URL` at its externally reachable base URL (without `/mcp`), and configure both Aurinko callback URLs for that domain. The unauthenticated health endpoint is `GET /health`.

## For ISPs and email hosting providers

**Offer your subscribers an AI mailbox connection without building an MCP/OAuth stack or replacing your IMAP and CalDAV services.**

A typical evaluation looks like this:

1. Your team creates one Aurinko application for the integration and configures user login.
2. You deploy this open-source MCP gateway at a hostname such as `mcp.yourprovider.com`.
3. Each subscriber authenticates individually and connects their own supported mailbox/calendar through Aurinko.
4. The subscriber connects an MCP-compatible assistant to your gateway URL and uses the available tools.

**What you operate:** the MCP proxy, its public HTTPS endpoint, configuration, and service availability. **What Aurinko provides:** application configuration, account connections, end-user sessions, and unified mailbox/calendar APIs. Credentials and mailbox data are processed through Aurinko according to its service architecture; self-hosting the proxy does **not** mean all data stays exclusively on your servers.

**Evaluating a multi-subscriber rollout?** Start with the open-source deployment, then [contact Aurinko](https://www.aurinko.io/) to discuss provisioning, operational requirements, and commercial terms. Bulk provisioning, white-label UI, and ISP-specific billing are **not claimed as built-in features of this repository**.

## How it works

```text
   MCP-compatible AI assistant
              |
       OAuth + MCP over HTTPS
              |
   +---------------------------+
   | Aurinko MCP Server        |  <- Open source; you deploy it
   | /mcp + OAuth endpoints    |
   +---------------------------+
              |
     User-specific Aurinko session
              |
   +---------------------------+
   | Aurinko Unified API       |  <- Aurinko account + app required
   | Users / sessions /        |
   | connected accounts        |
   +---------------------------+
              |
      Provider connections
              |
     IMAP / CalDAV / Google /
       Microsoft 365 / iCloud
```

The gateway authenticates MCP clients and associates each request with the verified user's Aurinko session. Mail and calendar tools validate account ownership before making API calls. An ISP's Aurinko application credentials configure the integration; they are **not** credentials that subscribers should enter into their AI assistants.

## Security and deployment considerations

- The server uses OAuth authorization code + **S256 PKCE** and encrypted JWE tokens; access tokens expire after **one hour**, refresh tokens after **30 days**.
- Tokens are bound to a user, client, resource, and Aurinko session. MCP sessions also enforce user/client ownership.
- `TOKEN_KEY` is a required 32-byte AES key. Treat it and the Aurinko application secret as production secrets.
- **There is currently no token revocation**, and previously issued refresh tokens remain valid until expiry even after refresh.
- **Multi-instance deployment needs additional work:** used authorization codes are tracked in process memory, not in a shared store.
- **CORS is not supported.** Public deployments require HTTPS except for documented localhost development cases.
- Consider your provider's data protection, consent, rate limits, access review, and operational policies before offering this to subscribers.

## Technical reference

The sections below retain the original setup, configuration, OAuth, tool usage, and testing details for implementers.

### Requirements

- Java 17 or higher
- Maven 3.9 or higher
- Network access to Maven Central and JitPack for the initial build

### Build and run

```sh
mvn clean package
export TOKEN_KEY="$(openssl rand -base64 32)"
export PUBLIC_URL=http://127.0.0.1:8080
export AURINKO_CLIENT_ID='<Aurinko application client ID>'
export AURINKO_CLIENT_SECRET='<Aurinko application secret>'
java -jar target/aurinko-mcp-0.1.0-SNAPSHOT.jar
```

The JAR includes all runtime dependencies and starts embedded Jetty.
The default endpoint is `http://127.0.0.1:8080/mcp`.
Stop the server with Ctrl+C.

Configuration is read from environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `HOST` | `127.0.0.1` | HTTP bind address |
| `PORT` | `8080` | HTTP port, from 1 to 65535 |
| `TOOLS_CONFIG_FILE` | Unset | Optional path to a YAML file overriding tool descriptions, parameter descriptions, service type descriptions and enabled tools |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75` | Optional JVM options; applies only in the container |
| `PUBLIC_URL` | `http://127.0.0.1:<PORT>` | External base URL used for token resource validation and OAuth metadata; HTTPS required except for localhost, 127.0.0.1 and [::1] |
| `TOKEN_KEY` | Required | Base64-encoded 32-byte AES encryption key; startup fails if absent or invalid |
| `AURINKO_URL` | `https://api.aurinko.io` | Aurinko API base URL; HTTPS required except for localhost, 127.0.0.1 and [::1] |
| `AURINKO_CLIENT_ID` | Required | Aurinko application's client ID |
| `AURINKO_CLIENT_SECRET` | Required | Aurinko application's secret in base64url format; omitted from configuration `toString` and never logged |
| `AURINKO_ACCOUNT_SERVICE_TYPES` | `Google,Office365,iCloud,IMAP,CalDav` | Comma-separated account types visible to tools and available to connect_account; matching account types is case-insensitive |
| `AURINKO_LOGIN_SERVICE_TYPES` | `Google,Office365` | Comma-separated login providers shown on the page |
| `AURINKO_LOGIN_SCOPES` | `Mail.Read Calendar.ReadWrite` | Scopes for the primary account used to sign in; without mail and calendar access, tools will return errors on that account |
| `AURINKO_IMAP_SCOPES` | `Mail.Read` | Scopes requested when connecting an IMAP mailbox |
| `AURINKO_CALDAV_SCOPES` | `Calendar.ReadWrite` | Scopes requested when connecting a CalDAV calendar |

`PUBLIC_URL` may end in `/`; the trailing slash is removed before constructing
the MCP resource URL. Configure the actual external URL when deploying behind
a proxy. The MCP resource URL is always `ServerConfig.getMcpUrl()`.
Keep `TOKEN_KEY` secret and reuse it across restarts and instances. Generating a
new key invalidates previously issued tokens, client IDs, and authorization
codes. The generation command above is
for initial setup; store the resulting key securely for subsequent runs.

```sh
PORT=9090 PUBLIC_URL=http://127.0.0.1:9090 \
  java -jar target/aurinko-mcp-0.1.0-SNAPSHOT.jar
```

Startup fails if the Aurinko client ID or secret is missing. Store the secret
securely. A single application-authenticated `AurinkoService` is shared by
login requests and closed when the server stops.

### Run in Docker

Build the image from the repository root:

```sh
docker build -t aurinko-mcp .
```

Set the required runtime environment variables, then start the container:

```sh
export TOKEN_KEY='<stored base64-encoded 32-byte key>'
export PUBLIC_URL=https://mcp.example.com
export AURINKO_CLIENT_ID='<Aurinko application client ID>'
export AURINKO_CLIENT_SECRET='<Aurinko application secret>'
docker run --rm --name aurinko-mcp -p 3000:3000 \
  -e TOKEN_KEY \
  -e PUBLIC_URL \
  -e AURINKO_CLIENT_ID \
  -e AURINKO_CLIENT_SECRET \
  aurinko-mcp
```

Generate `TOKEN_KEY` once with `openssl rand -base64 32`, store it as a secret,
and supply it in the environment for every run. Changing it invalidates all
previously issued tokens. In Coolify, set these variables in the application's
runtime environment. `PUBLIC_URL` is the external HTTPS base URL without `/mcp`
at the end. The container listens on `0.0.0.0:3000` and runs as an unprivileged
user. Image builds skip tests; run `mvn package` separately to run them.

`JAVA_OPTS` is optional and defaults to `-XX:MaxRAMPercentage=75`. Override it
with `-e JAVA_OPTS='...'` when starting the container if needed.

In the Aurinko application, enable **End user sessions** (`EndUserSessions`) and
allow both return URLs: `<PUBLIC_URL>/oauth/aurinko/callback` and
`<PUBLIC_URL>/accounts/callback`.

Configure the platform's health check to use `GET /health` on port 3000. It needs
no token and returns HTTP 200 with `{"status":"ok"}` without contacting Aurinko.

### Override tool descriptions

Default tool and parameter descriptions and service type hints are bundled in
`src/main/resources/tools.yaml`. Set `TOOLS_CONFIG_FILE` to a custom YAML file to
override selected fields without rebuilding the server. The file is loaded once
at startup; restart the server after changing it. Fields and parameters omitted
from the file retain their defaults. For example:

```yaml
tools:
  search_messages:
    description: >-
      Search the selected mailbox. Prefer a narrow date range and sender filter.
  create_event:
    enabled: false
serviceTypes:
  iCloud: >-
    Apple iCloud Mail and Calendar; ask the user to prepare an Apple app-specific password.
```

Use `parameters` under a tool to override individual parameter descriptions.
Unknown tools or parameters, empty descriptions, invalid YAML and missing or
unreadable files cause startup to fail. Service type keys may include custom
Aurinko providers. Disabled tools are absent from `tools/list`; argument types,
required fields, allowed values, annotations and tool behavior stay defined in Java.

For Docker or Coolify, mount the file into the container and set
`TOOLS_CONFIG_FILE` to its container path. For example, add these options to
`docker run` along with the required runtime variables:

```sh
--mount type=bind,src="$PWD/custom-tools.yaml",dst=/app/custom-tools.yaml,readonly \
-e TOOLS_CONFIG_FILE=/app/custom-tools.yaml
```

### Aurinko application setup

In the Aurinko application settings:

1. Enable the `EndUserSessions` capability.
2. Add both `<PUBLIC_URL>/oauth/aurinko/callback` and
   `<PUBLIC_URL>/accounts/callback` to the allowed return URLs.
3. Configure Google and Microsoft login, including the provider OAuth
   applications and credentials required by Aurinko.

Use the external `PUBLIC_URL` visible to the browser, with HTTPS for a deployed
server. For local development, make sure the local callback URL is allowed by
the Aurinko application. Provider configuration follows the
[Aurinko user OAuth flow](https://docs.aurinko.io/authentication/oauth-flow/user-oauth-flow).

Every request to `/mcp` requires `Authorization: Bearer <access-token>`.

Each MCP session is bound to the user and client that initialized it. Requests
with a different user or client, or with an unknown `Mcp-Session-Id`, receive
HTTP 404 with an empty body. A matching owner keeps the session ownership entry
active for 24 hours after its most recent access.

Missing or invalid tokens receive HTTP 401 and this header:

```text
WWW-Authenticate: Bearer resource_metadata="<PUBLIC_URL>/.well-known/oauth-protected-resource"
```

Both `/.well-known/oauth-protected-resource` and
`/.well-known/oauth-protected-resource/mcp` are public and return the same JSON
document with `resource` set to the MCP URL and `authorization_servers` set to
`[PUBLIC_URL]`.

### OAuth registration and authorization

The following routes are public:

| Route | Purpose |
| --- | --- |
| `GET /health` | Public health check; returns HTTP 200 with `{"status":"ok"}` without contacting Aurinko |
| `GET /.well-known/oauth-authorization-server` | Issuer, endpoint URLs, response/grant types, S256 PKCE support, and public client authentication method `none` |
| `POST /oauth/register` | Register a public MCP client using JSON `redirect_uris`; returns HTTP 201 with `client_id`, `redirect_uris`, and `token_endpoint_auth_method: "none"` |
| `GET /oauth/authorize` | Validate an authorization request and show the requesting application's host with a login button for each configured provider |
| `GET /oauth/aurinko/callback` | Verify encrypted login state and browser nonce, exchange the Aurinko code, and return this server's code to the MCP client |
| `POST /oauth/token` | Exchange an authorization code with PKCE or a refresh token for a new access/refresh pair |

`/oauth/token` accepts `application/x-www-form-urlencoded` requests. Public
clients authenticate with their registered `client_id`; there is no client secret.

The login page is always shown, including when only one provider is configured.
The sign-in URL always requests that Microsoft and Google show an account chooser (`prompt=select_account`).
Google uses `serviceType=Google`; Microsoft uses `serviceType=Office365`.
Each button points to Aurinko's `/v1/auth/authorizeUser` with `accountRole=primary`,
`responseType=code`, the configured application ID, callback URL, and login state.
`scopes` is always included and defaults to `Mail.Read Calendar.ReadWrite`.
Set `AURINKO_LOGIN_SCOPES` to override the primary account's scopes.

After scopes change, users who signed in earlier must sign in again so the provider issues a token with the new permissions.

Login state is an encrypted JWE of type `login`, valid for 10 minutes. It contains
the original authorization request and a random nonce. The nonce is also stored
in an HttpOnly cookie with `SameSite=Lax`, a 10-minute lifetime, and `Secure` when
`PUBLIC_URL` uses HTTPS. The callback validates both state and cookie before
calling Aurinko and deletes the cookie in every response.

Invalid state or nonce produces HTTP 400 without redirecting. Provider failure
or a missing provider code redirects to the validated client callback with
`error=access_denied`; an Aurinko exchange failure uses `error=server_error`.
Both preserve the MCP client's original `state`.

For an interactive login, open the authorization URL in a browser and complete
provider login in that same browser so the nonce cookie returns with the
callback. The curl example below displays the login page but does not sign in.

```sh
curl http://127.0.0.1:8080/oauth/register \
  -H 'Content-Type: application/json' \
  -d '{"redirect_uris":["http://localhost:3000/cb"]}'

CLIENT_ID='<client_id from registration response>'
CODE_VERIFIER="$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n')"
CODE_CHALLENGE="$(printf '%s' "$CODE_VERIFIER" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')"

curl -i -G http://127.0.0.1:8080/oauth/authorize \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode 'redirect_uri=http://localhost:3000/cb' \
  --data-urlencode 'response_type=code' \
  --data-urlencode 'state=client-state' \
  --data-urlencode "code_challenge=$CODE_CHALLENGE" \
  --data-urlencode 'code_challenge_method=S256' \
  --data-urlencode "resource=$PUBLIC_URL/mcp"
```

Redirect URIs must use HTTPS, or HTTP for `localhost` and `127.0.0.1` only,
and must not contain a fragment. Authorization requires an exact match with a
registered URI. Invalid client IDs and unregistered redirect URIs receive HTTP
400 without a redirect. Other invalid authorization parameters redirect to the
validated callback with `error=invalid_request` and the original `state`.
Invalid registration JSON receives `invalid_client_metadata`; disallowed
redirect URIs receive `invalid_redirect_uri`, both as OAuth JSON errors with
HTTP 400.

After the browser completes Aurinko login, copy this server's authorization code
from the redirect to the MCP client's callback. Exchange it within 60 seconds
using the same client ID, redirect URI, and PKCE verifier:

```sh
CODE='<code from the authorize redirect>'

curl http://127.0.0.1:8080/oauth/token \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "code=$CODE" \
  --data-urlencode 'redirect_uri=http://localhost:3000/cb' \
  --data-urlencode "code_verifier=$CODE_VERIFIER" \
  --data-urlencode "resource=$PUBLIC_URL/mcp"
```

The optional `resource` must match the one in the authorization code. PKCE
verifiers must contain 43–128 characters from `A-Z`, `a-z`, `0-9`, `-`, `.`, `_`,
and `~`. Successful exchanges consume the code; failed validation does not.

The HTTP 200 JSON response contains `access_token`, `token_type: "Bearer"`,
`expires_in: 3600`, and `refresh_token`. Use the access token for `/mcp`.
To obtain a new pair later:

```sh
REFRESH_TOKEN='<refresh_token from the token response>'

curl http://127.0.0.1:8080/oauth/token \
  --data-urlencode 'grant_type=refresh_token' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "refresh_token=$REFRESH_TOKEN"
```

Token errors return HTTP 400 JSON with `error` and `error_description`. Error
codes are `invalid_client`, `unsupported_grant_type`, `invalid_request`, and
`invalid_grant`. Both successful responses and errors include
`Cache-Control: no-store`.

Two limitations apply:

- Refreshing does not revoke the old refresh token. It remains valid until the
  end of its original 30-day lifetime. There is no database or revocation store
  to invalidate it.
- Used authorization codes are tracked for two minutes in one process's memory.
  Multiple server instances require a shared used-code store. Restarting the
  process also clears this memory; a still-valid code can then be redeemed again.

Client IDs are encrypted JWE strings without expiration. Authorization codes
are encrypted JWE strings valid for 60 seconds and contain `userId`, `clientId`,
`userSession`, `redirectUri`, `codeChallenge`, and `resource`. All six kinds of value use the
same `TOKEN_KEY`; encrypted type markers (`client`, `code`, `access`, `refresh`, `login`, and `connect`)
prevent using one kind as another. Nimbus is used only in `JweHelper`, which
also checks expiration using an injected `Clock`.

The callback uses `AurinkoService.createWithAppAuth(...).auth.exchangeCode(code)`
to obtain the real Aurinko user ID, then calls `AuthorizationCompleter` to issue
this server's code. The `userSession` is carried inside the encrypted authorization
code and access/refresh tokens, and preserved when refreshing. Tokens without a
nonblank session are rejected. It is excluded from diagnostic `toString` output
and is never returned as a separate client-visible field. Account access tokens
from the exchange are not retained. Additional mailboxes and calendars are
connected through the separate `connect_account` flow described below.

### Try ping

MCP clients first initialize a session, then list or call tools. The following
commands use the access token returned by `/oauth/token` for the configured
resource. `TOKEN_KEY` itself is not an access token.

```sh
ACCESS_TOKEN='<access_token from the token response>'
```

Initialize with:

```sh
curl -i http://127.0.0.1:8080/mcp \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"manual-check","version":"1.0"}}}'
```

Copy the response's `Mcp-Session-Id` header into `SESSION_ID`:

```sh
SESSION_ID='<Mcp-Session-Id from the initialize response>'

curl http://127.0.0.1:8080/mcp \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -H 'MCP-Protocol-Version: 2025-11-25' \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

curl http://127.0.0.1:8080/mcp \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -H 'MCP-Protocol-Version: 2025-11-25' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"ping","arguments":{}}}'
```

The tool result contains `{"type":"text","text":"pong"}` and `isError: false`.

### List connected accounts

Call `list_accounts` with empty arguments using the same authenticated MCP
session:

```sh
curl http://127.0.0.1:8080/mcp \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -H 'MCP-Protocol-Version: 2025-11-25' \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_accounts","arguments":{}}}'
```

The result is text containing a JSON array, for example:

```json
[{"id":42,"serviceType":"IMAP","email":"user@example.com","active":true}]
```

The user ID comes from the verified token through MCP transport context and
cannot be supplied as a tool argument. Accounts are fetched with application
authentication through `aurinko-java`, up to 200 accounts. Loading stops at the
limit or an empty page. An empty list returns
`[]`. Aurinko failures return `isError: true` with
`Failed to load accounts from Aurinko`; diagnostic details stay in server logs.

### Search and read email

Choose an active `accountId` from `list_accounts`. Both mail tools verify that
the account belongs to the authenticated user and is active before querying
messages. An unavailable or inaccessible account returns `isError: true` with
`Account not found`.

| Tool | Arguments | Result |
| --- | --- | --- |
| `search_messages` | Required integer `accountId`; optional string `query`; optional integer `limit` from 1 to 50, default 20 | Text containing a JSON array of message summaries |
| `get_message` | Required integer `accountId` and string `messageId` | Text containing a JSON message with body and CC recipients |

Examples of MCP tool call payloads:

```json
{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"search_messages","arguments":{"accountId":42,"query":"from:someone@example.com after:01/01/2026","limit":20}}}
```

```json
{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"get_message","arguments":{"accountId":42,"messageId":"message-1"}}}
```

Search reads only the first Aurinko page and returns at most `limit` records;
it does not follow `nextPageToken`. An omitted or blank query sends no `q`
parameter. Summaries contain `id`, `threadId`, `subject`, `from`, `to`,
`receivedAt`, and `hasAttachments`. Addresses are formatted as `Name <address>`
or a plain address; dates use ISO-8601 strings.

Reading a message also returns `cc`, `body`, and `bodyTruncated`. Bodies longer
than 50,000 characters are cut to that length with `bodyTruncated: true`.

Each mail request creates and closes its own Aurinko client. It sends
`X-Aurinko-Session` from the verified token and `X-Aurinko-Account-Id` for the
validated account. Application credentials are not sent with mail requests.
Aurinko failures return `isError: true` and `Failed to load messages from Aurinko`.
Logs record the failure type and stack location without exception messages,
user sessions, subjects, or message bodies.

### List and create calendar events

Both calendar tools use the verified user's session and validate ownership and
activity of `accountId` before contacting the calendar API. Omit `calendarId`
to use the primary calendar.

| Tool | Arguments | Result |
| --- | --- | --- |
| `list_events` | Required integer `accountId`, strings `timeMin` and `timeMax`; optional string `calendarId` and integer `limit` (1–50, default 20) | Text containing a JSON array of events |
| `create_event` | Required integer `accountId`, nonblank string `subject`, strings `start` and `end`; optional strings `calendarId`, `description`, and `location` | Text containing JSON `{"id":"..."}` |

Times must be ISO-8601 date-times with an explicit offset, such as
`2026-11-17T09:00:00+02:00` or `2026-11-17T07:00:00Z`. For listing, `timeMax`
must be later than `timeMin`, and the range cannot exceed 62 days. For creation,
`end` must be later than `start`.

```json
{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"list_events","arguments":{"accountId":42,"timeMin":"2026-11-17T09:00:00+02:00","timeMax":"2026-11-18T09:00:00+02:00","limit":20}}}
```

```json
{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"create_event","arguments":{"accountId":42,"subject":"Project review","start":"2026-11-17T09:00:00+02:00","end":"2026-11-17T10:00:00+02:00","description":"Review next steps","location":"Office"}}}
```

Event results contain `id`, `calendarId`, `subject`, `description`, `location`,
`start`, `end`, `allDay`, `organizer`, and `htmlLink`. Timed events return ISO-8601
date-times; all-day events return dates without times. Listing stops when the
requested limit is reached or an empty page is received. No further page is
requested once the limit is reached.

Creation sends no attendees and does not send invitations to other people.
The five read tools advertise `readOnlyHint: true`. `create_event` advertises
`readOnlyHint: false` and `destructiveHint: false`.

Invalid arguments return a tool error explaining the parameter or time range.
An inaccessible account returns `Account not found`; Aurinko failures return
`Failed to load events from Aurinko` or `Failed to create event in Aurinko`.
All tools log HTTP status codes when available, exception types, and stack
locations. Request URLs, response bodies, sessions, and event subjects,
descriptions, and locations are excluded from these logs.

### Connect an IMAP mailbox or CalDAV calendar

Call `connect_account` with required `serviceType` chosen from
`AURINKO_ACCOUNT_SERVICE_TYPES` (by default Google, Office365, iCloud, IMAP and CalDav).
Choose the provider of the user's email address: Outlook.com, Hotmail and Live use
Office365; @icloud.com, @me.com and @mac.com use iCloud. IMAP and CalDav are fallbacks
when no listed provider matches.

For example:

```json
{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"connect_account","arguments":{"serviceType":"IMAP"}}}
```

The result is text containing JSON with `url` and `expiresInSeconds: 600`.
The URL starts with `<PUBLIC_URL>/accounts/connect?state=...`. The tool gets
the user ID from the verified MCP principal and advertises `readOnlyHint: false`
and `destructiveHint: false`.

The user opens this link in a browser, sees the already connected email
addresses on our confirmation page, and clicks **Continue**. Mailbox or calendar
credentials are entered on the Aurinko page reached by that link.

`AURINKO_ACCOUNT_SERVICE_TYPES` filters accounts everywhere: account listing,
mail/calendar tools, disconnection, and connection pages and callbacks. The primary
sign-in account is hidden if its type is not listed; sign-in then only identifies
the user. Unknown service types, including CRM types, can be explicitly enabled.
When connecting, IMAP requests `AURINKO_IMAP_SCOPES`, CalDav requests
`AURINKO_CALDAV_SCOPES`, and all other allowed types request `AURINKO_LOGIN_SCOPES`.

The connection flow uses:

- `GET /accounts/connect?state=...` to show the English confirmation page and set
  `aurinko_connect_nonce`, an HttpOnly cookie with `SameSite=Lax`, path
  `/accounts/callback`, and a 10-minute lifetime. It is Secure for HTTPS.
- Aurinko `/v1/auth/authorizeUser` with `accountRole=secondary`, the selected
  service type, requested scopes, user ID, timestamp, and user signature.
- `GET /accounts/callback` to validate the state and nonce, exchange the code,
  verify that the connected account appeared in the user's account list, and show the result page.
  The nonce cookie is deleted by the callback. These browser routes do not
  require a Bearer token.

Connection state is a JWE of type `connect` valid for 10 minutes, with a random
32-byte base64url nonce. Each connection link can be used only once: after a callback
with valid state and cookie, it is consumed even if the connection fails. Reusing it
returns HTTP 400 with `Connection link already used`.
The user signature is hex-encoded HMAC-SHA256 of
`userId + timestampSeconds`, using the application's base64url-decoded client
secret as the key. The timestamp comes from the server clock.

Confirmation pages use `Cache-Control: no-store` and `Referrer-Policy: no-referrer`.
All displayed email addresses and service types are HTML-escaped. Invalid state
or nonce receives HTTP 400. Provider cancellation, an exchange or account-list error,
or an account absent from the user's account list produces the failure page without
exposing user data in error logs. On success, the page says **Account connected. You can return
to the chat.** The user can then call `list_accounts` again.

### Disconnect an account

Choose an account ID from `list_accounts` and call `disconnect_account`:

```json
{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"disconnect_account","arguments":{"accountId":42}}}
```

The result is text containing JSON:

```json
{"accountId":42,"disconnected":true}
```

The tool verifies that the account belongs to the current user before deleting
it through Aurinko. Both active and inactive additional accounts can be removed.
Disconnection is irreversible: use `connect_account` to connect the account again.
The account used to sign in cannot be removed. If Aurinko returns HTTP 404, the
result is an error: `This account cannot be disconnected. The account used to sign in cannot be removed.`
Other Aurinko failures return `Unable to disconnect the account.`
The tool advertises `readOnlyHint: false`, `destructiveHint: true`, and
`idempotentHint: false`.

### Project layout

All application packages are under `com.yoxel.aurinko.mcp`:

| Package | Responsibility |
| --- | --- |
| `config` | Settings from environment variables |
| `oauth` | Typed JWE values, client registration, grants, PKCE, login page/state/callback, used codes, and metadata |
| `auth` | Principal, verifier contract, Bearer filter, and protected resource metadata |
| `aurinko` | Provider authorize URL builder and application-authenticated code exchange through `aurinko-java` |
| `tools` | MCP tool definitions and handlers |

`Main` starts Jetty and registers the MCP servlet and tools.

The project uses the official [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
and [aurinko-java](https://github.com/Aurinko-Yoxel/aurinko-java), pinned to release
`1.8.18` through JitPack. It uses no Spring dependencies.

### Tests

```sh
mvn test
```

Unit tests cover typed JWE encryption, expiration with fixed clocks, access
and refresh tokens, client registration, redirect URI rules, authorization code
contents and lifetime, PKCE, concurrent used-code tracking, the Bearer filter,
and environment configuration. Login tests cover HTML escaping, browser nonce
validation, and typed login state expiration. Aurinko exchanges use a local
`com.sun.net.httpserver.HttpServer` substitute with no real provider calls.
HTTP integration
tests use temporary local ports to verify authenticated `ping`, protected
resource metadata, authorization server metadata, and registration/authorization
flows through the login page and Aurinko callback, code exchange, refresh, and authenticated `ping`. Redirects are
inspected through `Location` and are never followed.
````
