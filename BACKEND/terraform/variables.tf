variable "aws_region" {
description = "AWS region where resources will be created"
type        = string
default     = "us-east-1"
}

variable "slack_webhook_url" {
description = "Incoming webhook URL for Slack notifications"
type        = string
}

variable "docker_image_uri" {
description = "Full ECR repository URI (e.g. 123456789012.dkr.ecr.us-east-1.amazonaws.com/greenops-scrapper)"
type        = string
}