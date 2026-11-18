# AGENTS.md

## What this project is

An MCP server that lets ChatGPT, Claude and Gemini work with a user's mailboxes and calendars through Aurinko.
The server is its own OAuth 2.1 authorization server for MCP clients and delegates user sign-in to Aurinko.
It then calls the Aurinko API on the user's behalf with the Aurinko user session.

Setup, environment variables and endpoint reference are in `README.md`.

## Build and test

- JDK 17, Maven.
- `mvn package` compiles, runs all tests and builds the runnable jar.
- `docker build -t aurinko-mcp .` builds the container image; update `Dockerfile` and `start.sh` when changing how the application starts.
- All tests must pass before a task is considered done.

## Constraints

- No Spring and no other DI framework. Objects are wired by hand in the `server` package.
- No database. Server state lives in encrypted JWE tokens (`oauth/JweHelper`), each with its own type.
  A token of one type must never be accepted where another type is expected.
- Aurinko is called only through the `aurinko-java` library, never with a hand-written HTTP client.
- Do not add a dependency unless the task asks for it.

## Package layout

Root package: `com.yoxel.aurinko.mcp`

| Package | Responsibility |
|---|---|
| `config` | Reads and validates environment variables (`ServerConfig`, `AurinkoConfig`) |
| `server` | Wiring: creates all objects, the MCP server, HTTP routes and Jetty |
| `auth` | Bearer token check on `/mcp`, `McpPrincipal`, protected resource metadata |
| `oauth` | Authorization server: client registration, authorize, token, PKCE, sign-in through Aurinko |
| `aurinko` | Aurinko API access: accounts, email, calendar, auth, account access check |
| `accounts` | Browser flow for connecting an IMAP mailbox or a CalDAV calendar |
| `tools` | MCP tools, one class per tool, plus argument and result helpers |

`Main` only reads the configuration and starts the server.

## Code style

- A service is an interface plus an implementation named `<Interface>Impl`, or `<Interface><Technology>Impl`
  when the technology matters (`TokenManagerJweImpl`, `UsedCodesMemoryImpl`).
- One class has one responsibility. Servlets and tools parse input and format output; logic lives in services.
- Dependencies come through the constructor. No static mutable state.
- Lombok: `@Value` for data classes, `@Slf4j` for logging.
- No comments and no Javadoc.
- User-facing text (pages, tool descriptions, error messages) is in English.

## Adding an MCP tool

- Implement `tools/McpTool` in a new class and register it in `server/McpToolsFactory`.
- Read arguments with `ToolArguments`; invalid input throws `InvalidArgumentException`.
- Build results with `ToolResultHelper`; log failures with `ToolErrorLogger`.
- Any tool that takes an `accountId` must call `AccountAccessValidator`: `validate` for reading or writing
  account data, `validateOwnership` for actions on the account itself.
- Set `ToolAnnotations` honestly: `readOnlyHint` for tools that only read.
- Limit the size of what a tool returns (page size, body length).

## Security rules

- Never log MCP session identifiers (`Mcp-Session-Id`).
- Never log or return to the client: the Aurinko user session, the client secret, `TOKEN_KEY`, access and
  refresh tokens, authorization codes, state values. Exclude such fields from `toString`.
- Never log message bodies, subjects or event contents.
- Error responses and logs must not include exception messages from Aurinko; log the type and HTTP status.
- Escape everything that goes into an HTML page.
- Pages that carry a state value send `Cache-Control: no-store` and `Referrer-Policy: no-referrer`.
- After any exchange with Aurinko, check that the returned user is the one the state was issued for.

## Tests

- JUnit 5 and AssertJ.
- Every new class with logic gets a test class.
- Tests never call the real Aurinko. Use the fakes in `src/test/.../support`.
- Do not delete or weaken existing tests to make a change pass.

## Working rules

- Do exactly what the task asks. No unrelated refactoring, renaming or formatting changes.
- If you find a problem outside the task, report it instead of fixing it.
- Do not commit.
- Update `README.md` when an endpoint, a tool or an environment variable changes.
