package com.chanakanlabs.theparlor.account;

import com.chanakanlabs.theparlor.contracts.model.Profile;

public interface AccountProvisioning {
  Profile provisionForCurrentIdentity(AccountRegistration registration);
}
