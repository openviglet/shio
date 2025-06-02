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
package com.viglet.shio.exchange.post.type;

import java.util.Date;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import com.viglet.shio.persistence.repository.post.type.ShPostTypeAttrRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.exchange.ShExchange;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
@Slf4j
public class ShPostTypeImport {
    private final ShWidgetRepository shWidgetRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostTypeAttrRepository shPostTypeAttrRepository;

    @Autowired
    public ShPostTypeImport(ShWidgetRepository shWidgetRepository,
                            ShPostTypeRepository shPostTypeRepository,
                            ShPostTypeAttrRepository shPostTypeAttrRepository) {
        this.shWidgetRepository = shWidgetRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostTypeAttrRepository = shPostTypeAttrRepository;
    }

    @Transactional
    public void importPostType(ShExchange shExchange, boolean isCloned) {
        log.info("1 of 4 - Importing Post Types");
        Set<ShPostType> shPostTypes = new HashSet<>();
        shExchange.getPostTypes()
                .stream()
                .filter(shPostTypeExchange ->
                        shPostTypeRepository.findByName(shPostTypeExchange.getName()) == null)
                .forEach(shPostTypeExchange -> {
                    log.info(".. {} Post Type ({})", shPostTypeExchange.getName(), shPostTypeExchange.getId());
                    ShPostType shPostType = new ShPostType();
                 //   shPostType.setId(shPostTypeExchange.getId());
                    shPostType.setTitle(shPostTypeExchange.getLabel());
                    shPostType.setDate(isCloned ? new Date() : shPostTypeExchange.getDate());
                    shPostType.setDescription(shPostTypeExchange.getDescription());
                    shPostType.setName(shPostTypeExchange.getName());
                    shPostType.setNamePlural(shPostTypeExchange.getNamePlural());
                    shPostType.setOwner(shPostTypeExchange.getOwner());
                    shPostType.setSystem(shPostTypeExchange.isSystem() ? (byte) 1 : (byte) 0);
                    shPostTypes.add(shPostType);
                    this.importPostTypeAttr(shPostTypeExchange, shPostType);
                });
        shPostTypeRepository.saveAll(shPostTypes);

    }

    private void importPostTypeAttr(ShPostTypeExchange shPostTypeExchange, ShPostType shPostType) {
        if (!ObjectUtils.isEmpty(shPostTypeExchange.getFields())) {
            Set<ShPostTypeAttr> shPostTypeAttrs = new HashSet<>();
            shPostTypeExchange.getFields()
                    .entrySet().stream().map(this::importPostTypeField).forEach(shPostTypeAttr -> {
                        shPostTypeAttr.setShPostType(shPostType);
                        shPostTypeAttrs.add(shPostTypeAttr);
                    });
            shPostTypeAttrRepository.saveAll(shPostTypeAttrs);
        }
    }

    public ShPostTypeAttr importPostTypeField(Entry<String, ShPostTypeFieldExchange> postTypeField) {
        ShPostTypeFieldExchange shPostTypeFieldExchange = postTypeField.getValue();
        ShPostTypeAttr shPostTypeAttr = new ShPostTypeAttr();
      //  shPostTypeAttr.setId(shPostTypeFieldExchange.getId());
        shPostTypeAttr.setDescription(shPostTypeFieldExchange.getDescription());
        shPostTypeAttr.setLabel(shPostTypeFieldExchange.getLabel());
        shPostTypeAttr.setName(postTypeField.getKey());
        shPostTypeAttr.setOrdinal(shPostTypeFieldExchange.getOrdinal());
        shPostTypeAttr.setIsSummary(shPostTypeFieldExchange.isSummary() ? (byte) 1 : (byte) 0);
        shPostTypeAttr.setIsTitle(shPostTypeFieldExchange.isTitle() ? (byte) 1 : (byte) 0);
        shPostTypeAttr.setRequired(shPostTypeFieldExchange.isRequired() ? (byte) 1 : (byte) 0);
        shPostTypeAttr.setShWidget( shWidgetRepository.findByName(shPostTypeFieldExchange.getWidget()));
        shPostTypeAttr.setWidgetSettings(shPostTypeFieldExchange.getWidgetSettings());
        Set<ShPostTypeAttr> shPostTypeAttrs = new HashSet<>();
        if (!ObjectUtils.isEmpty(shPostTypeFieldExchange.getFields())) {
            shPostTypeFieldExchange.getFields()
                    .entrySet().stream()
                    .map(this::importPostTypeField).forEach(shPostTypeAttrChild -> {
                        shPostTypeAttrChild.setShParentPostTypeAttr(shPostTypeAttr);
                        shPostTypeAttrs.add(shPostTypeAttrChild);
                    });
        }
        shPostTypeAttr.setShPostTypeAttrs(shPostTypeAttrs);
        return shPostTypeAttr;
    }
}
