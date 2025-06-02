/*
 * Copyright (C) 2016-2020 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.shio.graphql.playground;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
* @author Alexandre Oliveira
* @since 0.3.7
*/
class ShPropertyGroupReader {

    private final Environment environment;
    private final String prefix;
    private Properties props;

    ShPropertyGroupReader(Environment environment, String prefix) {
        this.environment = Objects.requireNonNull(environment);
        this.prefix = Optional.ofNullable(prefix).orElse(StringUtils.EMPTY);
    }

    Properties load() {
        if (props == null) {
            props = new Properties();
            loadProps();
        }
        return props;
    }

    private void loadProps() {
        streamOfPropertySources().forEach(propertySource ->
                Arrays.stream(propertySource.getPropertyNames())
                        .filter(this::isWanted)
                        .forEach(key -> add(propertySource, key)));
    }

    private Stream<EnumerablePropertySource<?>> streamOfPropertySources() {
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            Iterator<PropertySource<?>> iterator = configurableEnvironment.getPropertySources().iterator();
            Iterable<PropertySource<?>> iterable = () -> iterator;
            return StreamSupport.stream(iterable.spliterator(), false)
                    .filter(EnumerablePropertySource.class::isInstance)
                    .map(EnumerablePropertySource.class::cast);
        }
        return Stream.empty();
    }

    private String withoutPrefix(String key) {
        return key.replace(prefix, StringUtils.EMPTY);
    }

    private boolean isWanted(String key) {
        return key.startsWith(prefix);
    }

    private Object add(EnumerablePropertySource<?> propertySource, String key) {
        return props.put(withoutPrefix(key), propertySource.getProperty(key));
    }

}

