package com.epam.reportportal.extension.github.service;

import com.epam.reportportal.auth.integration.validator.request.param.provider.ParamNamesProvider;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class GitHubRequiredParamNamesProvider implements ParamNamesProvider {

  @Override
  public List<String> provide() {
    return Collections.emptyList();
  }
}
