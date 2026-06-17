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
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationTypeRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.UserRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.entity.enums.IntegrationAuthFlowEnum;
import com.epam.reportportal.extension.AuthExtension;
import com.epam.reportportal.extension.CommonPluginCommand;
import com.epam.reportportal.extension.IntegrationGroupEnum;
import com.epam.reportportal.extension.PluginCommand;
import com.epam.reportportal.extension.command.ExtensionCommand;
import com.epam.reportportal.extension.github.command.SynchronizeGithubUserCommand;
import com.epam.reportportal.extension.github.event.listener.PluginLoadedEventListener;
import com.epam.reportportal.extension.github.oauth.GitHubOAuthProvider;
import com.epam.reportportal.extension.github.service.GitHubIntegrationStrategy;
import com.epam.reportportal.extension.github.service.GitHubRequiredParamNamesProvider;
import com.epam.reportportal.extension.github.utils.MemoizingSupplier;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import com.epam.reportportal.base.core.integration.util.IntegrationParamsEncryptor;
import org.pf4j.Extension;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;

/**
 * GitHub OAuth2 authentication extension. Provides GitHub SSO as a PF4J plugin.
 */
@Extension
@Slf4j
public class GitHubExtension implements AuthExtension, DisposableBean {

  public static final String SSO_LOGIN_PATH = "/oauth/login";
  public static final String SCHEMA_SCRIPTS_DIR = "resources/schema";

  private static final String PLUGIN_ID = "github";
  private static final String PLUGIN_NAME = "GitHub OAuth";
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
  private ApplicationContext applicationContext;

  @Autowired
  private IntegrationTypeRepository integrationTypeRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private OrganizationUserRepository organizationUserRepository;
  @Autowired
  private ProjectUserRepository projectUserRepository;

  @Autowired
  private ProjectRepository projectRepository;

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
  private OrganizationRepository organizationRepository;

  @Autowired
  private IntegrationParamsEncryptor paramsEncryptor;

  @Autowired
  private DataSource dataSource;

  private GitHubUserReplicator replicator;
  private GitHubOAuthProvider oauthProvider;
  private Map<String, ExtensionCommand<?>> commonCommands;

  private Supplier<GitHubIntegrationStrategy> gitHubIntegrationStrategySupplier;
  private Supplier<PluginLoadedEventListener> pluginLoadedListenerSupplier;


  @PostConstruct
  public void init() throws IOException {
    log.info("Initializing GitHub OAuth extension");
    this.gitHubIntegrationStrategySupplier = new MemoizingSupplier<>(
        () -> new GitHubIntegrationStrategy(integrationRepository,
            new UpdateAuthRequestValidator(new GitHubRequiredParamNamesProvider()), integrationDuplicateValidator,
            paramsEncryptor));

    this.pluginLoadedListenerSupplier = new MemoizingSupplier<>(
        () -> new PluginLoadedEventListener(PLUGIN_ID, integrationTypeRepository, integrationRepository,
            integrationType -> integrationType, dataSource));

    replicator = new GitHubUserReplicator(
        userRepository, userBinaryDataService, contentTypeResolver, userEventPublisher
    );
    oauthProvider = new GitHubOAuthProvider(replicator);
    SynchronizeGithubUserCommand syncCommand = new SynchronizeGithubUserCommand(replicator, projectRepository,
        organizationRepository,
        organizationUserRepository, projectUserRepository);
    commonCommands = Map.of(syncCommand.getName(), syncCommand);

    initListeners();
  }

  private void initListeners() {
    ApplicationEventMulticaster applicationEventMulticaster = applicationContext.getBean(
        AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
        ApplicationEventMulticaster.class
    );
    applicationEventMulticaster.addApplicationListener(pluginLoadedListenerSupplier.get());
  }

  @Override
  public void destroy() {
    ApplicationEventMulticaster applicationEventMulticaster = applicationContext.getBean(
        AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
        ApplicationEventMulticaster.class
    );
    applicationEventMulticaster.removeApplicationListener(pluginLoadedListenerSupplier.get());
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
    return getAuthIntegrationType()
        .filter(type -> !integrationRepository.findAllByTypeIn(type).isEmpty())
        .map(_ -> Map.of(
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
    return null;
  }

  @Override
  public Map<String, ExtensionCommand<?>> getCommonExtensionCommands() {
    return commonCommands;
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

  @Override
  public Optional<IntegrationAuthFlowEnum> getAuthFlow() {
    return Optional.of(IntegrationAuthFlowEnum.OAUTH);
  }

}
