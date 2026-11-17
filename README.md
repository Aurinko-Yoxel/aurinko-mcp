# Aurinko MCP Server

A standalone Java MCP server for the Aurinko API. The intended integration lets
users connect an IMAP mailbox and a CalDAV calendar and work with them from MCP
clients such as ChatGPT, Claude, and Gemini.

The current implementation provides a Streamable HTTP endpoint at `/mcp` with
two tools: `ping`, which returns `pong`, and `list_accounts`, which lists the
current user's connected mailboxes and calendars. Both accept an empty object.
The endpoint requires an encrypted JWE access token issued by this server's
`TokenManager`. Each token contains a user ID, an Aurinko `userSession`, a client ID, the resource URL,
and an expiration time. Tokens expire after one hour and are verified without
a database. Encryption uses Nimbus JOSE+JWT with `dir` and `A256GCM`.
OAuth client registration, authorization code exchange with S256 PKCE, and
refresh token grants are implemented. Refresh tokens expire after 30 days.
Users sign in through Google or Microsoft via Aurinko. Their Aurinko `userId`
becomes the user ID in this server's authorization codes and tokens.
Mailbox/calendar connections and other tools are not implemented yet.
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
| `AURINKO_CLIENT_SECRET` | Required | Aurinko application's secret; omitted from configuration `toString` and never logged |
| `AURINKO_LOGIN_SERVICE_TYPES` | `Google,Office365` | Comma-separated login providers shown on the page |
| `AURINKO_LOGIN_SCOPES` | Unset | Optional scopes passed to Aurinko's `authorizeUser` endpoint |

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
2. Add `<PUBLIC_URL>/oauth/aurinko/callback` to the allowed return URLs.
3. Configure Google and Microsoft login, including the provider OAuth
   applications and credentials required by Aurinko.

Use the external `PUBLIC_URL` visible to the browser, with HTTPS for a deployed
server. For local development, make sure the local callback URL is allowed by
the Aurinko application. Provider configuration follows the
[Aurinko user OAuth flow](https://docs.aurinko.io/authentication/oauth-flow/user-oauth-flow).

Every request to `/mcp` requires `Authorization: Bearer <access-token>`.
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
`userSession`, `redirectUri`, `codeChallenge`, and `resource`. All five kinds of value use the
same `TOKEN_KEY`; encrypted type markers (`client`, `code`, `access`, `refresh`, and `login`)
prevent using one kind as another. Nimbus is used only in `JweHelper`, which
also checks expiration using an injected `Clock`.

The callback uses `AurinkoService.createWithAppAuth(...).auth.exchangeCode(code)`
to obtain the real Aurinko user ID, then calls `AuthorizationCompleter` to issue
this server's code. The `userSession` is carried inside the encrypted authorization
code and access/refresh tokens, and preserved when refreshing. Tokens without a
nonblank session are rejected. It is excluded from diagnostic `toString` output
and is never returned as a separate client-visible field. Account access tokens
from the exchange are not retained. Mailboxes and calendars are not connected by
this flow.

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
authentication through `aurinko-java`, including all pages. An empty list returns
`[]`. Aurinko failures return `isError: true` with
`Failed to load accounts from Aurinko`; diagnostic details stay in server logs.

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
