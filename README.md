# Vehicle Manufacturing Service

A REST API for a car maker's production planning. It manages **assembly plants**, the **vehicle models**
they build, and **production orders** ("build 1,000 Aurora EVs at the Kanto plant between 1 and 10 Dec").

**Stack:** Java 21 · Spring Boot 3.5 · Spring Data JPA · PostgreSQL 16 · Flyway · Docker · Kubernetes ·
JUnit 5 / Mockito / Testcontainers · JaCoCo (100% line + branch gate) · Postman / newman · GitHub Actions

---

## 1. Architecture

```
Client (Postman / UI)
      │  HTTP/JSON
      ▼
┌──────────────────────────── vehicle-manufacturing-service ────────────────────────────┐
│ CorrelationIdFilter  →  Controller  →  Service (business rules, @Transactional)  →  Repository │
│                          (validation)       │                                         (JPA)      │
│ GlobalExceptionHandler  ◄── exceptions ─────┘                                                   │
└───────────────────────────────────────────────────────────────────────────────────┬────────────┘
                                                                                    ▼
                                                                        PostgreSQL (Flyway schema)
```

Code is organised **by feature** so each business area is self-contained:

```
src/main/java/com/example/manufacturing/
├── plant/             Plant entity, repository, service, controller, dto/
├── vehiclemodel/      VehicleModel ...
├── productionorder/   ProductionOrder, status life-cycle, capacity rules ...
├── common/            exceptions + RFC 9457 error handler, PageResponse, correlation-id filter
└── config/            OpenAPI
src/main/resources/
├── application.yml          all settings, overridable by environment variables
├── application-demo.yml     "demo" profile: loads sample data
├── db/migration/            Flyway schema (production)
└── db/demo/                 Flyway sample data (demo profile only)
```

## 2. Business rules

| Rule | Result |
|---|---|
| Plant / model codes are unique | `409 Conflict` |
| New orders only for `ACTIVE` plants and `active` models | `422` |
| `plannedEndDate` ≥ `plannedStartDate` | `422` |
| **Capacity:** `ceil(quantity / plant.dailyCapacity)` ≤ planned days | `422` (message explains the numbers) |
| Orders can be edited only while `PLANNED` | `422` |
| Status flow: `PLANNED → IN_PROGRESS ⇄ ON_HOLD`, `IN_PROGRESS → COMPLETED`, any non-final → `CANCELLED` | `422` for any other move |
| Progress only while `IN_PROGRESS`, never decreases, never above quantity; reaching quantity auto-completes | `422` |
| Delete order only if `PLANNED` or `CANCELLED` | `422` |
| Delete plant / model only if no orders reference it (retire it with `INACTIVE` / `active=false` instead) | `422` |
| Concurrent edits of the same record (optimistic locking via `@Version`) | `409` |

All errors use the standard **problem detail** format (`application/problem+json`):

```json
{ "type": "about:blank", "title": "Validation failed", "status": 400,
  "detail": "One or more fields are invalid", "instance": "/api/v1/plants",
  "errors": { "code": "must not be blank", "dailyCapacity": "must be greater than 0" } }
```

## 3. API

Base path `/api/v1`. Lists are paginated: `?page=0&size=20&sort=code,asc` (max size 100).
Interactive docs: **http://localhost:8080/swagger-ui.html**

| Method | Path | Description |
|---|---|---|
| GET | `/plants?status=` | List plants |
| GET | `/plants/{id}` | Get plant |
| POST | `/plants` | Create plant → `201` + `Location` |
| PUT | `/plants/{id}` | Update plant |
| DELETE | `/plants/{id}` | Delete plant → `204` |
| GET | `/vehicle-models?powertrain=&active=` | List models |
| GET | `/vehicle-models/{id}` | Get model |
| POST | `/vehicle-models` | Create model |
| PUT | `/vehicle-models/{id}` | Update model |
| DELETE | `/vehicle-models/{id}` | Delete model |
| GET | `/production-orders?status=&plantId=&vehicleModelId=` | List orders |
| GET | `/production-orders/{id}` | Get order |
| POST | `/production-orders` | Create order (starts `PLANNED`) |
| PUT | `/production-orders/{id}` | Re-plan order |
| PATCH | `/production-orders/{id}/status` | `{"status":"IN_PROGRESS"}` |
| PATCH | `/production-orders/{id}/progress` | `{"completedQuantity":400}` |
| DELETE | `/production-orders/{id}` | Delete order |

