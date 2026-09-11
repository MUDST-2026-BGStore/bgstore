package com.chanakanlabs.bgstore.identity;

import static org.mockito.Mockito.verify;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentityAccountServiceTest {

  @Mock private IdentityAccountJpaRepository accounts;

  @Test
  void synchronizesTheOidcProjectionWithOneAtomicRepositoryOperation() {
    var service = new IdentityAccountService(accounts);
    var identity =
        new AuthenticatedIdentity(
            "subject",
            "username",
            "user@example.test",
            "Local",
            "User",
            Set.of(ApplicationRole.CLIENT));

    service.synchronize(identity);

    verify(accounts).upsert("subject", "username", "user@example.test", "Local", "User");
  }
}
