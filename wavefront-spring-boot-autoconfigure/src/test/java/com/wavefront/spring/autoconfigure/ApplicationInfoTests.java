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

import org.junit.jupiter.api.Test;

import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApplicationInfo}.
 *
 * @author Stephane Nicoll
 */
class ApplicationInfoTests {

	private final MockEnvironment environment = new MockEnvironment();

	@Test
	void applicationInfoWithEmptyEnvironment() {
		ApplicationInfo applicationInfo = new ApplicationInfo(this.environment);
		assertThat(applicationInfo.getName()).isEqualTo("unnamed_application");
		assertThat(applicationInfo.getService()).isEqualTo("unnamed_service");
		assertThat(applicationInfo.getCluster()).isNull();
		assertThat(applicationInfo.getShard()).isNull();
	}

	@Test
	void applicationInfoWithFallbackSpringApplicationName() {
		this.environment.setProperty("spring.application.name", "test-app");
		ApplicationInfo applicationInfo = new ApplicationInfo(this.environment);
		assertThat(applicationInfo.getName()).isEqualTo("test-app");
	}

	@Test
	void applicationInfoWithConfiguredApplicationAndFallbackSpringApplicationName() {
		this.environment.setProperty("spring.application.name", "test-app");
		this.environment.setProperty("wavefront.application.name", "wavefront-app");
		ApplicationInfo applicationInfo = new ApplicationInfo(this.environment);
		assertThat(applicationInfo.getName()).isEqualTo("wavefront-app");
	}

	@Test
	void applicationInfoWithConfiguredService() {
		this.environment.setProperty("wavefront.application.service", "test-service");
		ApplicationInfo applicationInfo = new ApplicationInfo(this.environment);
		assertThat(applicationInfo.getService()).isEqualTo("test-service");
	}

	@Test
	void applicationInfoWithConfiguredCluster() {
		this.environment.setProperty("wavefront.application.cluster", "test-cluster");
		ApplicationInfo applicationInfo = new ApplicationInfo(this.environment);
		assertThat(applicationInfo.getCluster()).isEqualTo("test-cluster");
	}

	@Test
	void applicationInfoWithConfiguredShard() {
		this.environment.setProperty("wavefront.application.shard", "test-shard");
		ApplicationInfo applicationInfo = new ApplicationInfo(this.environment);
		assertThat(applicationInfo.getShard()).isEqualTo("test-shard");
	}

}
