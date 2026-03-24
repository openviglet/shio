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
package com.viglet.shio.persistence.utils;

import java.lang.reflect.Member;
import java.util.EnumSet;
import java.util.UUID;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.AnnotationBasedGenerator;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;

/**
 * Custom UUID generator that allows pre-assigned identifiers.
 * <p>
 * If the entity already has an ID (e.g., during import), it is preserved.
 * Otherwise, a new random UUID is generated.
 *
 * @author Alexandre Oliveira
 */
public class ShUuidGenerator
        implements BeforeExecutionGenerator, AnnotationBasedGenerator<ShAssignableUuidGenerator> {

    public ShUuidGenerator() {
    }

    @Override
    public void initialize(ShAssignableUuidGenerator annotation, Member member, GeneratorCreationContext context) {
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner,
                           Object currentValue, EventType eventType) {
        if (currentValue != null) {
            return currentValue;
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean generatedOnExecution() {
        return false;
    }

    @Override
    public boolean allowAssignedIdentifiers() {
        return true;
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }
}
