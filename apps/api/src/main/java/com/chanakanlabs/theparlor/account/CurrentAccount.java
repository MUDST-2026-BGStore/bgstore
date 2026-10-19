package com.chanakanlabs.theparlor.account;

import com.chanakanlabs.theparlor.contracts.model.Profile;
import java.util.Optional;
import java.util.UUID;

public interface CurrentAccount {
  // returns existing ID or empty
  Optional<UUID> findId();

  // returns existing ID or throws
  UUID requireId();

  Optional<Profile> findCurrentProfile();
}
