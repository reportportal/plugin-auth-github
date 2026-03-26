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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents response from GET /user GitHub API.
 */
@Setter
class UserResource implements Serializable {

  @Getter
  @JsonProperty("login")
  private String login;

  @Getter
  @JsonProperty("email")
  private String email;

  @Getter
  @JsonProperty("name")
  private String name;

  @Getter
  @JsonProperty("avatar_url")
  private String avatarUrl;

  @Getter
  @JsonProperty("organizations_url")
  private String organizationsUrl;

  private Map<String, Object> details = new HashMap<>();

  @JsonAnyGetter
  public Map<String, Object> any() {
    return details;
  }

  @JsonAnySetter
  public void setUnknown(String name, Object value) {
    details.put(name, value);
  }

}
