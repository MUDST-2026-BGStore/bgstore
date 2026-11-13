package com.chanakanlabs.theparlor.account.internal.persistence;

import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentity;
import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentityId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountIdentityRepository
    extends JpaRepository<AccountIdentity, AccountIdentityId> {
  java.util.List<AccountIdentity> findBySubject(String subject);
}
