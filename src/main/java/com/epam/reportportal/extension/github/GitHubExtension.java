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

package com.epam.reportportal.extension.github;

import static com.epam.reportportal.auth.integration.converter.OAuthRegistrationConverters.FROM_SPRING_MERGE;
import static com.epam.reportportal.extension.github.oauth.GitHubOAuthProvider.PROVIDER_NAME;

import com.epam.reportportal.auth.event.UserEventPublisher;
import com.epam.reportportal.auth.model.settings.OAuthRegistrationResource;
import com.epam.reportportal.auth.oauth.OAuthProvider;
import com.epam.reportportal.base.infrastructure.commons.ContentTypeResolver;
import com.epam.reportportal.base.infrastructure.persistence.binary.UserBinaryDataService;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.UserRepository;
import com.epam.reportportal.base.infrastructure.persistence.entity.oauth.OAuthRegistration;
import com.epam.reportportal.base.infrastructure.persistence.util.PersonalProjectService;
import com.epam.reportportal.extension.AuthExtension;
import com.epam.reportportal.extension.CommonPluginCommand;
import com.epam.reportportal.extension.IntegrationGroupEnum;
import com.epam.reportportal.extension.PluginCommand;
import com.epam.reportportal.extension.github.command.SynchronizeGithubUserCommand;
import com.epam.reportportal.extension.github.oauth.GitHubOAuthProvider;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * GitHub OAuth2 authentication extension. Provides GitHub SSO as a PF4J plugin.
 */
@Extension
@Slf4j
public class GitHubExtension implements AuthExtension {

  private static final String CALL_BACK_URL = "{baseUrl}/sso/login/{registrationId}";
  private static final String PLUGIN_NAME = "GitHub OAuth Plugin";
  private static final String DOCUMENTATION_LINK = "https://reportportal.io/docs/plugins/authorization/GitHubAuthorization";
  private static final String DOCUMENTATION_LINK_FIELD = "documentationLink";
  private static final String NAME_FIELD = "name";

  private static final AuthenticationProvider NO_OP_AUTH_PROVIDER = new AuthenticationProvider() {
    @Override
    public Authentication authenticate(Authentication authentication) {
      return null;
    }

    @Override
    public boolean supports(Class<?> authentication) {
      return false;
    }
  };

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ProjectRepository projectRepository;

  @Autowired
  private PersonalProjectService personalProjectService;

  @Autowired
  private UserBinaryDataService userBinaryDataService;

  @Autowired
  private ContentTypeResolver contentTypeResolver;

  @Autowired
  private UserEventPublisher userEventPublisher;

  private GitHubUserReplicator replicator;
  private GitHubOAuthProvider oauthProvider;
  private Map<String, CommonPluginCommand<?>> commonCommands;

  @Autowired
  public GitHubExtension(Map<String, Object> initParams) {
    // initParams provided by PF4J at extension construction time
  }

  @PostConstruct
  public void init() {
    log.info("init");
    log.debug("Initializing GitHub OAuth extension");
    replicator = new GitHubUserReplicator(
        userRepository, projectRepository, personalProjectService,
        userBinaryDataService, contentTypeResolver, userEventPublisher
    );
    oauthProvider = new GitHubOAuthProvider(replicator);
    SynchronizeGithubUserCommand syncCommand = new SynchronizeGithubUserCommand(replicator);
    commonCommands = Map.of(syncCommand.getName(), syncCommand);
  }

  @Override
  public AuthenticationProvider getAuthenticationProvider() {
    log.info("getAuthenticationProvider");
    return NO_OP_AUTH_PROVIDER;
  }

  @Override
  public Optional<OAuthProvider> getOAuthProvider() {
    log.info("getOAuthProvider");
    return Optional.of(oauthProvider);
  }

  @Override
  public Optional<OAuthRegistration> fillOAuthRegistration(String oauthProviderId,
      OAuthRegistrationResource registrationResource, String pathValue) {
    log.info("fillOAuthRegistration: oauthProviderId={}", oauthProviderId);
    if (!PROVIDER_NAME.equals(oauthProviderId)) {
      return Optional.empty();
    }
    ClientRegistration springRegistration = CommonOAuth2Provider.GITHUB.getBuilder(oauthProviderId)
        .clientId(registrationResource.getClientId())
        .clientSecret(registrationResource.getClientSecret())
        .redirectUri(getCallBackUrl(pathValue))
        .scope("read:user", "user:email", "read:org")
        .clientName(oauthProviderId)
        .build();
    return Optional.of(FROM_SPRING_MERGE.apply(registrationResource, springRegistration));
  }

  @Override
  public Optional<Map<String, Object>> getAuthProviderInfo() {
    log.info("getAuthProviderInfo");
    return Optional.of(Map.of(
        "button", GitHubOAuthProvider.BUTTON_HTML,
        "path", "/oauth/login/" + PROVIDER_NAME
    ));
  }

  @Override
  public Map<String, ?> getPluginParams() {
    log.info("getPluginParams");
    Map<String, Object> params = new HashMap<>();
    params.put(NAME_FIELD, PLUGIN_NAME);
    params.put(DOCUMENTATION_LINK_FIELD, DOCUMENTATION_LINK);
    params.put(ALLOWED_COMMANDS, new ArrayList<>());
    params.put(COMMON_COMMANDS, new ArrayList<>());
    return params;
  }

  @Override
  public CommonPluginCommand<?> getCommonCommand(String commandName) {
    log.info("getCommonCommand: commandName={}", commandName);
    return commonCommands.get(commandName);
  }

  @Override
  public PluginCommand<?> getIntegrationCommand(String commandName) {
    log.info("getIntegrationCommand: commandName={}", commandName);
    return null;
  }

  @Override
  public IntegrationGroupEnum getIntegrationGroup() {
    log.info("getIntegrationGroup");
    return IntegrationGroupEnum.AUTH;
  }

  private static String getCallBackUrl(String pathValue) {
    return StringUtils.isEmpty(pathValue) || pathValue.equals("/") ?
        CALL_BACK_URL.replaceFirst("baseUrl}/", "baseUrl}/api/") :
        CALL_BACK_URL;
  }
}
