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

The API describes itself: an OpenAPI 3 document is served at `/v3/api-docs` and
Swagger UI at `/swagger-ui.html`. See [API docs](#api-docs) below.

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
separate poller (`OutboxPublisher`) reads unpublished rows and publishes each one
to an SNS topic, tagging the event type as a message attribute so subscribers can
filter without deserialising the body — a row is only marked published after the
SNS call actually succeeds, so a crash mid-publish just means it gets retried on
the next poll rather than lost. Consumers on the other end still need to be
idempotent, since "at least once" is the honest guarantee here, not "exactly
once" — see the `event-fanout-notifications` repo linked below for how I handle
that on the consumer side (dedup by message ID, DLQ for poison messages).

**Why SNS/SQS via LocalStack rather than mocking it out.** I could have stubbed
the AWS SDK in tests and called it done, but that only proves the code compiles
against the SDK's interfaces — not that the message actually reaches a
subscriber with the right attributes. `OutboxPublisherIntegrationTest` runs a
real LocalStack container via Testcontainers, creates the topic and a subscribed
SQS queue, and asserts the message shows up. `docker-compose.yml` does the same
for local dev — LocalStack creates the topic itself on startup (see
`localstack-init/`), so `docker compose up` gives you a fully working stack with
no manual AWS console step. Swapping the endpoint override for a real AWS
account is a one-line config change (`aws.endpoint-override` just needs to be
unset), not a code change.

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

And confirm the event actually made it out to SNS (LocalStack, running as part
of the compose stack):

```bash
docker exec order-entitlement-localstack awslocal sns list-topics
docker exec order-entitlement-localstack awslocal sqs list-queues
```

By default nothing is subscribed to the topic in the compose stack — it's there
to publish to, and `OutboxPublisherIntegrationTest` is what actually subscribes a
queue and asserts delivery. To watch it live yourself, create a queue and
subscribe it before placing an order:

```bash
docker exec order-entitlement-localstack awslocal sqs create-queue --queue-name watch-queue
docker exec order-entitlement-localstack awslocal sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:000000000000:order-entitlement-events \
  --protocol sqs \
  --notification-endpoint arn:aws:sqs:us-east-1:000000000000:watch-queue
docker exec order-entitlement-localstack awslocal sqs receive-message --queue-url http://localhost:4566/000000000000/watch-queue
```

To run just the app against your own local Postgres instead of Docker, set
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD` and run `mvn spring-boot:run`.

## API docs

The service publishes its own OpenAPI 3 description, generated from the
controllers at runtime by [springdoc-openapi](https://springdoc.org). Once the
app is up:

- **Swagger UI** — <http://localhost:8080/swagger-ui.html>, if you want to read
  the API or fire requests at it from a browser.
- **OpenAPI document** — <http://localhost:8080/v3/api-docs> (JSON) or
  `/v3/api-docs.yaml` (YAML), if you want to generate a client or diff the
  contract in CI.

I generate the spec from the code rather than hand-maintaining a YAML file,
because a spec that lives in a separate file drifts from the handlers the moment
someone is in a hurry. Endpoint descriptions sit in `@Operation` annotations next
to the methods they describe, the request/response schemas come from the same
records the controllers actually bind, and the validation constraints already on
`PlaceOrderRequest` are what produce the `required` fields and bounds in the
published schema — so there's one source of truth, not two.

Two things the spec calls out explicitly, because they're the parts a caller gets
wrong otherwise: `POST /api/orders` is idempotent on its `Idempotency-Key`
header, and entitlements are granted *after* the order commits, so reading a
customer's entitlements straight after placing an order is eventually consistent.

Grabbing the spec without a browser:

```bash
curl http://localhost:8080/v3/api-docs | jq .
```

The version reported in the document is filtered in from the Maven build
(`@project.version@`), so it tracks the artifact version rather than a number
someone has to remember to bump.

## Running the tests

```bash
mvn clean verify
```

There are four layers of tests:

- `OrderTest` — pure unit tests against the `Order` aggregate, no Spring context.
- `PlaceOrderCommandHandlerTest` — the command handler with mocked repositories,
  using a fixed `Clock` so timestamps are deterministic.
- `OrderControllerIntegrationTest` — a full Spring Boot context with a real
  Postgres instance via Testcontainers, exercising the HTTP layer end to end,
  including the async entitlement grant (polled with Awaitility, since it
  happens after commit and isn't guaranteed to be done by the time the HTTP
  response comes back).
- `OutboxPublisherIntegrationTest` — real Postgres and LocalStack containers,
  asserting a published outbox event actually arrives on a subscribed SQS queue
  with the right message attributes.

Testcontainers needs a working Docker daemon to run the integration tests.

## A note on how this was built

I'm upfront about this: I used Claude to help scaffold this project quickly —
generating boilerplate, writing the Liquibase changesets, drafting this README —
while I made the architecture decisions and reviewed the result. The design
choices above (the outbox pattern, `AFTER_COMMIT` event handling, the
idempotency approach) reflect how I actually build systems like this, not code I
don't understand. I'd rather say that plainly than have it come across as
something else.

## What's not here (yet)

- No auth/authz — out of scope for what this is demonstrating.
- No pagination on the entitlements endpoint; fine for a demo, wouldn't be fine
  at scale.
- Apache Kafka is the other broker I'd eventually want a version of this
  against — SNS/SQS is what I have direct production experience with, so it's
  what's implemented here.

## Related

[`event-fanout-notifications`](https://github.com/rathoreasad76-cloud/event-fanout-notifications) —
a standalone companion project focused purely on the consumer side of this
pattern: fan-out from one SNS topic to multiple filtered SQS subscriptions,
dead-letter queues with a redrive policy, and idempotent consumers.
