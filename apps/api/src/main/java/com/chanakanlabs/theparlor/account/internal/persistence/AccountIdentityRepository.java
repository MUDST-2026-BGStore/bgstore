package com.chanakanlabs.theparlor.account.internal.persistence;

import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentity;
import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentityId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountIdentityRepository
    extends JpaRepository<AccountIdentity, AccountIdentityId> {
  List<AccountIdentity> findBySubject(String subject);

  /** pattern is an escaped LIKE pattern (escape character '\'), already lower-cased. */
  @Query(
      "select i from AccountIdentity i join fetch i.account a"
          + " where lower(concat(a.firstName, ' ', a.lastName)) like :pattern escape '\\'"
          + " or a.phoneNumber like :pattern escape '\\'"
          + " order by a.firstName, a.lastName, i.subject")
  List<AccountIdentity> search(@Param("pattern") String pattern, Pageable page);
}
