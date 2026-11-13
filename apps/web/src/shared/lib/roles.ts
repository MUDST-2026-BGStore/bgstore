/**
 * Realm roles that may open branch operations, mirroring the server's
 * `BranchAccess.requireOperator`. Staff are further limited to their assigned
 * branches by the API; Keycloak default roles grant nothing here. This only shapes
 * navigation: the API is the authority on every operation.
 */
export const BRANCH_OPERATOR_ROLES = ['staff', 'manager', 'admin'] as const;

export function canOperateBranches(roles: readonly string[]): boolean {
  return BRANCH_OPERATOR_ROLES.some((role) => roles.includes(role));
}
