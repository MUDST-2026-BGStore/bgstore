package com.chanakanlabs.theparlor.account.internal.persistence;

import com.chanakanlabs.theparlor.account.internal.domain.Account;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {}
