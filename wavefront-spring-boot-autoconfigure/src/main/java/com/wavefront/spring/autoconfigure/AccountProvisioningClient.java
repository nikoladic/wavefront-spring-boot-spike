/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wavefront.spring.autoconfigure;

import java.net.URI;
import java.time.Duration;

import org.apache.commons.logging.Log;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Negotiate a Wavefront api token based on an {@link ApplicationInfo}.
 *
 * @author Stephane Nicoll
 */
class AccountProvisioningClient {

	private final Log logger;

	private final RestTemplate restTemplate;

	AccountProvisioningClient(Log logger, RestTemplateBuilder restTemplateBuilder) {
		this.logger = logger;
		this.restTemplate = restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(10))
				.setReadTimeout(Duration.ofSeconds(10)).build();
	}

	String provisionAccount(ApplicationInfo applicationInfo) {
		UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString("https://wavefront.surf")
				.path("/api/v2/trial/spring-boot-autoconfigure").queryParam("application", applicationInfo.getName())
				.queryParam("service", applicationInfo.getService());
		if (applicationInfo.getCluster() != null) {
			uriComponentsBuilder.queryParam("cluster", applicationInfo.getCluster());
		}
		if (applicationInfo.getShard() != null) {
			uriComponentsBuilder.queryParam("shard", applicationInfo.getShard());
		}
		URI requestUri = uriComponentsBuilder.build().toUri();
		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Auto-negotiating Wavefront credentials from: " + requestUri);
		}
		ResponseEntity<String> response = this.restTemplate.postForEntity(requestUri, null, String.class);
		// TODO
		return response.getBody();
	}

}
