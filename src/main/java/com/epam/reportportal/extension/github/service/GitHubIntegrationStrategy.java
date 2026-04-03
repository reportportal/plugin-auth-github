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

package com.epam.reportportal.extension.github.service;

import static com.epam.reportportal.extension.github.model.RegistrationParam.AUTHORIZATION_URI;
import static com.epam.reportportal.extension.github.model.RegistrationParam.AUTH_GRANT_TYPE;
import static com.epam.reportportal.extension.github.model.RegistrationParam.CLIENT_AUTH_METHOD;
import static com.epam.reportportal.extension.github.model.RegistrationParam.CLIENT_ID;
import static com.epam.reportportal.extension.github.model.RegistrationParam.CLIENT_NAME;
import static com.epam.reportportal.extension.github.model.RegistrationParam.CLIENT_SECRET;
import static com.epam.reportportal.extension.github.model.RegistrationParam.JWK_SET_URI;
import static com.epam.reportportal.extension.github.model.RegistrationParam.REDIRECT_URI_TEMPLATE;
import static com.epam.reportportal.extension.github.model.RegistrationParam.REDIRECT_URL_TEMPLATE;
import static com.epam.reportportal.extension.github.model.RegistrationParam.RESTRICTIONS;
import static com.epam.reportportal.extension.github.model.RegistrationParam.SCOPES;
import static com.epam.reportportal.extension.github.model.RegistrationParam.TOKEN_URI;
import static com.epam.reportportal.extension.github.model.RegistrationParam.USER_INFO_ENDPOINT_NAME_ATTR;
import static com.epam.reportportal.extension.github.model.RegistrationParam.USER_INFO_ENDPOINT_NAME_ATTRIBUTE;
import static com.epam.reportportal.extension.github.model.RegistrationParam.USER_INFO_ENDPOINT_URI;
import static java.util.Optional.ofNullable;

import com.epam.reportportal.auth.integration.handler.impl.strategy.AuthIntegrationStrategy;
import com.epam.reportportal.auth.integration.validator.duplicate.IntegrationDuplicateValidator;
import com.epam.reportportal.auth.integration.validator.request.AuthRequestValidator;
import com.epam.reportportal.auth.model.AbstractAuthResource;
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationRepository;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.Integration;
import com.epam.reportportal.base.model.integration.IntegrationRQ;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jasypt.util.text.BasicTextEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Service;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
@Slf4j
@Service
public class GitHubIntegrationStrategy extends AuthIntegrationStrategy {

  private static final String CALL_BACK_URL = "{baseUrl}/sso/login/{registrationId}";
  private static final String ORGANIZATION_TYPE = "organization";
  private static final String ORGANIZATIONS_KEY = "organizations";

  @Value("${server.servlet.context-path}")
  private String pathValue;

  public GitHubIntegrationStrategy(IntegrationRepository integrationRepository,
      AuthRequestValidator<IntegrationRQ> updateAuthRequestValidator,
      IntegrationDuplicateValidator integrationDuplicateValidator,
      BasicTextEncryptor encryptor) {
    super(integrationRepository, updateAuthRequestValidator, integrationDuplicateValidator);
  }

  @Override
  protected void populateIntegrationDetails(Integration integration, IntegrationRQ integrationRq) {
    integration.setName(integrationRq.getName());

    var params = integrationRq.getIntegrationParams();

    ClientRegistration springRegistration = CommonOAuth2Provider.GITHUB.getBuilder(integration.getType().getName())
        .clientId((String) integrationRq.getIntegrationParams().get(CLIENT_ID))
        .clientSecret((String) integrationRq.getIntegrationParams().get(CLIENT_SECRET))
        .redirectUri(getCallBackUrl(pathValue))
        .scope("read:user", "user:email", "read:org")
        .clientName(integration.getType().getName())
        .build();

    getGithubRegistrationParams(integration, params, springRegistration);

  }

  @Override
  public AbstractAuthResource toResource(Integration integration) {
    return null;
  }

  private static void getGithubRegistrationParams(Integration integration, Map<String, Object> params,
      ClientRegistration springRegistration) {
    var integrationParams = integration.getParams().getParams();
    integrationParams.put(CLIENT_ID, params.get(CLIENT_ID));
    integrationParams.put(CLIENT_SECRET, params.get(CLIENT_SECRET));
    integrationParams.put(CLIENT_AUTH_METHOD, ofNullable((String) params.get(CLIENT_AUTH_METHOD))
        .orElseGet(() -> springRegistration.getClientAuthenticationMethod().getValue()));
    integrationParams.put(CLIENT_NAME, ofNullable((String) params.get(CLIENT_NAME))
        .orElseGet(springRegistration::getClientName));
    integrationParams.put(AUTH_GRANT_TYPE, ofNullable((String) params.get(AUTH_GRANT_TYPE))
        .orElseGet(() -> springRegistration.getAuthorizationGrantType().getValue()));
    integrationParams.put(REDIRECT_URI_TEMPLATE, ofNullable((String) params.get(REDIRECT_URL_TEMPLATE))
        .orElseGet(springRegistration::getRedirectUri));
    integrationParams.put(SCOPES, ofNullable(params.get(SCOPES))
        .map(s -> new ArrayList<>((java.util.Set<String>) s))
        .orElseGet(() -> new ArrayList<>(springRegistration.getScopes())));

    ClientRegistration.ProviderDetails details = springRegistration.getProviderDetails();
    integrationParams.put(AUTHORIZATION_URI, ofNullable((String) params.get(AUTHORIZATION_URI))
        .orElseGet(details::getAuthorizationUri));
    integrationParams.put(TOKEN_URI, ofNullable((String) params.get(TOKEN_URI))
        .orElseGet(details::getTokenUri));
    integrationParams.put(USER_INFO_ENDPOINT_URI, ofNullable((String) params.get(USER_INFO_ENDPOINT_URI))
        .orElseGet(() -> details.getUserInfoEndpoint().getUri()));
    integrationParams.put(USER_INFO_ENDPOINT_NAME_ATTR,
        ofNullable((String) params.get(USER_INFO_ENDPOINT_NAME_ATTRIBUTE))
            .orElseGet(() -> details.getUserInfoEndpoint().getUserNameAttributeName()));
    integrationParams.put(JWK_SET_URI, ofNullable((String) params.get(JWK_SET_URI))
        .orElseGet(details::getJwkSetUri));

    integrationParams.put(RESTRICTIONS, buildRestrictions((Map<String, String>) params.get(RESTRICTIONS)));
  }


  private static List<Map<String, Object>> buildRestrictions(Map<String, String> restrictions) {
    String organizations = ofNullable(restrictions)
        .map(r -> r.get(ORGANIZATIONS_KEY))
        .orElse("");

    if (StringUtils.isBlank(organizations)) {
      return Collections.emptyList();
    }

    return Arrays.stream(organizations.split(","))
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .map(org -> {
          Map<String, Object> restriction = new HashMap<>();
          restriction.put("type", ORGANIZATION_TYPE);
          restriction.put("value", org);
          return restriction;
        })
        .collect(Collectors.toList());
  }

  private static String getCallBackUrl(String pathValue) {
    return StringUtils.isEmpty(pathValue) || pathValue.equals("/") ?
        CALL_BACK_URL.replaceFirst("baseUrl}/", "baseUrl}/api/") :
        CALL_BACK_URL;
  }

}