Operations: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`, `/actuator/info`.
Every response carries an `X-Correlation-Id` header (send your own to trace a request through the logs).

## 4. Run it

### Option A – everything in Docker (quickest)
```bash
docker compose up --build
# API: http://localhost:8080   (demo data loaded)
```

### Option B – database in Docker, app in IntelliJ
1. `docker compose up -d postgres`
2. Open the folder in IntelliJ (**File → Open**), let Maven import, set SDK to Java 21.
3. Run `ManufacturingApplication`. For sample data add `demo` under
   **Run → Edit Configurations → Active profiles**.
4. Try the requests in `requests.http` with IntelliJ's HTTP client.

### Configuration (environment variables)
| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/manufacturing` |
| `DB_USERNAME` / `DB_PASSWORD` | `manufacturing` / `manufacturing` |
| `DB_POOL_SIZE` | `10` |
| `SERVER_PORT` | `8080` |
| `SPRING_PROFILES_ACTIVE` | *(none)* – use `demo` for sample data |
| `APP_LOG_LEVEL` | `INFO` |

## 5. Tests and quality gates

```bash
mvn test              # unit + controller tests (no Docker needed)
mvn verify            # + Testcontainers integration tests + 100% coverage gate (Docker needed)
mvn verify -DskipITs  # coverage gate without integration tests
```

* **Unit tests** (Mockito) cover every branch of every service rule and the full 5×5 status-transition matrix.
* **Controller tests** (`@WebMvcTest`) cover every endpoint, validation errors, bad parameters, malformed JSON
  and every error status.
* **Integration tests** (`*IT`, Testcontainers PostgreSQL) run real Flyway migrations, Hibernate schema
  validation, the JPQL filters and the whole order life-cycle.
* **JaCoCo** fails the build if line **or** branch coverage is below 100% (`coverage.minimum` in `pom.xml`).
  Report: `target/site/jacoco/index.html`.

### Postman
`postman/vehicle-manufacturing.postman_collection.json` holds **63 requests with assertions** in 5 folders
(health, plants, models, orders, cleanup). They cover every endpoint, every validation error, every business
rule and every status transition. After each request a collection-level check verifies the correlation-id
header, a response time under 2 s and `application/problem+json` for errors.
Each run uses unique codes, so you can repeat it against the same database.

* **Postman app:** Import the collection + `postman/local.postman_environment.json`, select the
  environment, then **Run collection** (keep the default order).
* **Command line / CI:**
  ```bash
  npx newman run postman/vehicle-manufacturing.postman_collection.json -e postman/local.postman_environment.json
  ```

### CI
`.github/workflows/ci.yml` runs `mvn verify` (with the coverage gate), then starts the stack with
docker compose and runs the Postman collection with newman on every push and pull request to `main`.

## 6. Kubernetes

Manifests are in `k8s/`: namespace, ConfigMap, Secret (placeholder), PostgreSQL StatefulSet with a persistent
volume, Deployment (2 replicas, rolling updates with zero unavailable pods, startup/liveness/readiness probes,
resource limits, non-root user, read-only root filesystem), Service, PodDisruptionBudget, HPA (2–6 pods at 70%
CPU) and Ingress.

**minikube example**
```bash
minikube start
minikube addons enable ingress
minikube addons enable metrics-server          # for the HPA

docker build -t vehicle-manufacturing-service:1.0.0 .
minikube image load vehicle-manufacturing-service:1.0.0

kubectl apply -k k8s/
kubectl -n manufacturing get pods -w           # wait until both app pods are Ready

echo "$(minikube ip) manufacturing.local" | sudo tee -a /etc/hosts
curl http://manufacturing.local/actuator/health/readiness
```
Then run Postman with `postman/kubernetes.postman_environment.json`.
Without an ingress: `kubectl -n manufacturing port-forward svc/vehicle-manufacturing-service 8080:80`.

**Before deploying to a real cluster:**
1. Push the image to your registry and update `image:` in `k8s/app.yaml`.
2. Replace `k8s/secret.yaml` with a secret from your secret manager.
3. Prefer a managed PostgreSQL and point `DB_URL` in `k8s/configmap.yaml` at it.
4. Add authentication (see below).

## 7. Not included yet

* **Authentication / authorisation.** The API is open. Typical next step: Spring Security as an OAuth2
  resource server that validates JWTs from your identity provider (Keycloak, Azure AD, Okta...), with roles such as
  `PLANNER` (create and re-plan orders) and `LINE_SUPERVISOR` (report progress).
* Audit trail of who changed what, and event publishing (Kafka) when an order changes status.
