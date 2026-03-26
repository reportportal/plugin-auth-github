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
import com.epam.reportportal.base.infrastructure.rules.commons.validation.BusinessRule;
import com.epam.reportportal.base.infrastructure.rules.exception.ErrorType;
import com.epam.reportportal.base.reporting.OperationCompletionRS;
import com.epam.reportportal.extension.github.GitHubUserReplicator;
import com.epam.reportportal.extension.role.AuthenticatedUserContextCommand;
import java.util.Objects;

/**
 * Command to synchronize GitHub user information.
 *
 * @author <a href="mailto:andrei_varabyeu@epam.com">Andrei Varabyeu</a>
 */
public class SynchronizeGithubUserCommand extends AuthenticatedUserContextCommand {

  private static final String COMMAND_NAME = "synchronize";
  private static final String ACCESS_TOKEN_PARAM = "access_token";

  private final GitHubUserReplicator replicator;

  /**
   * Instantiates a new Synchronize GitHub user command.
   *
   * @param replicator the replicator
   */
  public SynchronizeGithubUserCommand(GitHubUserReplicator replicator) {
    this.replicator = replicator;
  }

  @Override
  public String getName() {
    return COMMAND_NAME;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Synchronizes GitHub user information using the provided access token.
   *
   * @param pluginCommandRq the plugin command rq
   * @return the operation completion rs
   */
  @Override
  protected OperationCompletionRS invokeCommand(PluginCommandRQ pluginCommandRq) {
    String accessToken = (String) pluginCommandRq.getArguments().get(ACCESS_TOKEN_PARAM);
    BusinessRule.expect(accessToken, Objects::nonNull)
        .verify(ErrorType.INCORRECT_AUTHENTICATION_TYPE, "Cannot synchronize GitHub User: access_token is missing");
    replicator.synchronizeUser(accessToken);
    return new OperationCompletionRS("User info successfully synchronized");
  }
}
