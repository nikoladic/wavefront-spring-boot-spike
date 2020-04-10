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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.SpringApplicationEvent;
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
		implements EnvironmentPostProcessor, ApplicationListener<SpringApplicationEvent> {

	private static final String API_TOKEN_PROPERTY = "management.metrics.export.wavefront.api-token";

	private static final String URI_PROPERTY = "management.metrics.export.wavefront.uri";

	private static final String DEFAULT_CLUSTER_URI = "https://wavefront.surf";

	private final DeferredLog logger = new DeferredLog();

	private Supplier<String> accountProvisioningOutcome;

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		application.addListeners(this);
		if (!isApiTokenRequired(environment)) {
			return;
		}
		Resource localApiTokenResource = getLocalApiTokenResource();
		String existingApiToken = readExistingApiToken(localApiTokenResource);
		if (existingApiToken != null) {
			this.logger.debug("Existing Wavefront api token found from " + localApiTokenResource);
			registerApiToken(environment, existingApiToken);
		}
		else {
			String clusterUri = environment.getProperty(URI_PROPERTY, DEFAULT_CLUSTER_URI);
			try {
				AccountInfo accountInfo = autoNegotiateAccount(environment, clusterUri);
				registerApiToken(environment, accountInfo.getApiToken());
				writeApiTokenToDisk(localApiTokenResource, accountInfo.getApiToken());
				this.accountProvisioningOutcome = accountProvisioningSuccess(clusterUri, accountInfo);
			}
			catch (Exception ex) {
				this.accountProvisioningOutcome = accountProvisioningFailure(clusterUri, ex.getMessage());
			}
		}
	}

	@Override
	public void onApplicationEvent(SpringApplicationEvent event) {
		if (event instanceof ApplicationPreparedEvent) {
			this.logger.switchTo(AccountProvisioningEnvironmentPostProcessor.class);
		}
		if (event instanceof ApplicationStartedEvent || event instanceof ApplicationFailedEvent) {
			if (this.accountProvisioningOutcome != null) {
				System.out.println(this.accountProvisioningOutcome.get());
			}
		}
	}

	private boolean isApiTokenRequired(ConfigurableEnvironment environment) {
		String apiToken = environment.getProperty(API_TOKEN_PROPERTY);
		if (StringUtils.hasText(apiToken)) {
			this.logger.debug("Wavefront api token already set, no need to negotiate one");
			return false;
		}
		URI uri = environment.getProperty(URI_PROPERTY, URI.class);
		if (uri != null && "proxy".equals(uri.getScheme())) {
			this.logger.debug("Pushing to a Wavefront proxy does not require an api token.");
			return false;
		}
		return true;
	}

	private Supplier<String> accountProvisioningSuccess(String clusterUri, AccountInfo accountInfo) {
		StringBuilder sb = new StringBuilder(String.format(
				"%nA Wavefront account has been provisioned successfully and the API token has been saved to disk.%n%n"));
		sb.append(String.format(
				"To configure Spring Boot to use this account moving forward, add the following to your configuration:%n%n"));
		sb.append(String.format("\t%s=%s%n%n", API_TOKEN_PROPERTY, accountInfo.getApiToken()));
		sb.append(String.format("Connect to your Wavefront dashboard using this one-time use link:%n%s%n",
				accountInfo.determineLoginUrl(clusterUri)));
		return sb::toString;
	}

	private Supplier<String> accountProvisioningFailure(String clusterUri, String message) {
		StringBuilder sb = new StringBuilder(
				String.format("%nFailed to auto-negotiate a Wavefront api token from %s.", clusterUri));
		if (StringUtils.hasText(message)) {
			sb.append(String.format(" The error was:%n%n%s%n%n", message));
		}
		return sb::toString;
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
		Map<String, Object> wavefrontSettings = new HashMap<>();
		wavefrontSettings.put(API_TOKEN_PROPERTY, apiToken);
		String configuredClusterUri = environment.getProperty(URI_PROPERTY);
		if (!StringUtils.hasText(configuredClusterUri)) {
			wavefrontSettings.put(URI_PROPERTY, DEFAULT_CLUSTER_URI);
		}
		MapPropertySource wavefrontPropertySource = new MapPropertySource("wavefront", wavefrontSettings);
		environment.getPropertySources().addLast(wavefrontPropertySource);
	}

	protected Resource getLocalApiTokenResource() {
		return new PathResource(Paths.get(System.getProperty("user.home"), ".wavefront_token"));
	}

	protected AccountInfo provisionAccount(AccountProvisioningClient client, String clusterUri,
			ApplicationInfo applicationInfo) {
		return client.provisionAccount(clusterUri, applicationInfo);
	}

}
