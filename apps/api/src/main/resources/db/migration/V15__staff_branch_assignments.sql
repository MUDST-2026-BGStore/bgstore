CREATE TABLE staff_branch_assignment (
    staff_subject VARCHAR(255) NOT NULL,
    branch_id UUID NOT NULL REFERENCES branch (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (staff_subject, branch_id)
);

CREATE INDEX ix_staff_branch_assignment_branch
    ON staff_branch_assignment (branch_id);
