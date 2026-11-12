output "instance_id" {
  value = aws_instance.this.id
}

output "aws_context" {
  description = "Used by the local connection helper, including when this stack is recreated in another environment."
  value = {
    profile     = var.aws_profile
    region      = var.aws_region
    instance_id = aws_instance.this.id
    enable_ecr  = var.enable_ecr
  }
}

output "ecr_repository_urls" {
  value = { for name, repository in aws_ecr_repository.application : name => repository.repository_url }
}

output "vpc_id" {
  value = aws_vpc.this.id
}

output "subnet_id" {
  value = aws_subnet.public.id
}

output "private_ip" {
  value = aws_instance.this.private_ip
}

output "elastic_ip" {
  value = var.elastic_ip.create ? aws_eip.this[0].public_ip : try(data.aws_eip.existing[0].public_ip, null)
}

output "ssh_host" {
  description = "Attached EIP, ephemeral public IP, or private IP (VPN/bastion needed). Refresh after a stop/start."
  value = local.has_elastic_ip && var.elastic_ip.associate ? (
    var.elastic_ip.create ? aws_eip.this[0].public_ip : data.aws_eip.existing[0].public_ip
  ) : coalesce(aws_instance.this.public_ip, aws_instance.this.private_ip)
  depends_on = [aws_eip_association.this]
}

output "ssm_command" {
  value = var.enable_ssm ? "aws ssm start-session --profile ${var.aws_profile} --region ${var.aws_region} --target ${aws_instance.this.id}" : null
}
