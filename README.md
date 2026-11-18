# Aurinko MCP Server

A standalone Java MCP server for the Aurinko API. The intended integration lets
users connect an IMAP mailbox and a CalDAV calendar and work with them from MCP
clients such as ChatGPT, Claude, and Gemini.

The current implementation provides a Streamable HTTP endpoint at `/mcp` with
seven tools: `ping`, `list_accounts`, `search_messages`, `get_message`, `list_events`,
`create_event`, and `connect_account`.
`ping` returns `pong`; `list_accounts` lists the current user's connected
mailboxes and calendars. Both accept an empty object. The mail tools search and
read messages in a selected account. The calendar tools list events over a time
range and create events without attendees.
The endpoint requires an encrypted JWE access token issued by this server's
`TokenManager`. Each token contains a user ID, an Aurinko `userSession`, a client ID, the resource URL,
and an expiration time. Tokens expire after one hour and are verified without
a database. Encryption uses Nimbus JOSE+JWT with `dir` and `A256GCM`.
OAuth client registration, authorization code exchange with S256 PKCE, and
refresh token grants are implemented. Refresh tokens expire after 30 days.
Users sign in through Google or Microsoft via Aurinko. Their Aurinko `userId`
becomes the user ID in this server's authorization codes and tokens.
IMAP mailboxes and CalDAV calendars can be connected through a browser link
returned by `connect_account`.
There is no token revocation or CORS support.

## Requirements

- Java 17 or higher
- Maven 3.9 or higher
- Network access to Maven Central and JitPack for the initial build

## Build and run

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
| `PUBLIC_URL` | `http://127.0.0.1:<PORT>` | External base URL used for token resource validation and OAuth metadata |
| `TOKEN_KEY` | Required | Base64-encoded 32-byte AES encryption key; startup fails if absent or invalid |
| `AURINKO_URL` | `https://api.aurinko.io` | Aurinko API base URL |
| `AURINKO_CLIENT_ID` | Required | Aurinko application's client ID |
| `AURINKO_CLIENT_SECRET` | Required | Aurinko application's secret in base64url format; omitted from configuration `toString` and never logged |
| `AURINKO_LOGIN_SERVICE_TYPES` | `Google,Office365` | Comma-separated login providers shown on the page |
| `AURINKO_LOGIN_SCOPES` | Unset | Optional scopes passed to Aurinko's `authorizeUser` endpoint |
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

## Aurinko application setup

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

## OAuth registration and authorization

The following routes are public:

| Route | Purpose |
| --- | --- |
| `GET /.well-known/oauth-authorization-server` | Issuer, endpoint URLs, response/grant types, S256 PKCE support, and public client authentication method `none` |
| `POST /oauth/register` | Register a public MCP client using JSON `redirect_uris`; returns HTTP 201 with `client_id`, `redirect_uris`, and `token_endpoint_auth_method: "none"` |
| `GET /oauth/authorize` | Validate an authorization request and show the requesting application's host with a login button for each configured provider |
| `GET /oauth/aurinko/callback` | Verify encrypted login state and browser nonce, exchange the Aurinko code, and return this server's code to the MCP client |
| `POST /oauth/token` | Exchange an authorization code with PKCE or a refresh token for a new access/refresh pair |

`/oauth/token` accepts `application/x-www-form-urlencoded` requests. Public
clients authenticate with their registered `client_id`; there is no client secret.

The login page is always shown, including when only one provider is configured.
Google uses `serviceType=Google`; Microsoft uses `serviceType=Office365`.
Each button points to Aurinko's `/v1/auth/authorizeUser` with `accountRole=primary`,
`responseType=code`, the configured application ID, callback URL, and login state.
`scopes` is included only when configured.

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

## Try ping

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

## List connected accounts

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

## Search and read email

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

## List and create calendar events

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

## Connect an IMAP mailbox or CalDAV calendar

Call `connect_account` with required `serviceType` set to `IMAP` or `CalDav`:

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

The connection flow uses:

- `GET /accounts/connect?state=...` to show the English confirmation page and set
  `aurinko_connect_nonce`, an HttpOnly cookie with `SameSite=Lax`, path
  `/accounts/callback`, and a 10-minute lifetime. It is Secure for HTTPS.
- Aurinko `/v1/auth/authorizeUser` with `accountRole=secondary`, the selected
  service type, requested scopes, user ID, timestamp, and user signature.
- `GET /accounts/callback` to validate the state and nonce, exchange the code,
  verify that Aurinko returned the expected user ID, and show the result page.
  The nonce cookie is deleted by the callback. These browser routes do not
  require a Bearer token.

Connection state is a JWE of type `connect` valid for 10 minutes, with a random
32-byte base64url nonce. The user signature is hex-encoded HMAC-SHA256 of
`userId + timestampSeconds`, using the application's base64url-decoded client
secret as the key. The timestamp comes from the server clock.

Confirmation pages use `Cache-Control: no-store` and `Referrer-Policy: no-referrer`.
All displayed email addresses and service types are HTML-escaped. Invalid state
or nonce receives HTTP 400. Provider cancellation, an exchange error, or a
different user ID from Aurinko produces the failure page without exposing user
data in error logs. On success, the page says **Account connected. You can return
to the chat.** The user can then call `list_accounts` again.

## Project layout

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

## Tests

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
