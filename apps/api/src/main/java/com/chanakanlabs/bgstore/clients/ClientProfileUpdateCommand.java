package com.chanakanlabs.bgstore.clients;

import java.util.Optional;

/** BGStore-owned fields accepted by a future account-management API. */
public record ClientProfileUpdateCommand(String phone, Optional<ProfileImageUpload> profileImage) {}
