package com.viglet.shio.bean;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Setter
@Getter
public class ShPostTypeReport implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String name;
	
	private int total;
	
	private float percentage;
	
	private String color;


}
