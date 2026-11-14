package com.chanakanlabs.theparlor.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Looks up another person's account, for staff acting on behalf of a registered customer. */
public interface AccountDirectory {
  Optional<AccountContact> findBySubject(String subject);

  /**
   * Onboarded identities whose "first last" name contains the query case-insensitively, or whose
   * phone contains it. Queries shorter than 2 characters after trimming match nothing; at most 20
   * results, ordered by name then subject.
   */
  List<AccountMatch> search(String query, int limit);

  record AccountContact(UUID accountId, String name, String phoneNumber) {}

  record AccountMatch(String subject, String name, String phoneNumber) {}
}
