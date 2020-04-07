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

import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * An {@link EnvironmentPostProcessor} that auto-negotiates an api token for Wavefront if
 * necessary.
 *
 * @author Stephane Nicoll
 */
class WavefrontEnvironmentPostProcessor
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
		String wavefrontUri = environment.getProperty(WAVEFRONT_PROPERTIES_PREFIX + "uri");
		if (StringUtils.hasText(wavefrontUri)) {
			this.logger.debug("Custom wavefront URI set, could not auto-negotiate API token");
			return;
		}
		// TODO: read api token from disk
		autoNegotiateApiToken(environment);
	}

	@Override
	public void onApplicationEvent(ApplicationPreparedEvent event) {
		this.logger.switchTo(WavefrontEnvironmentPostProcessor.class);
	}

	private void autoNegotiateApiToken(ConfigurableEnvironment environment) {
		RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
		AccountProvisioningClient client = new AccountProvisioningClient(this.logger, restTemplateBuilder);
		ApplicationInfo applicationInfo = new ApplicationInfo(environment);
		String apiToken = client.provisionAccount(applicationInfo);
		MapPropertySource wavefrontPropertySource = new MapPropertySource("wavefront",
				Collections.singletonMap(WAVEFRONT_PROPERTIES_PREFIX + "api-token", apiToken));
		environment.getPropertySources().addLast(wavefrontPropertySource);
	}

}
