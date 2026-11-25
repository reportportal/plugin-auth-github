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

package com.epam.reportportal.extension.github.client;

import com.epam.reportportal.extension.github.model.EmailResource;
import com.epam.reportportal.extension.github.model.OrganizationResource;
import com.epam.reportportal.extension.github.model.UserResource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.web.client.RestClient;

/**
 * Simple GitHub API client.
 */
@Slf4j
public class GitHubClient {

  private static final String GITHUB_BASE_URL = "https://api.github.com";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  private final RestClient restClient;

  private GitHubClient(String accessToken) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    this.restClient = RestClient.builder()
        .requestFactory(requestFactory)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "bearer " + accessToken)
        .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
          String errorMessage =
              "Unable to load GitHub data: " + new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
          log.error(errorMessage);
          throw new AuthenticationServiceException(errorMessage);
        })
        .build();
  }

  public static GitHubClient withAccessToken(String accessToken) {
    log.debug("withAccessToken");
    return new GitHubClient(accessToken);
  }

  public UserResource getUser() {
    log.debug("getUser");
    return get(GITHUB_BASE_URL + "/user").body(UserResource.class);
  }

  public Map<String, Object> getUserAttributes() {
    log.debug("getUserAttributes");
    return get(GITHUB_BASE_URL + "/user").body(new ParameterizedTypeReference<>() {
    });
  }

  public List<EmailResource> getUserEmails() {
    log.debug("getUserEmails");
    return get(GITHUB_BASE_URL + "/user/emails").body(new ParameterizedTypeReference<>() {
    });
  }

  public List<OrganizationResource> getUserOrganizations(UserResource user) {
    log.debug("getUserOrganizations: login={}", user.getLogin());
    return get(user.getOrganizationsUrl()).body(new ParameterizedTypeReference<>() {
    });
  }

  public ResponseEntity<Resource> downloadResource(String url) {
    log.debug("downloadResource: url={}", url);
    return get(url).toEntity(Resource.class);
  }

  private RestClient.ResponseSpec get(String url) {
    log.debug("get: url={}", url);
    return this.restClient.get().uri(URI.create(url)).retrieve();
  }
}
