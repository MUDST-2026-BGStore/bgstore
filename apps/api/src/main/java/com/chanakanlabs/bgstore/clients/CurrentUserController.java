package com.chanakanlabs.bgstore.clients;

import com.chanakanlabs.bgstore.contract.api.IdentityApi;
import com.chanakanlabs.bgstore.contract.model.ApplicationRole;
import com.chanakanlabs.bgstore.contract.model.ClientProfile;
import com.chanakanlabs.bgstore.contract.model.CompleteClientProfileRequest;
import com.chanakanlabs.bgstore.contract.model.CurrentUserResponse;
import com.chanakanlabs.bgstore.contract.model.ReplaceStaffBranchAssignmentsRequest;
import com.chanakanlabs.bgstore.contract.model.StaffBranchAssignment;
import com.chanakanlabs.bgstore.contract.model.StaffBranchAssignmentList;
import com.chanakanlabs.bgstore.identity.StaffBranchAssignmentService;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CurrentUserController implements IdentityApi {

  private final CurrentUserService currentUsers;
  private final ClientDirectoryService clientDirectory;
  private final StaffBranchAssignmentService assignments;

  public CurrentUserController(
      CurrentUserService currentUsers,
      StaffBranchAssignmentService assignments,
      ClientDirectoryService clientDirectory) {
    this.currentUsers = currentUsers;
    this.assignments = assignments;
    this.clientDirectory = clientDirectory;
  }

  @Override
  public ResponseEntity<com.chanakanlabs.bgstore.contract.model.ClientListResponse> listClients(
      @Nullable String search) {
    return ResponseEntity.ok(clientDirectory.list(search));
  }

  @Override
  public ResponseEntity<StaffBranchAssignmentList> listStaffBranchAssignments() {
    return ResponseEntity.ok(assignments.list());
  }

  @Override
  public ResponseEntity<StaffBranchAssignment> replaceStaffBranchAssignments(
      String staffSubject, ReplaceStaffBranchAssignmentsRequest request) {
    return ResponseEntity.ok(assignments.replace(staffSubject, request));
  }

  @Override
  public ResponseEntity<CurrentUserResponse> getCurrentUser() {
    return ResponseEntity.ok(toResponse(currentUsers.currentUser()));
  }

  @Override
  public ResponseEntity<ClientProfile> completeClientProfile(
      CompleteClientProfileRequest completeClientProfileRequest) {
    return ResponseEntity.ok(
        toResponse(
            currentUsers.completeClientProfile(
                completeClientProfileRequest.getFirstName(),
                completeClientProfileRequest.getLastName(),
                completeClientProfileRequest.getCountryCode(),
                completeClientProfileRequest.getPhoneNumber())));
  }

  private static CurrentUserResponse toResponse(CurrentUserService.CurrentUser currentUser) {
    var identity = currentUser.identity();
    List<ApplicationRole> roles =
        identity.roles().stream()
            .sorted()
            .map(role -> ApplicationRole.valueOf(role.name()))
            .toList();
    var profile = currentUser.clientProfile();
    String firstName =
        profileNameOrIdentity(profile == null ? null : profile.firstName(), identity.firstName());
    String lastName =
        profileNameOrIdentity(profile == null ? null : profile.lastName(), identity.lastName());
    var response =
        new CurrentUserResponse(
            identity.subject(),
            identity.username(),
            identity.email(),
            firstName,
            lastName,
            roles,
            currentUser.onboardingRequired());
    if (profile != null) {
      response.setClientProfile(toResponse(profile));
    }
    return response;
  }

  private static ClientProfile toResponse(ClientProfileData profile) {
    var response = new ClientProfile(profile.completed());
    response.setPhone(profile.phone());
    return response;
  }

  private static String profileNameOrIdentity(@Nullable String profileName, String identityName) {
    return profileName == null || profileName.isBlank() ? identityName : profileName;
  }
}
