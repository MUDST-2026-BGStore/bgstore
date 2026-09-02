package com.chanakanlabs.bgstore.clients;

import com.chanakanlabs.bgstore.identity.AuthenticatedIdentity;
import com.chanakanlabs.bgstore.identity.CurrentIdentityProvider;
import com.chanakanlabs.bgstore.identity.IdentityAccountService;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserService {

  private static final Pattern THAI_LOCAL_MOBILE = Pattern.compile("^0([689][0-9]{8})$");
  private static final Pattern THAI_E164_MOBILE = Pattern.compile("^\\+66([689][0-9]{8})$");

  private final CurrentIdentityProvider currentIdentityProvider;
  private final IdentityAccountService identityAccounts;
  private final ClientProfileRepository clientProfiles;

  CurrentUserService(
      CurrentIdentityProvider currentIdentityProvider,
      IdentityAccountService identityAccounts,
      ClientProfileRepository clientProfiles) {
    this.currentIdentityProvider = currentIdentityProvider;
    this.identityAccounts = identityAccounts;
    this.clientProfiles = clientProfiles;
  }

  @Transactional
  public CurrentUser currentUser() {
    AuthenticatedIdentity identity = currentIdentityProvider.currentIdentity();
    identityAccounts.synchronize(identity);
    Optional<ClientProfileData> profile = clientProfileFor(identity);
    return new CurrentUser(identity, profile.orElse(null));
  }

  @Transactional
  public ClientProfileData completeClientProfile(String phone) {
    AuthenticatedIdentity identity = currentIdentityProvider.currentIdentity();
    if (!identity.isClientOnly()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client onboarding is not required.");
    }

    identityAccounts.synchronize(identity);
    clientProfiles.createIfAbsent(identity.subject());
    return clientProfiles.complete(identity.subject(), normalizeThaiMobile(phone));
  }

  @Transactional(readOnly = true)
  public boolean requiresOnboarding(AuthenticatedIdentity identity) {
    return identity.isClientOnly()
        && !clientProfiles
            .findBySubject(identity.subject())
            .map(ClientProfileData::completed)
            .orElse(false);
  }

  private Optional<ClientProfileData> clientProfileFor(AuthenticatedIdentity identity) {
    if (!identity.isClientOnly()) {
      return Optional.empty();
    }
    clientProfiles.createIfAbsent(identity.subject());
    return clientProfiles.findBySubject(identity.subject());
  }

  private static String normalizeThaiMobile(String phone) {
    String compact = phone.replaceAll("[\\s-]", "");
    var localMatch = THAI_LOCAL_MOBILE.matcher(compact);
    if (localMatch.matches()) {
      return "+66" + localMatch.group(1);
    }
    if (THAI_E164_MOBILE.matcher(compact).matches()) {
      return compact;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid Thai mobile number.");
  }

  public record CurrentUser(
      AuthenticatedIdentity identity, @Nullable ClientProfileData clientProfile) {
    public boolean onboardingRequired() {
      return identity.isClientOnly() && (clientProfile == null || !clientProfile.completed());
    }
  }
}
