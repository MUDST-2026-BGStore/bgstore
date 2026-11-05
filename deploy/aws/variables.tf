variable "aws_profile" {
  type    = string
  default = "chanakanlabs"
}

variable "aws_region" {
  type    = string
  default = "ap-southeast-7"
}

variable "aws_account_id" {
  type    = string
  default = "416604074663"
}

variable "adopt_existing" {
  description = "Import the recorded AWS resource IDs into an empty state. Leave false for provisioning or rebuilding the sandbox."
  type        = bool
  default     = false
}

variable "existing_resource_ids" {
  description = "Observed 2026-11-05. Imports record existing objects; they do not recreate them."
  type = object({
    vpc                 = string
    public_subnet       = string
    internet_gateway    = string
    public_route_table  = string
    public_subnet_route = string
    security_group      = string
    instance            = string
  })
  default = {
    vpc                 = "vpc-0c39febd5715c4f9a"
    public_subnet       = "subnet-084f78d6de4213473"
    internet_gateway    = "igw-03c209d95e5b44ad9"
    public_route_table  = "rtb-05e5caa8f4a6ba1bc"
    public_subnet_route = "subnet-084f78d6de4213473/rtb-05e5caa8f4a6ba1bc"
    security_group      = "sg-04683ef78375d5e75"
    instance            = "i-041c168130c959aec"
  }
}

variable "network_name" {
  type    = string
  default = "devopsnet"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  type    = string
  default = "10.0.0.0/20"
}

variable "availability_zone" {
  type    = string
  default = "ap-southeast-7a"
}

variable "instance_name" {
  type    = string
  default = "devopsandbox"
}

variable "ami_id" {
  description = "Pinned Amazon Linux 2023 ARM64 AMI, matching the existing instance. AMIs are regional."
  type        = string
  default     = "ami-03eb05a9bbe6bd96d"
}

variable "instance_type" {
  type    = string
  default = "m8g.large"
}

variable "private_ip" {
  type    = string
  default = "10.0.6.134"
}

variable "instance_state" {
  description = "Desired power state. Keep stopped when unused; set running explicitly before setup or deployment."
  type        = string
  default     = "stopped"
  validation {
    condition     = contains(["stopped", "running"], var.instance_state)
    error_message = "instance_state must be stopped or running."
  }
}

variable "associate_public_ip_address" {
  description = "The existing instance requests an ephemeral public IPv4 at launch, despite the subnet default being false. Use an optional EIP for a stable address."
  type        = bool
  default     = true
}

variable "security_group_name" {
  type    = string
  default = "devops-app"
}

variable "security_group_description" {
  type    = string
  default = "launch-wizard-1 created 2026-09-25T07:04:06.210Z"
}

variable "ssh_cidrs" {
  description = "Preserves current SSH access for adoption. Prefer your own public IPv4/32; [] disables inbound SSH."
  type        = set(string)
  default     = ["0.0.0.0/0"]
  validation {
    condition     = alltrue([for cidr in var.ssh_cidrs : can(cidrnetmask(cidr))])
    error_message = "ssh_cidrs must contain valid IPv4 CIDRs."
  }
}

variable "web_cidrs" {
  description = "IPv4 networks allowed to reach HTTP and HTTPS."
  type        = set(string)
  default     = ["0.0.0.0/0"]
  validation {
    condition     = alltrue([for cidr in var.web_cidrs : can(cidrnetmask(cidr))])
    error_message = "web_cidrs must contain valid IPv4 CIDRs."
  }
}

variable "ssh_key_name" {
  description = "Existing regional EC2 key pair, or name for a key imported from ssh_public_key. AWS cannot recover its private key."
  type        = string
  default     = "naos"
}

variable "ssh_public_key" {
  description = "Optional public SSH key to register for NEW instances. Never supply a private key."
  type        = string
  default     = null
  nullable    = true
}

variable "root_volume_size" {
  type    = number
  default = 50
  validation {
    condition     = var.root_volume_size >= 8 && floor(var.root_volume_size) == var.root_volume_size
    error_message = "Root volume size must be a whole number of GiB, at least 8. Existing volumes cannot shrink."
  }
}

variable "root_volume_encrypted" {
  description = "Encrypt new root disks. Changing encryption on an existing server requires replacement."
  type        = bool
  default     = true
}

variable "enable_ecr" {
  description = "Create private application image repositories and grant the EC2 role pull access."
  type        = bool
  default     = false
}

variable "elastic_ip" {
  description = "Create an EIP or reuse an allocation ID. associate=false reserves a new EIP without attaching it. All allocated public IPv4 addresses are billable."
  type = object({
    create        = optional(bool, false)
    allocation_id = optional(string)
    associate     = optional(bool, true)
  })
  default = {}
  validation {
    condition     = !(var.elastic_ip.create && var.elastic_ip.allocation_id != null)
    error_message = "Choose either create=true or an existing allocation_id, not both."
  }
  validation {
    condition     = var.elastic_ip.allocation_id == null ? true : can(regex("^eipalloc-[0-9a-f]+$", var.elastic_ip.allocation_id))
    error_message = "An existing allocation ID must start with eipalloc-."
  }
}

variable "enable_ssm" {
  description = "Create an EC2 role with AmazonSSMManagedInstanceCore. The running machine also needs an SSM agent and outbound HTTPS (EIP/public IP or private endpoints/NAT)."
  type        = bool
  default     = false
}

variable "bootstrap_new_instance" {
  description = "Run basic cloud-init only for NEW instances. Use Ansible for an already-created server. No application secrets are placed in user data."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Additional tags. Empty preserves current tags during adoption."
  type        = map(string)
  default     = {}
}
