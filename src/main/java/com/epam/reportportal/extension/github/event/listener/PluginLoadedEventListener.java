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

package com.epam.reportportal.extension.github.event.listener;

import static com.epam.reportportal.extension.github.GitHubExtension.SCHEMA_SCRIPTS_DIR;

import com.epam.reportportal.base.core.events.domain.PluginUploadedEvent;
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationTypeRepository;
import com.epam.reportportal.extension.github.info.PluginInfoProvider;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author Andrei Piankouski
 */
@Slf4j
public class PluginLoadedEventListener implements ApplicationListener<PluginUploadedEvent> {

  private final String pluginId;
  private final IntegrationTypeRepository integrationTypeRepository;
  private final IntegrationRepository integrationRepository;
  private final PluginInfoProvider pluginInfoProvider;
  private final DataSource dataSource;

  public PluginLoadedEventListener(String pluginId,
      IntegrationTypeRepository integrationTypeRepository,
      IntegrationRepository integrationRepository, PluginInfoProvider pluginInfoProvider, DataSource dataSource) {
    this.pluginId = pluginId;
    this.integrationTypeRepository = integrationTypeRepository;
    this.integrationRepository = integrationRepository;
    this.pluginInfoProvider = pluginInfoProvider;
    this.dataSource = dataSource;
  }

  @Override
  public void onApplicationEvent(PluginUploadedEvent event) {
    if (!supports(event)) {
      return;
    }
    initSchema();
  }

  private boolean supports(PluginUploadedEvent event) {
    return Objects.nonNull(event.getPluginActivityResource())
        && pluginId.equals(event.getPluginActivityResource().getName());
  }

  @SneakyThrows
  public void initSchema() {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());
    Resource[] resources = resolver.getResources("classpath:" + SCHEMA_SCRIPTS_DIR + "/*.sql");
    log.debug("GitHub schema init: found {} script(s)", resources.length);
    if (resources.length == 0) {
      return;
    }
    Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    for (Resource r : resources) {
      log.info("GitHub schema init: executing {}", r.getFilename());
      try (InputStream is = r.getInputStream()) {
        String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
      }
    }
    log.info("GitHub schema init: completed");
  }

}
