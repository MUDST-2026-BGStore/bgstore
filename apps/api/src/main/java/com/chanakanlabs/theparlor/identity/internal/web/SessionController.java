package com.chanakanlabs.theparlor.identity.internal.web;

import com.chanakanlabs.theparlor.contracts.api.AuthenticationApi;
import com.chanakanlabs.theparlor.contracts.model.CsrfResponse;
import com.chanakanlabs.theparlor.contracts.model.IdentityProfile;
import com.chanakanlabs.theparlor.contracts.model.SessionResponse;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
class SessionController implements AuthenticationApi {

  private final CurrentUser currentUser;
  private final HttpServletRequest request;
  private final URI issuer;

  SessionController(
      CurrentUser currentUser,
      HttpServletRequest request,
      @Value("${theparlor.auth.issuer}") String issuer) {
    this.currentUser = currentUser;
    this.request = request;
    this.issuer = URI.create(issuer);
  }

  @GetMapping("/api/auth/login-locale")
  ResponseEntity<Void> loginLocale(
      @RequestParam String locale, @RequestParam("returnTo") String returnTo) {
    if (!locale.equals("en") && !locale.equals("th"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    URI target;
    try {
      target = URI.create(returnTo);
    } catch (IllegalArgumentException invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    // Compare the normalized path so "../" segments cannot leave the realm.
    if (!issuer.getScheme().equals(target.getScheme())
        || !issuer.getAuthority().equals(target.getAuthority())
        || target.getRawPath() == null
        || !target.getRawPath().equals(target.normalize().getRawPath())
        || !target.getPath().startsWith(issuer.getPath() + "/")
        || target.getRawFragment() != null
        || !locale.equals(
            UriComponentsBuilder.fromUri(target).build().getQueryParams().getFirst("kc_locale")))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    var session = request.getSession(false);
    if (session == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    // A short-lived display preference on the platform origin; contains no identity or tokens.
    var cookie =
        ResponseCookie.from("theparlor.loginLocale", locale.equals("th") ? "th-TH" : "en-US")
            .path("/")
            .sameSite("Lax")
            .secure(request.isSecure())
            .maxAge(3600)
            .build();
    return ResponseEntity.status(HttpStatus.FOUND)
        .header("Set-Cookie", cookie.toString())
        .location(target)
        .build();
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
