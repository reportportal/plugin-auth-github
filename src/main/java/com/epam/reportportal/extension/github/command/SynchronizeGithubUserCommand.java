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

package com.epam.reportportal.extension.github.command;

import com.epam.reportportal.api.model.PluginCommandRQ;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepositoryCustom;
import com.epam.reportportal.base.infrastructure.persistence.entity.user.UserRole;
import com.epam.reportportal.base.infrastructure.rules.commons.validation.BusinessRule;
import com.epam.reportportal.base.infrastructure.rules.exception.ErrorType;
import com.epam.reportportal.base.reporting.OperationCompletionRS;
import com.epam.reportportal.extension.command.AbstractExtensionCommand;
import com.epam.reportportal.extension.github.GitHubUserReplicator;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SynchronizeGithubUserCommand extends AbstractExtensionCommand<OperationCompletionRS> {

  private static final String COMMAND_NAME = "synchronize";
  private static final String ACCESS_TOKEN_PARAM = "access_token";

  private final GitHubUserReplicator replicator;

  public SynchronizeGithubUserCommand(GitHubUserReplicator replicator,
      ProjectRepository projectRepository, OrganizationRepositoryCustom organizationRepository) {
    super(projectRepository, organizationRepository);
    this.replicator = replicator;
    this.minUserRole = UserRole.USER;
  }

  @Override
  public String getName() {
    return COMMAND_NAME;
  }

  @Override
  protected OperationCompletionRS invokeCommand(PluginCommandRQ pluginCommandRq) {
    String accessToken = (String) pluginCommandRq.getArguments().get(ACCESS_TOKEN_PARAM);
    BusinessRule.expect(accessToken, Objects::nonNull)
        .verify(ErrorType.INCORRECT_AUTHENTICATION_TYPE, "Cannot synchronize GitHub User: access_token is missing");
    replicator.synchronizeUser(accessToken);
    return new OperationCompletionRS("User info successfully synchronized");
  }
}
