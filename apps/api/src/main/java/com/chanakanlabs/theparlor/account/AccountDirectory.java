package com.chanakanlabs.theparlor.account;

import java.util.Optional;
import java.util.UUID;

/** Looks up another person's account, for staff acting on behalf of a registered customer. */
public interface AccountDirectory {
  Optional<AccountContact> findBySubject(String subject);

  record AccountContact(UUID accountId, String name, String phoneNumber) {}
}
