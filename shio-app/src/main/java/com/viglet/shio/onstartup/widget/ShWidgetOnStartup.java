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
package com.viglet.shio.onstartup.widget;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.widget.ShSystemWidget;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShWidgetOnStartup {

	private final ShWidgetRepository shWidgetRepository;

	private static final String TEXT_TYPE = "TEXT,TEXTAREA";

	@Autowired
	public ShWidgetOnStartup(ShWidgetRepository shWidgetRepository) {
		this.shWidgetRepository = shWidgetRepository;
	}

	public void createDefaultRows() {

		if (shWidgetRepository.findAll().isEmpty()) {

			ShWidget shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.HIDDEN);
			shWidget.setDescription("Hidden Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShHiddenWidget");
			shWidget.setImplementationCode("template/widget/hidden/hidden.html");
			shWidget.setSettingPath("template/widget/hidden/setting/hidden-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);
			
			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.TEXT);
			shWidget.setDescription("Text Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShTextWidget");
			shWidget.setImplementationCode("template/widget/text.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);

			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.TEXT_AREA);
			shWidget.setDescription("Text Area Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShTextAreaWidget");
			shWidget.setImplementationCode("template/widget/textarea.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);

			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.FILE);
			shWidget.setDescription("File Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShFileWidget");
			shWidget.setImplementationCode("template/widget/file.html");
			shWidget.setSettingPath("template/widget/file/setting/file-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);

			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.ACE_JS);
			shWidget.setDescription("Ace Editor Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShAceEditorWidget");
			shWidget.setImplementationCode("template/widget/ace-editor-js.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);

			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.ACE_HTML);
			shWidget.setDescription("Ace Editor Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShAceEditorWidget");
			shWidget.setImplementationCode("template/widget/ace-editor-html.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);

			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.HTML_EDITOR);
			shWidget.setDescription("HTML Editor Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShHTMLWidget");
			shWidget.setImplementationCode("template/widget/html-editor/html-editor.html");
			shWidget.setSettingPath("template/widget/html-editor/setting/html-editor-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);

			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.CONTENT_SELECT);
			shWidget.setDescription("Content Select Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShContentSelectWidget");
			shWidget.setImplementationCode("template/widget/content-select.html");
			shWidget.setSettingPath("template/widget/content-select/setting/content-select-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);

			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.RELATOR);
			shWidget.setDescription("Relator Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShRelatorWidget");
			shWidget.setImplementationCode("template/widget/relator/relator-list.html");
			shWidget.setSettingPath("template/widget/relator/setting/relator-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);
			
			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.COMBO_BOX);
			shWidget.setDescription("Combo Box Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShComboBoxWidget");
			shWidget.setImplementationCode("template/widget/combo-box/combo-box.html");
			shWidget.setSettingPath("template/widget/combo-box/setting/combo-box-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);
			
			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.RECAPTCHA);
			shWidget.setDescription("reCAPTCHA Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShReCaptchaWidget");
			shWidget.setImplementationCode("template/widget/recaptcha/recaptcha.html");
			shWidget.setSettingPath("template/widget/recaptcha/setting/recaptcha-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);			
			
			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.FORM_CONFIGURATION);
			shWidget.setDescription("Form Configuraion Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShFormConfigurationWidget");
			shWidget.setImplementationCode("template/widget/form/form-configuration.html");
			shWidget.setSettingPath("template/widget/form/setting/form-configuration-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);
			
			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.DATE);
			shWidget.setDescription("Date Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShDateWidget");
			shWidget.setImplementationCode("template/widget/date/date.html");
			shWidget.setSettingPath("template/widget/date/setting/date-setting.html");
			shWidget.setType("DATE,".concat(TEXT_TYPE));

			shWidgetRepository.save(shWidget);
						
			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.MULTI_SELECT);
			shWidget.setDescription("Multi Select Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShMultiSelectWidget");
			shWidget.setImplementationCode("template/widget/multi-select/multi-select.html");
			shWidget.setSettingPath("template/widget/multi-select/setting/multi-select-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);
			
			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.TAB);
			shWidget.setDescription("Tab Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShTabWidget");
			shWidget.setImplementationCode("template/widget/tab/tab.html");
			shWidget.setSettingPath("template/widget/tab/setting/tab-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);
			
			shWidget = new ShWidget();
			shWidget.setName(ShSystemWidget.CHECK_BOX);
			shWidget.setDescription("Check Box Widget");
			shWidget.setClassName("com.viglet.shio.widget.ShCheckBoxWidget");
			shWidget.setImplementationCode("template/widget/check-box/check-box.html");
			shWidget.setSettingPath("template/widget/check-box/setting/check-box-setting.html");
			shWidget.setType(TEXT_TYPE);

			shWidgetRepository.save(shWidget);
		}

	}
}
