package com.chanakanlabs.theparlor.wiring.internal.web;

import com.chanakanlabs.theparlor.contracts.api.WiringApi;
import com.chanakanlabs.theparlor.contracts.model.HelloResponse;
import com.chanakanlabs.theparlor.contracts.model.WhoamiResponse;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WiringController implements WiringApi {
  private final CurrentUser currentUser;

  WiringController(CurrentUser currentUser) {
    this.currentUser = currentUser;
  }

  @Override
  public HelloResponse hello() {
    return new HelloResponse("theparlor-api", "ok");
  }

  @Override
  public WhoamiResponse whoami() {
    var profile = currentUser.require();
    return new WhoamiResponse(profile.username(), profile.roles());
  }
}
