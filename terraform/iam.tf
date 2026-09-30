# -------------------------------------------------------------
# IAM Role for Application Execution (EC2 / ECS / App Runner)
# Follows Amazon Bar Raiser Least Privilege Principle
# -------------------------------------------------------------
resource "aws_iam_role" "app_role" {
  name = "${var.app_name}-execution-role-${random_id.suffix.hex}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = [
            "ec2.amazonaws.com",
            "ecs-tasks.amazonaws.com"
          ]
        }
      }
    ]
  })
}

# -------------------------------------------------------------
# Least-Privilege Policy strictly scoped to our 2 DynamoDB tables
# Zero wildcard (*) resource permissions
# -------------------------------------------------------------
resource "aws_iam_policy" "dynamodb_policy" {
  name        = "${var.app_name}-dynamodb-policy-${random_id.suffix.hex}"
  description = "Scoped DynamoDB access policy for ShortLink Service"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "DynamoDbTableAccess"
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
          "dynamodb:Scan"
        ]
        Resource = [
          aws_dynamodb_table.links_table.arn,
          "${aws_dynamodb_table.links_table.arn}/*",
          aws_dynamodb_table.clicks_table.arn,
          "${aws_dynamodb_table.clicks_table.arn}/*"
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "dynamodb_attach" {
  role       = aws_iam_role.app_role.name
  policy_arn = aws_iam_policy.dynamodb_policy.arn
}

resource "aws_iam_instance_profile" "app_instance_profile" {
  name = "${var.app_name}-instance-profile-${random_id.suffix.hex}"
  role = aws_iam_role.app_role.name
}
