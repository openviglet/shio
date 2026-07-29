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

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.widget.ShSystemWidget;

/**
 * The widget catalogue, seeded <b>per widget</b> and on every startup (SH325).
 *
 * <h2>Why it is not guarded by "is the catalogue empty"</h2>
 *
 * <p>It used to be, and that made every widget added by a later release reachable only on a
 * brand-new database. SH252's {@code Ace Editor - CSS} is the case that surfaced it: on any instance
 * that predates it, {@code POST /api/v2/render/post-types} answered a <b>500</b> —
 * {@code Widget 'Ace Editor - CSS' is not seeded} — so SH183's whole render model was uncreatable
 * everywhere real users are. The sharpest part was that <b>SH289's {@code reconcile} failed
 * identically</b>: the verb written to repair an older instance's render model was broken by the
 * same defect it exists to repair, because both go through {@code specsFor}. One reader, one bug,
 * both doors.</p>
 *
 * <p>That is SH152's provision-never-upsert rule applied at the wrong <b>granularity</b>. Post-types
 * are provisioned per type — {@code ensure} creates what is absent — while widgets were provisioned
 * per <em>catalogue</em>. And unlike a post-type, this catalogue is <b>closed and code-owned</b>: a
 * curator never adds a widget, because a widget is a Java class plus a React component. So
 * find-or-create per row is both safe and obviously correct.</p>
 *
 * <h2>There were two guards, and only one was written down</h2>
 *
 * <p>SH325 was filed against the {@code findAll().isEmpty()} check that stood here, and fixing only
 * that would have changed nothing: {@code ShOnStartup} called this <b>inside</b> its own first-time
 * gate ({@code ShConfigVarOnStartup.FIRST_TIME_NAME}), so on an existing instance this method was
 * never invoked at all. The call is now outside that gate — which is safe precisely because this is
 * idempotent, and is the only way an instance ever receives a widget added after it was created.
 * SH322's lesson again: the entry named one site and there were two.</p>
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p><b>It never touches a row that exists</b>, following SH289's additive-only rule: changing a
 * seeded widget's class or template is a different decision from creating a missing one, and doing
 * both here would silently overwrite whatever an operator had reason to change. For the same reason
 * it <em>removes</em> nothing — the SH60-deprecated {@code reCAPTCHA} row is still served by
 * {@code GET /api/v2/widget} on instances old enough to have it, and deleting a row a post-type's
 * attribute may still point at is a migration, not a seed.</p>
 *
 * @author Alexandre Oliveira
 */
@Component
public class ShWidgetOnStartup {

	private final ShWidgetRepository shWidgetRepository;

	private static final String TEXT_TYPE = "TEXT,TEXTAREA";
	private static final String ACE_EDITOR = "com.viglet.shio.widget.ShAceEditorWidget";
	private static final String ACE_DESCRIPTION = "Ace Editor Widget";

	public ShWidgetOnStartup(ShWidgetRepository shWidgetRepository) {
		this.shWidgetRepository = shWidgetRepository;
	}

	/**
	 * One row per widget this build knows about.
	 *
	 * @param name               the catalogue key — what a post-type field references, and what
	 *                           {@code ShRenderPostTypes} resolves
	 * @param description        shown in the console
	 * @param className          the server-side widget class
	 * @param implementationCode the legacy AngularJS template path (SH21 retires these; the React
	 *                           console maps on {@code name}, so it is carried and not read)
	 * @param settingPath        the legacy settings template, or {@code null}
	 * @param type               the field types this widget can back
	 */
	private record Spec(String name, String description, String className,
			String implementationCode, String settingPath, String type) {
	}

