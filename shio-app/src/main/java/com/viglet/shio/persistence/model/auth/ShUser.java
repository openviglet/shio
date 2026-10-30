/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
package com.viglet.shio.persistence.model.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQuery;

/**
 * The persistent class for the ShUser database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@NamedQuery(name = "ShUser.findAll", query = "SELECT s FROM ShUser s")
public class ShUser implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "username")
	private String username;

	private String confirmEmail;

	@Column(name = "email")
	private String email;

	private String firstName;

	private Instant lastLogin;

	private String lastName;

	private String lastPostType;

	/**
	 * SH971 — what the console's dock reports to this person, as JSON ({"muted": [...]}). Served by
	 * its own endpoint, never with the user, so it is left out of every user payload.
	 */
	@JsonIgnore
	private String dockPreferences;

	private int loginTimes;

	/**
	 * SH1334 — the bcrypt hash, accepted on a write and never sent: the user list and the create
	 * response both serialised it, so the console received every user's hash, and a user created
	 * before SH1334 had their plaintext echoed back. The same rule the webhook secret follows.
	 */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@Column(name = "password")
	private String password;

	private String realm;

	private String recoverPassword;

	@Column(name = "enabled")
	private int enabled;
	
	@ManyToMany
	private Set<ShGroup> shGroups = new HashSet<>();
	
	public ShUser(ShUser shUser) {
		this.username = shUser.username;
		this.email = shUser.email;
		this.password = shUser.password;
		this.enabled = shUser.enabled;
	}

	public ShUser() {
		super();
	}

	public String getConfirmEmail() {
		return this.confirmEmail;
	}

	public void setConfirmEmail(String confirmEmail) {
		this.confirmEmail = confirmEmail;
	}

	public String getEmail() {
		return this.email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getFirstName() {
		return this.firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public Instant getLastLogin() {
		return this.lastLogin;
	}

	public void setLastLogin(Instant lastLogin) {
		this.lastLogin = lastLogin;
	}

	public String getLastName() {
		return this.lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getDockPreferences() {
		return dockPreferences;
	}

	public void setDockPreferences(String dockPreferences) {
		this.dockPreferences = dockPreferences;
	}

	public String getLastPostType() {
		return this.lastPostType;
	}

	public void setLastPostType(String lastPostType) {
		this.lastPostType = lastPostType;
	}

	public int getLoginTimes() {
		return this.loginTimes;
	}

	public void setLoginTimes(int loginTimes) {
		this.loginTimes = loginTimes;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getRealm() {
		return this.realm;
	}

	public void setRealm(String realm) {
		this.realm = realm;
	}

	public String getRecoverPassword() {
		return this.recoverPassword;
	}

	public void setRecoverPassword(String recoverPassword) {
		this.recoverPassword = recoverPassword;
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}
	
	public int getEnabled() {
		return enabled;
	}

	public void setEnabled(int enabled) {
		this.enabled = enabled;
	}
	
	public Set<ShGroup> getShGroups() {
		return this.shGroups;
	}

	public void setShGroups(Set<ShGroup> shGroups) {
		this.shGroups.clear();
		if (shGroups != null) {
			this.shGroups.addAll(shGroups);
		}
	}
}
