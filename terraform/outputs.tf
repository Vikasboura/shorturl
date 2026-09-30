output "dynamodb_links_table_name" {
  description = "DynamoDB Links table name"
  value       = aws_dynamodb_table.links_table.name
}

output "dynamodb_clicks_table_name" {
  description = "DynamoDB Click Events table name"
  value       = aws_dynamodb_table.clicks_table.name
}

output "s3_dashboard_bucket_name" {
  description = "S3 Bucket hosting React Dashboard"
  value       = aws_s3_bucket.dashboard_bucket.id
}

output "cloudfront_distribution_domain" {
  description = "CloudFront CDN domain URL for accessing the dashboard"
  value       = "https://${aws_cloudfront_distribution.dashboard_cdn.domain_name}"
}

output "app_iam_role_arn" {
  description = "IAM Role ARN for Spring Boot backend runtime"
  value       = aws_iam_role.app_role.arn
}
