# E-Commerce Microservices — Spring Cloud Demo

A hands-on reference project that demonstrates the core building blocks of a
Spring Cloud microservices system, modeled on a small e-commerce domain.

> **Payment module is intentionally skipped** per the brief. The pieces that are
> here (discovery, central config, gateway, inter-service calls) are exactly the
> ones a payment service would plug into.

---

## 1. The big picture

```
                       ┌────────────────────────┐
                       │   Discovery Server      │
                       │   (Eureka)  :8761       │
                       └────────────▲───────────┘
            register/discover       │  every service registers here
        ┌──────────────┬────────────┼───────────────┬──────────────┐
        │              │            │               │              │
┌───────┴──────┐ ┌─────┴───────┐ ┌──┴──────────┐ ┌──┴─────────┐ ┌──┴──────────┐
│ Config Server│ │ API Gateway │ │  Product    │ │ Inventory  │ │   Order     │
│  :8888       │ │  :8080      │ │  Service    │ │  Service   │ │  Service    │
│              │ │             │ │  :8081      │ │  :8082     │ │  :8083      │
└──────▲───────┘ └─────▲───────┘ └─────────────┘ └─────▲──────┘ └─────┬───────┘
       │               │                                │             │
       │ all services pull config from here             │   OpenFeign │
       └────────────────────────────────────────────────┴─ stock check┘
                                       (order → inventory, load-balanced)

Clients ──► API Gateway :8080 ──► /api/products  ──► product-service
                                 /api/inventory ──► inventory-service
                                 /api/orders    ──► order-service
```

---

## 2. The microservices concepts, and where each one lives

| Concept | Module | What it does |
|---|---|---|
| **Service Discovery** | `discovery-server` | Netflix **Eureka** registry. Every service registers on startup; others find it by name, not host/port. |
| **Centralized Configuration** | `config-server` | Spring Cloud **Config Server**. Single source of truth for all config (in `config-repo/`). Services fetch their config at boot. |
| **API Gateway** | `api-gateway` | Spring Cloud **Gateway**. Single entry point; path-based routing to services via `lb://` (Eureka-aware load balancing). |
| **Declarative inter-service calls** | `order-service` | **OpenFeign** client (`InventoryClient`) calls inventory-service by its Eureka name, load-balanced automatically. |
| **Client-side load balancing** | (all clients) | Spring Cloud **LoadBalancer** resolves `lb://service-name` and Feign names to live instances. |
| **Circuit Breaker + Retry** | `order-service` | **Resilience4j** guards the inventory calls (`InventoryGateway`). Opens the circuit when inventory-service fails, short-circuits to a fallback (HTTP 503) instead of cascading. |
| **Saga + compensating rollback** | `order-service` (orchestrator) + `inventory-service` (participant) | `OrderSaga` runs a multi-step distributed transaction (reserve → create → confirm). If a step fails, it undoes the completed steps in reverse via compensations (release reservation, cancel order). |
| **Business services** | `product-`, `inventory-`, `order-service` | Plain Spring Boot + JPA (H2 in-memory) services that own their own data. |

### How service discovery works here
1. `discovery-server` starts and is the registry (registers with *nobody*).
2. Each other service has `spring-cloud-starter-netflix-eureka-client` and a
   `eureka.client.service-url.defaultZone` (set centrally in
   `config-repo/application.yml`). On boot it registers itself.
3. When order-service needs inventory-service, it asks Eureka for instances and
   load-balances — it never hardcodes `localhost:8082`.

### How centralized config works here
- `config-server` runs with the `native` profile, serving `*.yml` files from
  `config-server/src/main/resources/config-repo/`.
- Each service ships a *tiny* `application.yml` containing only its name plus
  `spring.config.import: optional:configserver:http://localhost:8888`.
- At boot the service downloads `application.yml` (shared) + `<its-name>.yml`
  (specific) from the config server — that's where its port and datasource come from.

### How the circuit breaker works here
The order → inventory call is the one network hop that can fail, so it's wrapped
with **Resilience4j** in `InventoryGateway` (its own bean — see the note below):

```java
@Retry(name = "inventory")
@CircuitBreaker(name = "inventory", fallbackMethod = "reserveFallback")
public ReserveResponse reserveStock(String skuCode, int quantity) {
    return inventoryClient.reserve(new ReserveRequest(skuCode, quantity));  // remote call
}

private ReserveResponse reserveFallback(String skuCode, int quantity, Throwable t) {
    throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Inventory unavailable…");
}
```

