#!/bin/bash
set -e

echo "Initializing DynamoDB tables in LocalStack..."

# 1. Create shortlink-urls table
awslocal dynamodb create-table \
    --table-name shortlink-urls \
    --attribute-definitions \
        AttributeName=short_code,AttributeType=S \
    --key-schema \
        AttributeName=short_code,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1

# Enable TTL on expires_at attribute
awslocal dynamodb update-time-to-live \
    --table-name shortlink-urls \
    --time-to-live-specification "Enabled=true, AttributeName=expires_at" \
    --region us-east-1

# 2. Create shortlink-clicks table with GSI for Date-based queries
awslocal dynamodb create-table \
    --table-name shortlink-clicks \
    --attribute-definitions \
        AttributeName=short_code,AttributeType=S \
        AttributeName=timestamp_event_id,AttributeType=S \
        AttributeName=date_str,AttributeType=S \
    --key-schema \
        AttributeName=short_code,KeyType=HASH \
        AttributeName=timestamp_event_id,KeyType=RANGE \
    --global-secondary-indexes \
        "[
            {
                \"IndexName\": \"DateIndex\",
                \"KeySchema\": [
                    {\"AttributeName\":\"short_code\",\"KeyType\":\"HASH\"},
                    {\"AttributeName\":\"date_str\",\"KeyType\":\"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1

echo "DynamoDB tables successfully created!"
