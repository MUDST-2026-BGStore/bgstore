# AWS automatically creates the VPC's default ACL, security group and main route
# table. This stack manages the custom resources actually used by devopsandbox.
# It leaves the account's separate default VPC and shared DHCP options alone.
resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = merge(var.tags, { Name = "${var.network_name}-vpc" })

}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = false
  tags                    = merge(var.tags, { Name = "${var.network_name}-subnet-public1-${var.availability_zone}" })

}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.network_name}-igw" })

}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }
  tags = merge(var.tags, { Name = "${var.network_name}-rtb-public" })

}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id

}

resource "aws_security_group" "instance" {
  name        = var.security_group_name
  description = var.security_group_description
  vpc_id      = aws_vpc.this.id
  tags        = var.tags

  # An explicit empty list revokes all ingress when both CIDR sets are empty.
  # Omitting dynamic ingress blocks would leave imported rules unmanaged.
  ingress = [for rule in concat(
    length(var.ssh_cidrs) > 0 ? [{ port = 22, cidrs = var.ssh_cidrs }] : [],
    length(var.web_cidrs) > 0 ? [for port in [80, 443] : { port = port, cidrs = var.web_cidrs }] : []
    ) : {
    protocol         = "tcp"
    from_port        = rule.port
    to_port          = rule.port
    cidr_blocks      = sort(tolist(rule.cidrs))
    description      = ""
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    security_groups  = []
    self             = false
  }]
  egress {
    protocol    = "-1"
    from_port   = 0
    to_port     = 0
    cidr_blocks = ["0.0.0.0/0"]
  }

}

data "aws_key_pair" "existing" {
  count    = var.ssh_public_key == null ? 1 : 0
  key_name = var.ssh_key_name
}

resource "aws_key_pair" "new" {
  count      = var.ssh_public_key == null ? 0 : 1
  key_name   = var.ssh_key_name
  public_key = var.ssh_public_key
  tags       = var.tags

  lifecycle {
    prevent_destroy = true
    precondition {
      condition     = !var.adopt_existing
      error_message = "Keep the existing naos key during adoption. Registering a new key does not update authorized_keys on an existing machine."
    }
  }
}

resource "aws_instance" "this" {
  ami               = var.ami_id
  instance_type     = var.instance_type
  availability_zone = var.availability_zone
  subnet_id         = aws_subnet.public.id
  private_ip        = var.private_ip
  # A stopped instance has no ephemeral IP. AWS does not return its original
  # launch flag reliably then; leave this computed for an adopted instance.
  associate_public_ip_address = var.adopt_existing ? null : var.associate_public_ip_address
  vpc_security_group_ids      = [aws_security_group.instance.id]
  key_name                    = var.ssh_public_key == null ? data.aws_key_pair.existing[0].key_name : aws_key_pair.new[0].key_name
  ebs_optimized               = true
  monitoring                  = false
  iam_instance_profile        = local.needs_instance_role ? aws_iam_instance_profile.ssm[0].name : null
  user_data                   = var.bootstrap_new_instance ? file("${path.module}/cloud-init.yaml") : null
  tags                        = merge(var.tags, { Name = var.instance_name })

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    iops                  = 3000
    throughput            = 125
    encrypted             = var.root_volume_encrypted
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    http_protocol_ipv6          = "disabled"
    instance_metadata_tags      = "disabled"
  }

  lifecycle {
    precondition {
      condition     = !var.bootstrap_new_instance || !var.adopt_existing
      error_message = "Cloud-init is for a provisioned/rebuilt instance. Use Ansible when adopting an existing server."
    }
  }
  depends_on = [aws_route_table_association.public, aws_iam_role_policy_attachment.ssm, aws_iam_role_policy.ecr_pull]
}

# Manage power state explicitly; stopped is the cost-conscious default.
resource "aws_ec2_instance_state" "this" {
  instance_id = aws_instance.this.id
  state       = var.instance_state
}
