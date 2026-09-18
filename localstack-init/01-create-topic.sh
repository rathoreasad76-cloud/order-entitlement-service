#!/bin/bash
# Runs automatically on LocalStack startup (mounted into /etc/localstack/init/ready.d).
# Creates the SNS topic the app publishes outbox events to, so `docker compose up`
# gives you a fully working local stack with no manual setup step.
set -euo pipefail

awslocal sns create-topic --name order-entitlement-events

echo "Created SNS topic: order-entitlement-events"
