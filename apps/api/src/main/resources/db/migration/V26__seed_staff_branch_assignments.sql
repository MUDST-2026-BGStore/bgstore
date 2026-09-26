-- Demo staff account branch assignments. staff@example.test has a pinned
-- Keycloak user id (infra/local/keycloak/bgstore-realm.json), so its JWT `sub`
-- is stable and we can pre-seed its scope before the first sign-in. Assigning
-- every demo branch lets the seeded staff member use the floor overview and
-- branch-scoped features without waiting for a manager assignment.
INSERT INTO staff_branch_assignment (staff_subject, branch_id, created_at)
SELECT '11111111-2222-3333-4444-555555555555', id, now()
FROM branch
ON CONFLICT DO NOTHING;
