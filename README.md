# order-entitlement-service

A small backend service for placing orders and granting entitlements from them —
think "customer buys a product, customer gets access to that product." It's not
tied to any real business; I built it to show how I actually structure a service
when I care about getting the boundaries and the failure modes right, rather than
just wiring up CRUD endpoints.

It's deliberately close to problems I've worked on: payments and licensing systems
where "did this write actually happen, exactly once, and did the right thing
happen as a result" matters more than the CRUD itself.

## What it does

- `POST /api/orders` — place an order for one or more product lines. Idempotent:
  retrying the same request with the same `Idempotency-Key` header returns the
  original order instead of creating a duplicate.
- `GET /api/orders/{id}` — fetch an order.
- `GET /api/customers/{customerId}/entitlements` — list what a customer has been
  granted, as a result of orders they've placed.

Placing an order doesn't grant entitlements synchronously inside the same
transaction. It publishes a domain event after the order is committed, and a
listener reacts to that event to grant the entitlement and write an outbox
record. That separation is the main thing this project is trying to demonstrate,
so it's worth explaining why.

## Why the design is shaped this way

**The aggregate doesn't know about entitlements.** `Order` is responsible for its
own invariants — you can't place an order with no lines, you can't cancel an
order twice — and nothing else. It has no idea that placing an order grants
something downstream. That's a separate concern, handled by a separate listener.
If I added a second consequence of placing an order (say, sending a receipt
email), it wouldn't touch `Order` at all.

**Why `AFTER_COMMIT` instead of granting the entitlement in the same
transaction.** The tempting shortcut is: save the order, grant the entitlement,
commit once. The problem is that couples two things that don't need to fail
together. If entitlement-granting throws for an unrelated reason, you don't want
to roll back an order that was otherwise placed successfully. Listening after
commit means the order is durably saved before anything downstream even runs —
and it runs in its own transaction, so a failure there doesn't retroactively undo
the order.

**Why there's an outbox table at all.** The classic failure mode here is the
"dual write" problem: you save your own state in Postgres and separately publish
a message to a broker, and if the process dies between those two operations, one
happens and the other doesn't, with no way to tell after the fact. Writing an
outbox row in the *same* transaction as the entitlement grant means both either
happen or neither does — there's no window where you've granted something but
have no record that it should have been published anywhere, or vice versa. A
separate poller (`OutboxPublisher`) then reads unpublished rows and would hand
them to a real broker. Right now it just logs them — wiring it to Kafka/SQS/SNS
is the natural next step, and intentionally left as a seam rather than baked in,
since the point of this project is the pattern, not a specific broker.

**Why idempotency is enforced with a client-supplied key rather than just
relying on "don't double-click the button."** Networks retry. Load balancers
retry. If a client's request times out but the server actually processed it, the
client has no way to know that without a mechanism like this. The handler checks
for an existing order with the same key before doing anything else, so a retry
is safe by construction rather than by luck.

**Why reads don't go through the aggregate.** `OrderQueryService` reads JPA
projections directly instead of loading an `Order` aggregate and mapping it back
out. Rehydrating a full aggregate just to serialize it back to JSON is wasted
work and, worse, tempts you into leaking write-side invariants into read paths.
Commands go through the aggregate because that's where the business rules live;
queries don't need business rules, they need data.

## Running it locally

You'll need Java 17 and Maven (or just use the Maven wrapper if I add one — for
now, a local `mvn` install is assumed). Postgres is easiest via Docker.

```bash
docker compose up --build
```

This brings up Postgres and the app together, and the app runs its Liquibase
migrations automatically on startup. Once it's up:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-1" \
  -d '{
        "customerId": "customer-1",
        "currency": "USD",
        "lines": [{"productCode": "PRO_LICENSE", "quantity": 1, "unitPrice": 99.00}]
      }'
```

Then check the entitlement landed:

```bash
curl http://localhost:8080/api/customers/customer-1/entitlements
```

To run just the app against your own local Postgres instead of Docker, set
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD` and run `mvn spring-boot:run`.

## Running the tests

```bash
mvn clean verify
```

There are three layers of tests:

- `OrderTest` — pure unit tests against the `Order` aggregate, no Spring context.
- `PlaceOrderCommandHandlerTest` — the command handler with mocked repositories,
  using a fixed `Clock` so timestamps are deterministic.
- `OrderControllerIntegrationTest` — a full Spring Boot context with a real
  Postgres instance via Testcontainers, exercising the HTTP layer end to end,
  including the async entitlement grant (polled with Awaitility, since it
  happens after commit and isn't guaranteed to be done by the time the HTTP
  response comes back).

Testcontainers needs a working Docker daemon to run the integration test.

## A note on how this was built

I'm upfront about this: I used Claude to help scaffold this project quickly —
generating boilerplate, writing the Liquibase changesets, drafting this README —
while I made the architecture decisions and reviewed the result. The design
choices above (the outbox pattern, `AFTER_COMMIT` event handling, the
idempotency approach) reflect how I actually build systems like this, not code I
don't understand. I'd rather say that plainly than have it come across as
something else.

## What's not here (yet)

- The outbox publisher logs events instead of actually publishing to a broker.
  Swapping in a real Kafka/SQS producer is the obvious next step.
- No auth/authz — out of scope for what this is demonstrating.
- No pagination on the entitlements endpoint; fine for a demo, wouldn't be fine
  at scale.
