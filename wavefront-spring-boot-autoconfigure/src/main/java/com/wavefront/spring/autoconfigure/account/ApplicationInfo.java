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

import java.util.function.Supplier;

import org.springframework.core.env.Environment;

/**
 * Gather the necessary information to provision a Wavefront account.
 *
 * @author Stephane Nicoll
 */
class ApplicationInfo {

	private static final String PREFIX = "wavefront.application.";

	private final String name;

	private final String service;

	private final String cluster;

	private final String shard;

	ApplicationInfo(Environment environment) {
		this.name = getValue(environment, "name", fallbackApplicationName(environment));
		this.service = getValue(environment, "service", () -> "unnamed_service");
		this.cluster = getValue(environment, "cluster", () -> null);
		this.shard = getValue(environment, "shard", () -> null);
	}

	private static String getValue(Environment environment, String name, Supplier<String> fallback) {
		String value = environment.getProperty(PREFIX + name);
		return (value != null) ? value : fallback.get();
	}

	private static Supplier<String> fallbackApplicationName(Environment environment) {
		return () -> {
			String applicationName = environment.getProperty("spring.application.name");
			return (applicationName != null) ? applicationName : "unnamed_application";
		};
	}

	String getName() {
		return this.name;
	}

	String getService() {
		return this.service;
	}

	String getCluster() {
		return this.cluster;
	}

	String getShard() {
		return this.shard;
	}

}
