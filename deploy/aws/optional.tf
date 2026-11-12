locals {
  has_elastic_ip      = var.elastic_ip.create || var.elastic_ip.allocation_id != null
  allocation_id       = var.elastic_ip.create ? aws_eip.this[0].id : var.elastic_ip.allocation_id
  needs_instance_role = var.enable_ssm || var.enable_ecr
}

resource "aws_eip" "this" {
  count  = var.elastic_ip.create ? 1 : 0
  domain = "vpc"
  tags   = merge(var.tags, { Name = "${var.instance_name}-eip" })
}

data "aws_eip" "existing" {
  count = var.elastic_ip.allocation_id != null ? 1 : 0
  id    = var.elastic_ip.allocation_id
}

resource "aws_eip_association" "this" {
  count                = local.has_elastic_ip && var.elastic_ip.associate ? 1 : 0
  allocation_id        = local.allocation_id
  network_interface_id = aws_instance.this.primary_network_interface_id
  allow_reassociation  = false
  depends_on           = [aws_internet_gateway.this]
}

resource "aws_iam_role" "ssm" {
  count = local.needs_instance_role ? 1 : 0
  name  = "${var.instance_name}-node"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "ssm" {
  count      = var.enable_ssm ? 1 : 0
  role       = aws_iam_role.ssm[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ssm" {
  count = local.needs_instance_role ? 1 : 0
  name  = "${var.instance_name}-node"
  role  = aws_iam_role.ssm[0].name
  tags  = var.tags
}

resource "aws_ecr_repository" "application" {
  for_each             = var.enable_ecr ? toset(["api", "web"]) : toset([])
  name                 = "theparlor/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  tags                 = var.tags

  encryption_configuration {
    encryption_type = "AES256"
  }
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "application" {
  for_each   = aws_ecr_repository.application
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the ten most recent sandbox images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_iam_role_policy" "ecr_pull" {
  count = var.enable_ecr ? 1 : 0
  name  = "theparlor-image-pull"
  role  = aws_iam_role.ssm[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
      {
        Effect   = "Allow"
        Action   = ["ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"]
        Resource = [for repository in aws_ecr_repository.application : repository.arn]
      }
    ]
  })
}
