output "application_url" {
  value = "https://${local.app_fqdn}"
}

output "backend_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "ecs_service_name" {
  value = aws_ecs_service.backend.name
}

output "demo_seed_task_family" {
  value = aws_ecs_task_definition.demo_seed.family
}

output "frontend_bucket_name" {
  value = aws_s3_bucket.frontend.id
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.site.id
}

output "database_endpoint" {
  value     = aws_db_instance.postgres.endpoint
  sensitive = true
}

output "alarm_topic_arn" {
  value = aws_sns_topic.alarms.arn
}

output "github_deploy_role_arn" {
  value = aws_iam_role.github_deploy.arn
}
