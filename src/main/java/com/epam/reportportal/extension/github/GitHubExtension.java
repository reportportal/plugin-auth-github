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

import static com.epam.reportportal.extension.github.oauth.GitHubOAuthProvider.PROVIDER_NAME;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath;

import com.epam.reportportal.auth.event.UserEventPublisher;
import com.epam.reportportal.auth.integration.handler.impl.strategy.AuthIntegrationStrategy;
import com.epam.reportportal.auth.integration.validator.duplicate.IntegrationDuplicateValidator;
import com.epam.reportportal.auth.integration.validator.request.UpdateAuthRequestValidator;
import com.epam.reportportal.auth.oauth.OAuthProvider;
import com.epam.reportportal.base.infrastructure.commons.ContentTypeResolver;
import com.epam.reportportal.base.infrastructure.persistence.binary.UserBinaryDataService;
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.UserRepository;
import com.epam.reportportal.base.infrastructure.persistence.util.PersonalProjectService;
import com.epam.reportportal.extension.AuthExtension;
import com.epam.reportportal.extension.CommonPluginCommand;
import com.epam.reportportal.extension.IntegrationGroupEnum;
import com.epam.reportportal.extension.PluginCommand;
import com.epam.reportportal.extension.github.command.SynchronizeGithubUserCommand;
import com.epam.reportportal.extension.github.oauth.GitHubOAuthProvider;
import com.epam.reportportal.extension.github.service.GitHubIntegrationStrategy;
import com.epam.reportportal.extension.github.service.GitHubRequiredParamNamesProvider;
import com.epam.reportportal.extension.github.utils.MemoizingSupplier;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.util.text.BasicTextEncryptor;
import org.pf4j.Extension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;

/**
 * GitHub OAuth2 authentication extension. Provides GitHub SSO as a PF4J plugin.
 */
@Extension
@Slf4j
public class GitHubExtension implements AuthExtension {

  public static final String SSO_LOGIN_PATH = "/oauth/login";
  public static final String SCHEMA_SCRIPTS_DIR = "schema";

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
  IntegrationRepository integrationRepository;

  @Autowired
  private UserEventPublisher userEventPublisher;

  @Autowired
  private IntegrationDuplicateValidator integrationDuplicateValidator;

  @Autowired
  private BasicTextEncryptor encryptor;

  private GitHubUserReplicator replicator;
  private GitHubOAuthProvider oauthProvider;
  private Map<String, CommonPluginCommand<?>> commonCommands;

  private Supplier<GitHubIntegrationStrategy> gitHubIntegrationStrategySupplier;


  @Autowired
  public GitHubExtension(Map<String, Object> initParams) {
    // initParams provided by PF4J at extension construction time
  }

  @PostConstruct
  public void init() {
    log.info("Initializing GitHub OAuth extension");
    this.gitHubIntegrationStrategySupplier = new MemoizingSupplier<>(
        () -> new GitHubIntegrationStrategy(integrationRepository,
            new UpdateAuthRequestValidator(new GitHubRequiredParamNamesProvider()), integrationDuplicateValidator,
            encryptor));

    replicator = new GitHubUserReplicator(
        userRepository, projectRepository, personalProjectService,
        userBinaryDataService, contentTypeResolver, userEventPublisher
    );
    oauthProvider = new GitHubOAuthProvider(replicator);
    SynchronizeGithubUserCommand syncCommand = new SynchronizeGithubUserCommand(replicator);
    commonCommands = Map.of(syncCommand.getName(), syncCommand);

/*    initApplicationListeners();
    initSchema();*/
  }

  @Override
  public AuthenticationProvider getAuthenticationProvider() {
    return NO_OP_AUTH_PROVIDER;
  }

  @Override
  public Optional<OAuthProvider> getOAuthProvider() {
    return Optional.of(oauthProvider);
  }

  @Override
  public Optional<String> getAuthIntegrationType() {
    return Optional.of("github");
  }


  @Override
  public Optional<Map<String, Object>> getAuthProviderInfo() {
    return Optional.of(Map.of(
        "button", GitHubOAuthProvider.BUTTON_HTML,
        "path", getAuthBasePath() + "/" + PROVIDER_NAME
    ));
  }

  @Override
  public Map<String, ?> getPluginParams() {
    Map<String, Object> params = new HashMap<>();
    params.put(NAME_FIELD, PLUGIN_NAME);
    params.put(DOCUMENTATION_LINK_FIELD, DOCUMENTATION_LINK);
    params.put(ALLOWED_COMMANDS, new ArrayList<>());
    params.put(COMMON_COMMANDS, new ArrayList<>());
    return params;
  }

  @Override
  public CommonPluginCommand<?> getCommonCommand(String commandName) {
    return commonCommands.get(commandName);
  }

  @Override
  public PluginCommand<?> getIntegrationCommand(String commandName) {
    return null;
  }

  @Override
  public IntegrationGroupEnum getIntegrationGroup() {
    return IntegrationGroupEnum.AUTH;
  }


  private String getAuthBasePath() {
    return fromCurrentContextPath().path(SSO_LOGIN_PATH).build().getPath();
  }

  @Override
  public Optional<AuthIntegrationStrategy> getStrategy() {
    return Optional.of(gitHubIntegrationStrategySupplier.get());
  }

}
