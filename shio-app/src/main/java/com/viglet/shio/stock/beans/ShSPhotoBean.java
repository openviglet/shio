package com.viglet.shio.stock.beans;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShSPhotoBean {
	private String name;
	
	private Instant date;
	
	@JsonProperty("preview_xxs")
	private ShSPhotoPreviewBean previewXXS;

	@JsonProperty("preview_xs")
	private ShSPhotoPreviewBean previewXS;

	@JsonProperty("preview_s")
	private ShSPhotoPreviewBean previewS;
	
	@JsonProperty("preview_m")
	private ShSPhotoPreviewBean previewM;
	
	@JsonProperty("preview_l")
	private ShSPhotoPreviewBean previewL;
	
	@JsonProperty("preview_xl")
	private ShSPhotoPreviewBean previewXL;
	
	private ShSPhotoPreviewBean raw;

	private String dominantColor;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Instant getDate() {
		return date;
	}

	public void setDate(Instant date) {
		this.date = date;
	}

	public ShSPhotoPreviewBean getPreviewXXS() {
		return previewXXS;
	}

	public void setPreviewXXS(ShSPhotoPreviewBean previewXXS) {
		this.previewXXS = previewXXS;
	}

	public ShSPhotoPreviewBean getPreviewXS() {
		return previewXS;
	}

	public void setPreviewXS(ShSPhotoPreviewBean previewXS) {
		this.previewXS = previewXS;
	}

	public ShSPhotoPreviewBean getPreviewS() {
		return previewS;
	}

	public void setPreviewS(ShSPhotoPreviewBean previewS) {
		this.previewS = previewS;
	}

	public ShSPhotoPreviewBean getPreviewM() {
		return previewM;
	}

	public void setPreviewM(ShSPhotoPreviewBean previewM) {
		this.previewM = previewM;
	}

	public ShSPhotoPreviewBean getPreviewL() {
		return previewL;
	}

	public void setPreviewL(ShSPhotoPreviewBean previewL) {
		this.previewL = previewL;
	}

	public ShSPhotoPreviewBean getPreviewXL() {
		return previewXL;
	}

	public void setPreviewXL(ShSPhotoPreviewBean previewXL) {
		this.previewXL = previewXL;
	}

	public ShSPhotoPreviewBean getRaw() {
		return raw;
	}

	public void setRaw(ShSPhotoPreviewBean raw) {
		this.raw = raw;
	}

	public String getDominantColor() {
		return dominantColor;
	}

	public void setDominantColor(String dominantColor) {
		this.dominantColor = dominantColor;
	}

	
}
