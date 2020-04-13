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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.wavefront.sdk.common.application.ApplicationTags;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.wavefront.WavefrontMeterRegistry;

import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Advanced Auto-configuration for Wavefront.
 *
 * @author Stephane Nicoll
 */
@Configuration
@ConditionalOnClass({ WavefrontMeterRegistry.class, MeterRegistryCustomizer.class, ApplicationTags.class })
@EnableConfigurationProperties(WavefrontProperties.class)
public class WavefrontAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ApplicationTags wavefrontApplicationTags(WavefrontProperties properties) {
		return new ApplicationTagsFactory().createFromProperties(properties);
	}

	@Bean
	public MeterRegistryCustomizer<WavefrontMeterRegistry> wavefrontTagsMeterRegistryCustomizer(
			ApplicationTags applicationTags) {
		return (registry) -> registry.config().commonTags(createTagsFrom(applicationTags));
	}

	private Iterable<Tag> createTagsFrom(ApplicationTags applicationTags) {
		Map<String, String> tags = new HashMap<>();
		PropertyMapper mapper = PropertyMapper.get().alwaysApplyingWhenNonNull();
		mapper.from(applicationTags::getApplication).to((application) -> tags.put("application", application));
		mapper.from(applicationTags::getService).to((service) -> tags.put("service", service));
		mapper.from(applicationTags::getCluster).to((cluster) -> tags.put("cluster", cluster));
		mapper.from(applicationTags::getShard).to((shard) -> tags.put("shard", shard));
		if (applicationTags.getCustomTags() != null) {
			tags.putAll(applicationTags.getCustomTags());
		}
		return Tags.of(tags.entrySet().stream().map((entry) -> Tag.of(entry.getKey(), entry.getValue()))
				.collect(Collectors.toList()));
	}

}
