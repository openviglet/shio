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
package com.viglet.shio.provider.auth.otds.bean;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Alexandre Oliveira
 */
@Setter
@Getter
public class ShOTDSAuthenticationResponseBean {
    private String resourceID;
    private String continuationContext;
    private String token;
    private String ticket;
    private String userId;
    private String continuationData;
    private String failureReason;
    private long passwordExpirationTime;
    private boolean continuation;


}