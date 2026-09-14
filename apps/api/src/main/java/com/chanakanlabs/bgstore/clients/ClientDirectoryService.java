package com.chanakanlabs.bgstore.clients;

import com.chanakanlabs.bgstore.contract.model.ClientListResponse;
import com.chanakanlabs.bgstore.contract.model.ClientSummary;
import com.chanakanlabs.bgstore.identity.AccessPolicy;
import com.chanakanlabs.bgstore.identity.IdentityAccountJpaRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-only lookup of registered clients used when making a reservation. */
@Service
@Transactional(readOnly = true)
public class ClientDirectoryService {
  private final IdentityAccountJpaRepository accounts;
  private final AccessPolicy accessPolicy;

  ClientDirectoryService(IdentityAccountJpaRepository accounts, AccessPolicy accessPolicy) {
    this.accounts = accounts;
    this.accessPolicy = accessPolicy;
  }

  public ClientListResponse list(@Nullable String search) {
    accessPolicy.requireStaffOrManager();
    String normalized = search == null ? null : search.trim();
    return new ClientListResponse(
        accounts.findClients(normalized).stream()
            .map(
                row -> {
                  ClientSummary client = new ClientSummary(row.subject(), row.displayName());
                  client.setFirstName(row.firstName());
                  client.setLastName(row.lastName());
                  client.setPhone(row.phone());
                  return client;
                })
            .toList());
  }
}
