package com.chanakanlabs.theparlor.identity.internal.web;

import com.chanakanlabs.theparlor.contracts.api.AuthenticationApi;
import com.chanakanlabs.theparlor.contracts.model.CsrfResponse;
import com.chanakanlabs.theparlor.contracts.model.IdentityProfile;
import com.chanakanlabs.theparlor.contracts.model.SessionResponse;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessionController implements AuthenticationApi {

  private final CurrentUser currentUser;
  private final HttpServletRequest request;

  SessionController(CurrentUser currentUser, HttpServletRequest request) {
    this.currentUser = currentUser;
    this.request = request;
  }

  @Override
  public SessionResponse session() {
    return currentUser
        .find()
        .map(
            user ->
                new SessionResponse(true)
                    .profile(
                        new IdentityProfile(
                                user.subject(), user.username(), user.emailVerified(), user.roles())
                            .displayName(user.displayName())
                            .email(user.email())))
        .orElseGet(() -> new SessionResponse(false));
  }

  @Override
  public CsrfResponse csrf() {
    var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

    return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
  }
}
