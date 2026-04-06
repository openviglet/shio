package com.viglet.shio.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author Alexandre Oliveira
 */
@Setter
@Getter
public class ShCurrentUser {

    private String username;

    private String firstName;

    private String lastName;

    private String email;

    private boolean admin;

    private List<String> privileges;
}
