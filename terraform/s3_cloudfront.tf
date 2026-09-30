# -------------------------------------------------------------
# S3 Bucket for Static React Dashboard
# Encrypted at rest with strictly blocked public access
# -------------------------------------------------------------
resource "aws_s3_bucket" "dashboard_bucket" {
  bucket        = "${var.app_name}-dashboard-${random_id.suffix.hex}"
  force_destroy = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "dashboard_encryption" {
  bucket = aws_s3_bucket.dashboard_bucket.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "dashboard_pab" {
  bucket = aws_s3_bucket.dashboard_bucket.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# -------------------------------------------------------------
# CloudFront Origin Access Control (OAC)
# Replaces deprecated OAI with sigv4 authentication
# -------------------------------------------------------------
resource "aws_cloudfront_origin_access_control" "dashboard_oac" {
  name                              = "${var.app_name}-dashboard-oac-${random_id.suffix.hex}"
  description                       = "OAC for ShortLink React Dashboard"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# -------------------------------------------------------------
# CloudFront CDN Distribution (Free Tier: PriceClass_100)
# -------------------------------------------------------------
resource "aws_cloudfront_distribution" "dashboard_cdn" {
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  price_class         = "PriceClass_100"

  origin {
    domain_name              = aws_s3_bucket.dashboard_bucket.bucket_regional_domain_name
    origin_id                = "S3-${aws_s3_bucket.dashboard_bucket.id}"
    origin_access_control_id = aws_cloudfront_origin_access_control.dashboard_oac.id
  }

  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD", "OPTIONS"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-${aws_s3_bucket.dashboard_bucket.id}"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 3600
    max_ttl                = 86400
  }

  # SPA Routing Support: Redirect 403 / 404 to index.html with 200 OK
  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 10
  }

  custom_error_response {
    error_code            = 404
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 10
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = {
    Name = "${var.app_name}-dashboard-cdn"
  }
}

# -------------------------------------------------------------
# S3 Bucket Policy allowing CloudFront OAC Read Access
# -------------------------------------------------------------
resource "aws_s3_bucket_policy" "dashboard_bucket_policy" {
  bucket = aws_s3_bucket.dashboard_bucket.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowCloudFrontServicePrincipalReadOnly"
        Effect    = "Allow"
        Principal = {
          Service = "cloudfront.amazonaws.com"
        }
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.dashboard_bucket.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.dashboard_cdn.arn
          }
        }
      }
    ]
  })
}