	/**
	 * The catalogue, in the order it was written — the one table, so a widget added here is created
	 * on a new instance <em>and</em> offered to an old one (SH289's rule: one field table, or the
	 * two doors drift).
	 */
	private static List<Spec> specs() {
		List<Spec> specs = new ArrayList<>();
		specs.add(new Spec(ShSystemWidget.HIDDEN, "Hidden Widget",
				"com.viglet.shio.widget.ShHiddenWidget",
				"template/widget/hidden/hidden.html",
				"template/widget/hidden/setting/hidden-setting.html", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.TEXT, "Text Widget",
				"com.viglet.shio.widget.ShTextWidget",
				"template/widget/text.html", null, TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.TEXT_AREA, "Text Area Widget",
				"com.viglet.shio.widget.ShTextAreaWidget",
				"template/widget/textarea.html", null, TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.FILE, "File Widget",
				"com.viglet.shio.widget.ShFileWidget",
				"template/widget/file.html",
				"template/widget/file/setting/file-setting.html", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.ACE_JS, ACE_DESCRIPTION, ACE_EDITOR,
				"template/widget/ace-editor-js.html", null, TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.ACE_HTML, ACE_DESCRIPTION, ACE_EDITOR,
				"template/widget/ace-editor-html.html", null, TEXT_TYPE));
		// SH252 — the row whose absence made SH183's render model uncreatable on every existing
		// instance, and the reason this method is per-widget rather than per-catalogue.
		specs.add(new Spec(ShSystemWidget.ACE_CSS, ACE_DESCRIPTION, ACE_EDITOR,
				"template/widget/ace-editor-css.html", null, TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.HTML_EDITOR, "HTML Editor Widget",
				"com.viglet.shio.widget.ShHTMLWidget",
				"template/widget/html-editor/html-editor.html",
				"template/widget/html-editor/setting/html-editor-setting.html", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.CONTENT_SELECT, "Content Select Widget",
				"com.viglet.shio.widget.ShContentSelectWidget",
				"template/widget/content-select.html",
				"template/widget/content-select/setting/content-select-setting.html", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.RELATOR, "Relator Widget",
				"com.viglet.shio.widget.ShRelatorWidget",
				"template/widget/relator/relator-list.html",
				"template/widget/relator/setting/relator-setting.html", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.COMBO_BOX, "Combo Box Widget",
				"com.viglet.shio.widget.ShComboBoxWidget",
				"template/widget/combo-box/combo-box.html",
				"template/widget/combo-box/setting/combo-box-setting.html", TEXT_TYPE));
		// SH60: the legacy reCAPTCHA widget is deprecated (not ported to React). Its server-side
		// class (com.viglet.shio.widget.ShReCaptchaWidget) was removed in the nuclear refactor and
		// form-spam protection is the consuming front-end app's concern in a headless-first
		// product — so the orphaned seed is dropped. AngularJS templates go with SH21.
		specs.add(new Spec(ShSystemWidget.FORM_CONFIGURATION, "Form Configuraion Widget",
				"com.viglet.shio.widget.ShFormConfigurationWidget",
				"template/widget/form/form-configuration.html",
				"template/widget/form/setting/form-configuration-setting.html", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.DATE, "Date Widget",
				"com.viglet.shio.widget.ShDateWidget",
				"template/widget/date/date.html",
				"template/widget/date/setting/date-setting.html", "DATE,".concat(TEXT_TYPE)));
		specs.add(new Spec(ShSystemWidget.MULTI_SELECT, "Multi Select Widget",
				"com.viglet.shio.widget.ShMultiSelectWidget",
				"template/widget/multi-select/multi-select.html",
				"template/widget/multi-select/setting/multi-select-setting.html", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.TAB, "Tab Widget",
				"com.viglet.shio.widget.ShTabWidget",
				"template/widget/tab/tab.html",
				"template/widget/tab/setting/tab-setting.html", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.CHECK_BOX, "Check Box Widget",
				"com.viglet.shio.widget.ShCheckBoxWidget",
				"template/widget/check-box/check-box.html",
				"template/widget/check-box/setting/check-box-setting.html", TEXT_TYPE));
		return specs;
	}

	/**
	 * Creates every widget this build knows about and none that already exists.
	 *
	 * @return the names it created, so an upgrade can log what it added
	 */
	public List<String> createDefaultRows() {
		List<String> created = new ArrayList<>();
		for (Spec spec : specs()) {
			// findFirstByName, not findByName: the latter throws
			// IncorrectResultSizeDataAccessException on a duplicate, and a seed that dies on
			// pre-existing data is worse than one that leaves it alone (SH321's shape).
			if (shWidgetRepository.findFirstByName(spec.name()) != null) {
				continue;
			}
			ShWidget widget = new ShWidget();
			widget.setName(spec.name());
			widget.setDescription(spec.description());
			widget.setClassName(spec.className());
			widget.setImplementationCode(spec.implementationCode());
			widget.setSettingPath(spec.settingPath());
			widget.setType(spec.type());
			shWidgetRepository.save(widget);
			created.add(spec.name());
		}
		return created;
	}

	/** The catalogue keys this build expects to exist — read by the SH325 guard test. */
	public static List<String> expectedNames() {
		return specs().stream().map(Spec::name).toList();
	}
}
