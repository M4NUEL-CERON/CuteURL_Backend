# CuteURL — Backend

A URL shortener with no sign-up. `POST` a long link, get a six-character code that redirects for 30 days. Spring Boot on top of Redis, where an atomic `SET NX` with a TTL does the heavy lifting.

**Live demo:** [m4nuel-ceron.github.io/CuteURL-Frontend](https://m4nuel-ceron.github.io/CuteURL-Frontend/) · Frontend repo: [CuteURL-Frontend](https://github.com/M4NUEL-CERON/CuteURL-Frontend)

> The API runs on Render's free tier and sleeps after 15 minutes idle; the first request can take 30–60 s.

## How a link works

```
POST /shorten {url}  ─►  validate (http/https, has host, ≤ 2048 chars)
                     ─►  code = 6 hex chars
                     ─►  Redis SET url:{code} {url} NX EX 30d   (collision? new code, retry)
                     ◄─  { "shortUrl": "https://<host>/{code}" }

GET /{code}          ─►  Redis GET url:{code}
                     ◄─  302 Location: {url}   or   404 if missing/expired
```

- **Atomic writes.** `SET NX` claims a code in one round trip, so two requests can never overwrite each other's link. A collision just retries with a new code.
- **Expiry is Redis's job.** Every key carries a TTL (`URL_TTL_DAYS`, default 30), so there is no cleanup job.
- **302, not 301.** Browsers cache permanent redirects; a 302 keeps working until the link expires and then correctly turns into a 404.
- **Validation at the edge.** Only `http`/`https` URLs with a host are accepted (no `javascript:` or `mailto:`), capped at 2048 characters so a caller cannot fill the store with huge values.

## Run it locally

One command, with a local Redis (Docker):

```bash
docker compose up --build
curl -X POST localhost:8080/shorten -H 'Content-Type: application/json' -d '{"url":"https://example.com"}'
```

Without Docker, point it at any Redis (Java 25):

```bash
REDIS_HOST=localhost REDIS_PORT=6379 REDIS_PASSWORD= ./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test
```

17 tests, no Redis needed: `UrlServiceTest` mocks `StringRedisTemplate` and checks the validation rules, the TTL, and retry-on-collision. `UrlControllerTest` drives the endpoints through MockMvc (200, 400, 302, 404). GitHub Actions runs them on every push.

## API

| Endpoint | Response |
| --- | --- |
| `POST /shorten` `{"url": "https://..."}` | `200 {"shortUrl": "..."}`, or `400` for an empty or invalid URL |
| `GET /{code}` | `302` to the original URL, or `404` if unknown or expired |
| `GET /actuator/health` | Includes the Redis connection; used by the host's health check |

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `REDIS_HOST`, `REDIS_PORT` | — | Redis connection |
| `REDIS_USERNAME` | `default` | Redis user |
| `REDIS_PASSWORD` | — | Redis password |
| `REDIS_SSL` | `false` | TLS to Redis |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated origins allowed to call the API |
| `APP_BASE_URL` | `RENDER_EXTERNAL_URL`, then `http://localhost:8080` | Host used to build `shortUrl` |
| `URL_TTL_DAYS` | `30` | Link lifetime |
| `PORT` | `8080` | HTTP port |

## Deploy (Render)

`render.yaml` is a Blueprint: in Render choose **New → Blueprint**, select this repo, and fill in the Redis variables (for example, from a free Redis Cloud database). Render injects `RENDER_EXTERNAL_URL`, so short links point at the service automatically.

## Stack

Java 25 · Spring Boot 4.1 · Spring Data Redis · Spring Boot Actuator · JUnit 5 + Mockito · Docker · GitHub Actions · Render