- **Retry**: a couple of attempts for transient blips before giving up.
- **Circuit breaker**: counts failures over a sliding window
  (`config-repo/order-service.yml`). At ≥50% failures over the last 10 calls it
  **opens** for 10s, short-circuiting straight to the fallback so order-service
  stops hammering a dead inventory-service. After 10s it goes **half-open**,
  lets a few probe calls through, and **closes** again if they succeed.
- **Why a separate bean?** Resilience4j works via Spring AOP proxies, which only
  intercept calls coming from *outside* the bean. If `reserveStock` lived on
  `OrderSaga` and were called by another `OrderSaga` method, that self-invocation
  would bypass the proxy and the breaker would silently never run.
- A reserve that simply fails for "insufficient stock" returns a normal `200`
  (`success:false`), so it does **not** count against the breaker — only
  timeouts / connection-refused / 5xx do.

### How the saga works here
A monolith would place an order in one ACID transaction. Across services with
separate databases that's impossible, so `OrderSaga` runs a **sequence of local
transactions, each with a compensating action**, and reaches a consistent end
state (all done, or all undone) — *eventual consistency*, not atomic rollback.

| # | Forward step | Where | Compensation (undo) |
|---|---|---|---|
| 1 | Reserve inventory | inventory-service (remote) | Release the reservation |
| 2 | Create order `PENDING` | order-service (local) | Mark the order `CANCELLED` |
| 3 | Confirm order | order-service (local) | — (last step; nothing after it) |

- As each forward step succeeds, its undo action is **pushed onto a stack**.
- If any step throws, the saga **pops and runs the compensations newest-first
  (LIFO)** — exactly mirroring how you'd unwind nested work.
- Step 3 is a **deterministic, demo-triggerable failure**: send
  `"simulateFailure": true` to force it (stands in for a shipping/risk check or
  the skipped payment step).
- **Idempotent compensation**: releasing a reservation flips its status to
  `RELEASED`; releasing it again is a safe no-op. This matters because retries
  and the circuit breaker can cause a compensation to be attempted more than once.
- Compensations are run **in isolation** — one that fails is logged but doesn't
  stop the others. (In production a failed compensation would go to a retry
  queue / outbox, since a stuck reservation leaks stock until undone.)

### How the gateway works here
- Routes are defined centrally in `config-repo/api-gateway.yml`.
- `Path=/api/products/**` → `lb://product-service`, etc.
- `lb://` means "look this service up in Eureka and load-balance" — so the
  gateway needs zero knowledge of where services actually run.

---

## 3. Project layout

```
ecommerce-microservices/
├── pom.xml                     ← parent (manages Spring Boot + Spring Cloud BOM)
├── discovery-server/           ← Eureka server          (:8761)
├── config-server/              ← Config server          (:8888)
│   └── .../config-repo/        ← the actual config files served to everyone
├── api-gateway/                ← Spring Cloud Gateway    (:8080)
├── product-service/            ← catalog CRUD            (:8081)
├── inventory-service/          ← stock lookup            (:8082)
└── order-service/              ← orders + Feign call     (:8083)
```

---

## 4. Running it

**Prerequisites:** JDK 17 (works on 17–23) and Maven (or use your IDE).

> ⚠️ Build with JDK 17–23. Lombok's annotation processor does not yet support
> JDK 26, so on a JDK 26 default you'll see "cannot find symbol: method getX()".
> Point Maven at JDK 17, e.g.:
> `JAVA_HOME=/path/to/jdk-17 mvn clean install`

Start the services **in this order** (each in its own terminal), because the
later ones depend on the earlier ones being up:

```bash
# 1. Eureka first — everyone needs to register
mvn -pl discovery-server spring-boot:run

# 2. Config server — services fetch config from it at boot
mvn -pl config-server spring-boot:run

# 3. Gateway + business services (order can start last)
mvn -pl api-gateway       spring-boot:run
mvn -pl product-service   spring-boot:run
mvn -pl inventory-service spring-boot:run
mvn -pl order-service     spring-boot:run
```

> Build everything first if you prefer: `mvn clean install` from the root.

**Dashboards / consoles:**
- Eureka dashboard: <http://localhost:8761> (watch services appear)
- H2 console (per service): e.g. <http://localhost:8081/h2-console>

