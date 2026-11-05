# Leave these blocks in place after adoption: imports are idempotent.
# Fresh environments and rebuilds use adopt_existing=false.
import {
  for_each = var.adopt_existing ? { current = var.existing_resource_ids.vpc } : {}
  to       = aws_vpc.this
  id       = each.value
}
import {
  for_each = var.adopt_existing ? { current = var.existing_resource_ids.public_subnet } : {}
  to       = aws_subnet.public
  id       = each.value
}
import {
  for_each = var.adopt_existing ? { current = var.existing_resource_ids.internet_gateway } : {}
  to       = aws_internet_gateway.this
  id       = each.value
}
import {
  for_each = var.adopt_existing ? { current = var.existing_resource_ids.public_route_table } : {}
  to       = aws_route_table.public
  id       = each.value
}
import {
  for_each = var.adopt_existing ? { current = var.existing_resource_ids.public_subnet_route } : {}
  to       = aws_route_table_association.public
  id       = each.value
}
import {
  for_each = var.adopt_existing ? { current = var.existing_resource_ids.security_group } : {}
  to       = aws_security_group.instance
  id       = each.value
}
import {
  for_each = var.adopt_existing ? { current = var.existing_resource_ids.instance } : {}
  to       = aws_instance.this
  id       = each.value
}
import {
  for_each = var.adopt_existing ? { current = var.existing_resource_ids.instance } : {}
  to       = aws_ec2_instance_state.this
  id       = each.value
}
