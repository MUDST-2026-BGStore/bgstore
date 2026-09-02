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

  private static final Pattern COUNTRY_CODE = Pattern.compile("^\\+[1-9][0-9]{0,3}$");
  private static final Pattern PHONE_NUMBER = Pattern.compile("^[0-9][0-9() .-]*$");

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
  public ClientProfileData completeClientProfile(String countryCode, String phoneNumber) {
    AuthenticatedIdentity identity = currentIdentityProvider.currentIdentity();
    if (!identity.isClientOnly()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client onboarding is not required.");
    }

    identityAccounts.synchronize(identity);
    clientProfiles.createIfAbsent(identity.subject());
    return clientProfiles.complete(identity.subject(), normalizePhone(countryCode, phoneNumber));
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

  private static String normalizePhone(String countryCode, String phoneNumber) {
    String rawCountryCode = countryCode == null ? "" : countryCode;
    String rawPhoneNumber = phoneNumber == null ? "" : phoneNumber;
    String compactCountryCode = rawCountryCode.replaceAll("[\\s-]", "");
    String compactNumber = rawPhoneNumber.replaceAll("[()\\s.-]", "");
    if (!COUNTRY_CODE.matcher(compactCountryCode).matches()
        || !PHONE_NUMBER.matcher(rawPhoneNumber).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid phone number.");
    }
    if (compactNumber.startsWith("0")) {
      compactNumber = compactNumber.substring(1);
    }
    String e164 = compactCountryCode + compactNumber;
    if (!e164.matches("^\\+[1-9][0-9]{7,14}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid phone number.");
    }
    return e164;
  }

  public record CurrentUser(
      AuthenticatedIdentity identity, @Nullable ClientProfileData clientProfile) {
    public boolean onboardingRequired() {
      return identity.isClientOnly() && (clientProfile == null || !clientProfile.completed());
    }
  }
}
