package com.chanakanlabs.bgstore.branches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.contract.model.CreateBranchRequest;
import com.chanakanlabs.bgstore.identity.AccessPolicy;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BranchControllerTest {

  @Mock private BranchDirectory directory;
  @Mock private BranchManagementService management;
  @Mock private AccessPolicy accessPolicy;

  private BranchController controller;

  @BeforeEach
  void setUp() {
    controller = new BranchController(directory, management, accessPolicy);
  }

  @Test
  void listsBranchesWithTheirPublishedDetails() {
    UUID id = UUID.randomUUID();
    when(accessPolicy.canAccessBranch(id)).thenReturn(true);
    when(directory.findAll())
        .thenReturn(
            List.of(
                new Branch(
                    id,
                    "Central",
                    "Rama II",
                    LocalTime.of(9, 0),
                    LocalTime.of(19, 0),
                    "+6621234567",
                    BranchStatus.INACTIVE,
                    13.654321,
                    100.501765)));

    var response = controller.listBranches();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getItems())
        .singleElement()
        .satisfies(
            branch -> {
              assertThat(branch.getName()).isEqualTo("Central");
              assertThat(branch.getAddress()).isEqualTo("Rama II");
              assertThat(branch.getOpensAt()).isEqualTo("09:00");
              assertThat(branch.getClosesAt()).isEqualTo("19:00");
              assertThat(branch.getPhone()).isEqualTo("+6621234567");
              assertThat(branch.getStatus().getValue()).isEqualTo("INACTIVE");
              assertThat(branch.getLatitude()).isEqualTo(13.654321);
              assertThat(branch.getLongitude()).isEqualTo(100.501765);
            });
  }

  @Test
  void createsBranchThroughTheManagerCommand() {
    UUID id = UUID.randomUUID();
    var request = new CreateBranchRequest(" New branch ");
    request.setAddress("  Sukhumvit  ");
    request.setOpensAt("09:00");
    request.setClosesAt("19:00");
    when(management.createBranch(
            " New branch ", "  Sukhumvit  ", LocalTime.of(9, 0), LocalTime.of(19, 0)))
        .thenReturn(
            new Branch(id, "New branch", "Sukhumvit", LocalTime.of(9, 0), LocalTime.of(19, 0)));

    var response = controller.createBranch(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getId()).isEqualTo(id);
    verify(management)
        .createBranch(" New branch ", "  Sukhumvit  ", LocalTime.of(9, 0), LocalTime.of(19, 0));
  }
}
