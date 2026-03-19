# MySpaCoverSkuRecommendation Backend

Production-ready Spring Boot backend for the existing spa cover recommendation frontend. The API accepts a CSV upload, generates fallback SKU payloads, calls the external inventory API, and returns a downloadable Excel report.

## What changed

- Removed local-machine absolute paths and source-tree file mutation.
- Replaced the embedded TestNG execution flow with a runtime-safe job flow based on the uploaded CSV.
- Moved deploy-time configuration to environment variables.
- Added configurable CORS so the deployed frontend can call the backend directly.
- Added Docker packaging and a deployment env template for DevOps.

## Runtime flow

1. `POST /api/tests/upload` uploads a CSV and returns a `fileId`.
2. `POST /api/tests/execute/{fileId}` runs the recommendation job synchronously.
3. `POST /api/tests/execute-async/{fileId}` starts the job in the background.
4. `GET /api/tests/status/{fileId}` returns job status.
5. `GET /api/tests/download/{fileId}` downloads the generated Excel report.

Health endpoint:

- `GET /api/tests/health`

## Required environment variables

Use [.env.example](/Users/shalini/Desktop/MySpaCoverSkuRecommendationBackend/.env.example) as the source of truth.

Required for real execution:

- `APP_CORS_ALLOWED_ORIGINS`
- `INVENTORY_API_LOGIN_EMAIL`
- `INVENTORY_API_LOGIN_PASSWORD`
- `INVENTORY_API_WEBHOOK_KEY`

Usually left as defaults unless infrastructure requires different values:

- `SERVER_PORT`
- `APP_STORAGE_ROOT`
- `INVENTORY_API_BASE_URL`
- `INVENTORY_API_LOGIN_ENDPOINT`
- `INVENTORY_API_INVENTORY_ENDPOINT`
- `INVENTORY_API_CONNECT_TIMEOUT`
- `INVENTORY_API_READ_TIMEOUT`

## Local run

```bash
./mvnw spring-boot:run
```

Set the env vars first, especially the inventory credentials, webhook key, and frontend origin.

## Build

```bash
./mvnw clean package
```

If CI/CD wants to skip tests temporarily:

```bash
./mvnw clean package -DskipTests
```

## Docker

Build:

```bash
docker build -t myspa-sku-backend .
```

Run:

```bash
docker run --env-file .env.example -p 8080:8080 myspa-sku-backend
```

Replace the blank values in the env file before using it.

## Frontend and proxy notes

- Point the frontend API base URL at this backend server.
- Set `APP_CORS_ALLOWED_ORIGINS` to the exact frontend origin, for example `https://spa.example.com`.
- If you use Nginx or another reverse proxy, route `/api/tests/` to this service.
- Persist `/app/data` if you want uploaded CSVs and generated reports to survive container restarts.

## Verification

Code compilation was verified locally with:

```bash
./mvnw -o -Dmaven.repo.local=/Users/shalini/.m2/repository compile
```

Full `mvn test` could not be completed in this sandbox because the local Maven cache does not contain the required `maven-surefire-plugin` transitive artifacts and network access is blocked here.
