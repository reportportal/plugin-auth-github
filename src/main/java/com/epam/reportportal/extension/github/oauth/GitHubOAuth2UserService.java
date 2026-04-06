/*
 * Copyright 2026 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.extension.github.oauth;

import static com.epam.reportportal.extension.github.oauth.GitHubOAuthProvider.PROVIDER_NAME;

import com.epam.reportportal.auth.model.OAuthRegistrationResource;
import com.epam.reportportal.auth.oauth.RPOAuth2User;
import com.epam.reportportal.base.infrastructure.persistence.commons.ReportPortalUser;
import com.epam.reportportal.extension.github.GitHubUserReplicator;
import com.epam.reportportal.extension.github.client.GitHubClient;
import com.epam.reportportal.extension.github.model.OrganizationResource;
import com.epam.reportportal.extension.github.model.UserResource;
import com.google.common.base.Splitter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * OAuth2 user service for GitHub login. Loads the GitHub user and replicates them into ReportPortal.
 */
@Slf4j
public class GitHubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  private final GitHubUserReplicator replicator;
  private final Supplier<OAuthRegistrationResource> oAuthRegistrationSupplier;

  public GitHubOAuth2UserService(GitHubUserReplicator replicator,
      Supplier<OAuthRegistrationResource> oAuthRegistrationSupplier) {
    this.replicator = replicator;
    this.oAuthRegistrationSupplier = oAuthRegistrationSupplier;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    log.info("loadUser: registrationId={}", userRequest.getClientRegistration().getRegistrationId());
    if (!userRequest.getClientRegistration().getRegistrationId().equals(PROVIDER_NAME)) {
      return null;
    }
    String accessToken = userRequest.getAccessToken().getTokenValue();

    GitHubClient gitHubClient = GitHubClient.withAccessToken(accessToken);
    UserResource gitHubUser = gitHubClient.getUser();

    List<String> allowedOrganizations = parseAllowedOrganizations(oAuthRegistrationSupplier.get());
    if (!allowedOrganizations.isEmpty()) {
      validateUserOrganizations(gitHubUser, gitHubClient, allowedOrganizations);
    }

    ReportPortalUser user = replicator.replicateUser(gitHubUser, gitHubClient);
    return new RPOAuth2User(user, accessToken);
  }

  private List<String> parseAllowedOrganizations(OAuthRegistrationResource registration) {
    return Optional.ofNullable(registration.getRestrictions())
        .map(restrictions -> restrictions.get("organizations"))
        .map(orgs -> Splitter.on(',').omitEmptyStrings().splitToList(orgs))
        .orElse(Collections.emptyList());
  }

  private void validateUserOrganizations(UserResource user, GitHubClient client,
      List<String> allowedOrgs) {
    boolean hasAccess = client.getUserOrganizations(user)
        .stream()
        .map(OrganizationResource::getLogin)
        .anyMatch(allowedOrgs::contains);

    if (!hasAccess) {
      throw new OAuth2AuthenticationException(new OAuth2Error("access_denied",
          "User '" + user.getLogin() + "' does not belong to allowed GitHub organization", null));
    }
  }
}