---

## 5. Try it — all through the gateway (:8080)

```bash
# Create a product
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone 15","description":"Apple phone","price":999.00,"skuCode":"iphone-15"}'

# List products
curl http://localhost:8080/api/products

# Check inventory (seeded: iphone-15=50, galaxy-s24=0, pixel-9=12)
curl http://localhost:8080/api/inventory/iphone-15      # inStock: true
curl http://localhost:8080/api/inventory/galaxy-s24     # inStock: false

# Place an order — runs the SAGA: reserve inventory → create → confirm
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"skuCode":"iphone-15","quantity":2}'             # 201, status CONFIRMED
# -> {"id":1,...,"status":"CONFIRMED","reservationId":1}  and stock 50 -> 48

# Order for an out-of-stock SKU is rejected (saga aborts at step 1)
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"skuCode":"galaxy-s24","quantity":1}'            # 409 Conflict
```

These calls are the payoff: a request hits the **gateway**, is routed to
**order-service** (found via **Eureka**, configured via the **config server**),
which drives a **saga** of **load-balanced Feign calls** to **inventory-service**
before committing the order. That's the whole stack working together.

---

## 6. Watch the circuit breaker trip

1. With everything running, **stop `inventory-service`** (Ctrl-C its terminal).
2. Fire several orders so the breaker's window fills up:
   ```bash
   for i in $(seq 1 8); do
     curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/orders \
       -H "Content-Type: application/json" \
       -d '{"skuCode":"iphone-15","quantity":1}'
   done
   ```
   The first few requests fail slowly (retrying a dead service) and return `503`.
   Once the failure rate crosses the threshold the breaker **opens** — subsequent
   requests return `503` *immediately* (no waiting, no retries) from the fallback.
3. Inspect the breaker state via actuator:
   ```bash
   curl http://localhost:8083/actuator/health | jq '.components.circuitBreakers'
   curl http://localhost:8083/actuator/circuitbreakers
   curl http://localhost:8083/actuator/circuitbreakerevents
   ```
4. **Restart `inventory-service`.** After the 10s open window the breaker goes
   half-open, the next probe succeeds, it closes, and orders flow again.

This is the resilience payoff: one downstream outage degrades gracefully (fast,
predictable 503s) instead of piling up threads and cascading across services.

## 7. Watch the saga compensate (rollback)

The saga's third step can be forced to fail with `"simulateFailure": true`,
which triggers the compensations. Watch the inventory **go down and come back**:

```bash
# Stock before
curl -s http://localhost:8080/api/inventory/iphone-15           # quantity: 48

# Force a saga failure at the confirm step
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"skuCode":"iphone-15","quantity":5,"simulateFailure":true}'   # 422

# Stock after — UNCHANGED: step 1 reserved 5, the rollback released them again
curl -s http://localhost:8080/api/inventory/iphone-15           # quantity: 48
```

The order-service log shows the compensation unwinding in reverse:

```
[saga …] step 1 OK  reserved (reservationId=2)        # forward: stock 48 -> 43
[saga …] step 2 OK  order 2 PENDING
[saga …] step 3 simulated failure during confirmation
[saga …] FAILED: 422 … — running 2 compensation(s)
[saga …] compensating: cancel order 2                  # undo step 2 (LIFO)
[saga …] compensating: release reservation 2           # undo step 1 -> stock back to 48
```

Net effect: no partial state is left behind. The order ends up `CANCELLED` and
the stock is fully restored — the distributed equivalent of a transaction rollback.

> ✅ This exact run is verified — the numbers above (50 → 48 confirmed, then a
> failed order that reserves 5 and releases them back to 48) are the real output.

## 8. Where to look next (extending the demo)

- Add a **payment-service** — it would register with Eureka, get config from the
  config server, sit behind the gateway, and slot into the saga as a new step
  between "create order" and "confirm" (with a *refund* as its compensation).
- Make the saga **choreography-based** instead of orchestration-based: services
  publish events (Kafka/RabbitMQ) and react, rather than order-service calling
  each one. Removes the central coordinator at the cost of harder traceability.
- Add a **TimeLimiter** + bulkhead to the inventory calls (needs an async/reactive
  return type), complementing the circuit breaker that's already in place.
- Swap the config `native` backend for a **Git** repo.
- Add **distributed tracing** (Micrometer + Zipkin) to follow a request across
  services.
