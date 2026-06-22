/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.acl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.acl.ShContentAcl;

@Repository
public interface ShContentAclRepository extends JpaRepository<ShContentAcl, String> {

    List<ShContentAcl> findByObjectId(String objectId);

    List<ShContentAcl> findByObjectIdIn(List<String> objectIds);

    void deleteByObjectId(String objectId);
}
