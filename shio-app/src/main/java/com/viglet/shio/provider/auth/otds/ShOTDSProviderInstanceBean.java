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
package com.viglet.shio.provider.auth.otds;

import com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Setter
@Getter
public class ShOTDSProviderInstanceBean extends ShAuthProviderInstance {

    private String host;
    private int port;
    private String resourceId;
    private String secretKey;
    private String partition;
    private String domain;
    private String membershipFilter;
    private String username;
    private String userFilter;
    private String userScope;
    private String userDN;
    private String groupFilter;
    private String groupScope;
    private String groupDN;


}