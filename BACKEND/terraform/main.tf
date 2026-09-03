resource "aws_iam_role" "lambda_role" {
name = "greenops-lambda-role"

assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
    Action    = "sts:AssumeRole"
    Effect    = "Allow"
    Principal = { Service = "lambda.amazonaws.com" }
    }]
})
}

resource "aws_iam_role_policy_attachment" "lambda_basic" {
role       = aws_iam_role.lambda_role.name
policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_policy" "greenops_policy" {
name = "GreenOpsLambdaPolicy"
policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
    {
        Effect = "Allow"
        Action = [
        "costexplorer:TagResources",
        "costexplorer:GetCostAndUsage"
        ]
        Resource = "*"
    },
    {
        Effect = "Allow"
        Action = [
        "dynamodb:PutItem",
        "dynamodb:UpdateItem"
        ]
        Resource = aws_dynamodb_table.audit_log.arn
    },
    {
        Effect = "Allow"
        Action = [
        "secretsmanager:GetSecretValue"
        ]
        Resource = aws_secretsmanager_secret.slack_webhook_secret.arn
    }
    ]
})
}

resource "aws_iam_role_policy_attachment" "attach_greenops_policy" {
role       = aws_iam_role.lambda_role.name
policy_arn = aws_iam_policy.greenops_policy.arn
}

resource "aws_secretsmanager_secret" "slack_webhook_secret" {
name = var.slack_webhook_secret_name
}

resource "aws_secretsmanager_secret_version" "slack_webhook_version" {
secret_id     = aws_secretsmanager_secret.slack_webhook_secret.id
secret_string = var.slack_webhook_url # inject via GitHub Secrets or SSM
}

resource "aws_dynamodb_table" "audit_log" {
name         = "greenops-audit-log"
billing_mode = "PAY_PER_REQUEST"
hash_key     = "scan_id"

attribute {
    name = "scan_id"
    type = "S"
}

tags = {
    Project     = "GreenOps"
    Environment = "dev"
}
}

locals {

lambda_jar_path = tolist(fileset("${path.module}/../target", "greenops-scrapper-*.jar"))[0]
}

resource "aws_lambda_function" "scrapper" {
function_name    = "greenops-scanner"
role              = aws_iam_role.lambda_role.arn
runtime           = "java17"
handler           = "com.greenops.scrapper.handler.ScanHandler::run"
filename          = "${path.module}/../target/${local.lambda_jar_path}"
source_code_hash  = filebase64sha256("${path.module}/../target/${local.lambda_jar_path}")

timeout = 300

environment {
    variables = {
    SLACK_WEBHOOK_URL_ARN = aws_secretsmanager_secret.slack_webhook_secret.arn
    DYNAMODB_TABLE_NAME    = aws_dynamodb_table.audit_log.name
    APP_REGION              = var.aws_region
    }
}
}

resource "aws_cloudwatch_log_group" "scrapper_logs" {
name              = "/aws/lambda/greenops-scanner"
retention_in_days = 30
}

resource "aws_cloudwatch_event_rule" "nightly_scan" {
name                = "nightly-scan"
  schedule_expression = "cron(0 1 * * ? *)" # 01:00 UTC every day
}

resource "aws_cloudwatch_event_target" "scanner_target" {
rule      = aws_cloudwatch_event_rule.nightly_scan.name
target_id = "GreenOpsScanner"
arn       = aws_lambda_function.scrapper.arn
}

resource "aws_lambda_permission" "allow_eventbridge" {
statement_id  = "AllowEventBridgeInvoke"
action        = "lambda:InvokeFunction"
function_name = aws_lambda_function.scrapper.function_name
principal     = "events.amazonaws.com"
source_arn    = aws_cloudwatch_event_rule.nightly_scan.arn
}