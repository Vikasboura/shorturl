# -------------------------------------------------------------
# DynamoDB: Links Table
# Partition Key: short_code
# TTL: expires_at (epoch seconds)
# Billing: PAY_PER_REQUEST (AWS Free Tier compliant)
# -------------------------------------------------------------
resource "aws_dynamodb_table" "links_table" {
  name         = "${var.app_name}-urls"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "short_code"

  attribute {
    name = "short_code"
    type = "S"
  }

  ttl {
    attribute_name = "expires_at"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "${var.app_name}-urls"
  }
}

# -------------------------------------------------------------
# DynamoDB: Click Events Analytics Table
# Partition Key: short_code
# Sort Key: timestamp_event_id
# GSI: DateIndex (PK: short_code, SK: date_str)
# -------------------------------------------------------------
resource "aws_dynamodb_table" "clicks_table" {
  name         = "${var.app_name}-clicks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "short_code"
  range_key    = "timestamp_event_id"

  attribute {
    name = "short_code"
    type = "S"
  }

  attribute {
    name = "timestamp_event_id"
    type = "S"
  }

  attribute {
    name = "date_str"
    type = "S"
  }

  global_secondary_index {
    name            = "DateIndex"
    hash_key        = "short_code"
    range_key       = "date_str"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "${var.app_name}-clicks"
  }
}
