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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * An {@link EnvironmentPostProcessor} that auto-negotiates an api token for Wavefront if
 * necessary.
 *
 * @author Stephane Nicoll
 */
class AccountProvisioningEnvironmentPostProcessor
		implements EnvironmentPostProcessor, ApplicationListener<ApplicationPreparedEvent> {

	private static final String WAVEFRONT_PROPERTIES_PREFIX = "management.metrics.export.wavefront.";

	private final DeferredLog logger = new DeferredLog();

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String apiToken = environment.getProperty(WAVEFRONT_PROPERTIES_PREFIX + "api-token");
		if (StringUtils.hasText(apiToken)) {
			this.logger.debug("Wavefront api token already set, no need to auto-negotiate one");
			return;
		}
		Resource localApiTokenResource = getLocalApiTokenResource();
		String existingApiToken = readExistingApiToken(localApiTokenResource);
		if (existingApiToken != null) {
			this.logger.debug("Existing Wavefront api token found from " + localApiTokenResource);
			registerApiToken(environment, existingApiToken);
		}
		else {
			AccountInfo accountInfo = autoNegotiateAccount(environment);
			registerApiToken(environment, accountInfo.getApiToken());
			writeApiTokenToDisk(localApiTokenResource, accountInfo.getApiToken());
		}
	}

	@Override
	public void onApplicationEvent(ApplicationPreparedEvent event) {
		this.logger.switchTo(AccountProvisioningEnvironmentPostProcessor.class);
	}

	private String readExistingApiToken(Resource localApiTokenResource) {
		if (localApiTokenResource.isReadable()) {
			try (InputStream in = localApiTokenResource.getInputStream()) {
				return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				this.logger.error("Failed to read wavefront token from " + localApiTokenResource, ex);
			}
		}
		return null;
	}

	private void writeApiTokenToDisk(Resource localApiTokenResource, String apiToken) {
		if (localApiTokenResource.isFile()) {
			try (OutputStream out = new FileOutputStream(localApiTokenResource.getFile())) {
				StreamUtils.copy(apiToken, StandardCharsets.UTF_8, out);
			}
			catch (IOException ex) {
				throw new IllegalStateException(ex);
			}
		}
	}

	private AccountInfo autoNegotiateAccount(ConfigurableEnvironment environment) {
		String clusterUri = environment.getProperty(WAVEFRONT_PROPERTIES_PREFIX + "uri", "https://wavefront.surf");
		RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
		AccountProvisioningClient client = new AccountProvisioningClient(this.logger, restTemplateBuilder);
		ApplicationInfo applicationInfo = new ApplicationInfo(environment);
		return provisionAccount(client, clusterUri, applicationInfo);
	}

	private void registerApiToken(ConfigurableEnvironment environment, String apiToken) {
		MapPropertySource wavefrontPropertySource = new MapPropertySource("wavefront",
				Collections.singletonMap(WAVEFRONT_PROPERTIES_PREFIX + "api-token", apiToken));
		environment.getPropertySources().addLast(wavefrontPropertySource);
	}

	protected Resource getLocalApiTokenResource() {
		return new PathResource(Paths.get(System.getProperty("user.home"), ".wavefront-token"));
	}

	protected AccountInfo provisionAccount(AccountProvisioningClient client, String clusterUri,
			ApplicationInfo applicationInfo) {
		return client.provisionAccount(clusterUri, applicationInfo);
	}

}
