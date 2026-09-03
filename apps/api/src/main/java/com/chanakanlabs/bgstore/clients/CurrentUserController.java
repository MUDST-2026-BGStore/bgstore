package com.chanakanlabs.bgstore.clients;

import com.chanakanlabs.bgstore.contract.api.IdentityApi;
import com.chanakanlabs.bgstore.contract.model.ApplicationRole;
import com.chanakanlabs.bgstore.contract.model.ClientProfile;
import com.chanakanlabs.bgstore.contract.model.CompleteClientProfileRequest;
import com.chanakanlabs.bgstore.contract.model.CurrentUserResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CurrentUserController implements IdentityApi {

  private final CurrentUserService currentUsers;

  public CurrentUserController(CurrentUserService currentUsers) {
    this.currentUsers = currentUsers;
  }

  @Override
  public ResponseEntity<CurrentUserResponse> getCurrentUser() {
    return ResponseEntity.ok(toResponse(currentUsers.currentUser()));
  }

  @Override
  public ResponseEntity<ClientProfile> completeClientProfile(
      CompleteClientProfileRequest completeClientProfileRequest) {
    return ResponseEntity.ok(
        toResponse(currentUsers.completeClientProfile(completeClientProfileRequest.getPhone())));
  }

  private static CurrentUserResponse toResponse(CurrentUserService.CurrentUser currentUser) {
    var identity = currentUser.identity();
    List<ApplicationRole> roles =
        identity.roles().stream()
            .sorted()
            .map(role -> ApplicationRole.valueOf(role.name()))
            .toList();
    return new CurrentUserResponse(
            identity.subject(),
            identity.username(),
            identity.email(),
            identity.firstName(),
            identity.lastName(),
            roles,
            currentUser.onboardingRequired())
        .clientProfile(
            currentUser.clientProfile() == null ? null : toResponse(currentUser.clientProfile()));
  }

  private static ClientProfile toResponse(ClientProfileData profile) {
    return new ClientProfile(profile.completed()).phone(profile.phone());
  }
}
