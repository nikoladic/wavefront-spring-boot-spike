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

package com.wavefront.spring.autoconfigure.account;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.apache.commons.logging.Log;

import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Negotiate a Wavefront {@linkplain AccountInfo account} based on an
 * {@link ApplicationInfo}.
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

	/**
	 * Provision an account for the specified Wavefront cluster and application
	 * information.
	 * @param clusterUri the URI of the Wavefront cluster
	 * @param applicationInfo the {@link ApplicationInfo} to use
	 * @return the provisioned account
	 * @throws AccountProvisioningFailedException if the cluster does not support freemium
	 * accounts
	 */
	AccountInfo provisionAccount(String clusterUri, ApplicationInfo applicationInfo) {
		UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(clusterUri)
				.path("/api/v2/trial/spring-boot-autoconfigure").queryParam("application", applicationInfo.getName())
				.queryParam("service", applicationInfo.getService());
		if (applicationInfo.getCluster() != null) {
			uriComponentsBuilder.queryParam("cluster", applicationInfo.getCluster());
		}
		if (applicationInfo.getShard() != null) {
			uriComponentsBuilder.queryParam("shard", applicationInfo.getShard());
		}
		URI requestUri = uriComponentsBuilder.build().toUri();
		this.logger.debug("Auto-negotiating Wavefront credentials from: " + requestUri);
		try {
			String json = this.restTemplate.postForObject(requestUri, null, String.class);
			Map<String, Object> content = new BasicJsonParser().parseMap(json);
			return new AccountInfo((String) content.get("token"), (String) content.get("url"));
		}
		catch (HttpClientErrorException ex) {
			throw new AccountProvisioningFailedException(ex.getResponseBodyAsString());
		}
	}

}
