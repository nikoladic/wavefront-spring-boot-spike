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

	static final String API_TOKEN_PROPERTY = "management.metrics.export.wavefront.api-token";

	static final String URI_PROPERTY = "management.metrics.export.wavefront.uri";

	private final DeferredLog logger = new DeferredLog();

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String apiToken = environment.getProperty(API_TOKEN_PROPERTY);
		if (StringUtils.hasText(apiToken)) {
			this.logger.debug("Wavefront api token already set, no need to negotiate one");
			return;
		}
		Resource localApiTokenResource = getLocalApiTokenResource();
		String existingApiToken = readExistingApiToken(localApiTokenResource);
		if (existingApiToken != null) {
			this.logger.debug("Existing Wavefront api token found from " + localApiTokenResource);
			registerApiToken(environment, existingApiToken);
		}
		else {
			String clusterUri = environment.getProperty(URI_PROPERTY, "https://wavefront.surf");
			try {
				AccountInfo accountInfo = autoNegotiateAccount(environment, clusterUri);
				registerApiToken(environment, accountInfo.getApiToken());
				writeApiTokenToDisk(localApiTokenResource, accountInfo.getApiToken());
				logAccountProvisioning(clusterUri, accountInfo);
			}
			catch (Exception ex) {
				logAccountProvisioningFailure(clusterUri, ex.getMessage());
			}
		}
	}

	private void logAccountProvisioning(String clusterUri, AccountInfo accountInfo) {
		StringBuilder sb = new StringBuilder(String.format(
				"%nA Wavefront account has been provisioned successfully and the API token has been saved to disk.%n%n"));
		sb.append(String.format(
				"To configure Spring Boot to use this account moving forward, add the following to your configuration:%n%n"));
		sb.append(String.format("\t%s=%s%n%n", API_TOKEN_PROPERTY, accountInfo.getApiToken()));
		sb.append(String.format("Connect to your Wavefront dashboard using this one-time use link:%n%s%n",
				accountInfo.determineLoginUrl(clusterUri)));
		System.out.println(sb.toString());
	}

	private void logAccountProvisioningFailure(String clusterUri, String message) {
		StringBuilder sb = new StringBuilder(
				String.format("%nFailed to auto-negotiate a Wavefront api token from %s.", clusterUri));
		if (StringUtils.hasText(message)) {
			sb.append(String.format(" The error was:%n%n%s%n%n", message));
		}
		System.out.println(sb.toString());
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

	private AccountInfo autoNegotiateAccount(ConfigurableEnvironment environment, String clusterUri) {
		RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
		AccountProvisioningClient client = new AccountProvisioningClient(this.logger, restTemplateBuilder);
		ApplicationInfo applicationInfo = new ApplicationInfo(environment);
		return provisionAccount(client, clusterUri, applicationInfo);
	}

	private void registerApiToken(ConfigurableEnvironment environment, String apiToken) {
		MapPropertySource wavefrontPropertySource = new MapPropertySource("wavefront",
				Collections.singletonMap(API_TOKEN_PROPERTY, apiToken));
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
