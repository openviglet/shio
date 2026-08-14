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
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
	private static final String ACE_DESCRIPTION = "Ace Editor Widget";

	public ShWidgetOnStartup(ShWidgetRepository shWidgetRepository) {
		this.shWidgetRepository = shWidgetRepository;
	}

	/**
	 * One row per widget this build knows about.
	 *
	 * @param name        the catalogue key — what a post-type field references, what
	 *                    {@code ShRenderPostTypes} resolves, and what the React console maps onto a
	 *                    component. It is the whole contract
	 * @param description shown in the console
	 * @param type        the field types this widget can back
	 */
	private record Spec(String name, String description, String type) {
	}

	/**
	 * The catalogue, in the order it was written — the one table, so a widget added here is created
	 * on a new instance <em>and</em> offered to an old one (SH289's rule: one field table, or the
	 * two doors drift).
	 */
	private static List<Spec> specs() {
		List<Spec> specs = new ArrayList<>();
		specs.add(new Spec(ShSystemWidget.HIDDEN, "Hidden Widget", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.TEXT, "Text Widget", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.TEXT_AREA, "Text Area Widget", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.FILE, "File Widget", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.ACE_JS, ACE_DESCRIPTION, TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.ACE_HTML, ACE_DESCRIPTION, TEXT_TYPE));
		// SH252 — the row whose absence made SH183's render model uncreatable on every existing
		// instance, and the reason this method is per-widget rather than per-catalogue.
		specs.add(new Spec(ShSystemWidget.ACE_CSS, ACE_DESCRIPTION, TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.HTML_EDITOR, "HTML Editor Widget", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.CONTENT_SELECT, "Content Select Widget", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.RELATOR, "Relator Widget", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.COMBO_BOX, "Combo Box Widget", TEXT_TYPE));
		// SH60: the legacy reCAPTCHA widget is deprecated (not ported to React). Its server-side
		// class was removed in the nuclear refactor and form-spam protection is the consuming
		// front-end app's concern in a headless-first product — so the orphaned seed is dropped.
		specs.add(new Spec(ShSystemWidget.FORM_CONFIGURATION, "Form Configuraion Widget", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.DATE, "Date Widget", "DATE,".concat(TEXT_TYPE)));
		specs.add(new Spec(ShSystemWidget.MULTI_SELECT, "Multi Select Widget", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.TAB, "Tab Widget", TEXT_TYPE));
		specs.add(new Spec(ShSystemWidget.CHECK_BOX, "Check Box Widget", TEXT_TYPE));
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

	/**
	 * Names the catalogue holds more than once, with how many rows each has (SH753).
	 *
	 * <h2>Why a report and not a refusal</h2>
	 *
	 * <p>"Unique by name" is what every resolver assumes and <b>nothing enforces</b>: there is no
	 * unique index, and the catalogue is a shared global row set an import, a
	 * {@code POST /api/v2/widget} or a cascading write can add to. SH753 is what that costs — a
	 * catalogue with five rows named {@code Text} took every upload down, because the reader in the
	 * File post-type path was the throwing one.</p>
	 *
	 * <p>The read is now the tolerant one everywhere, which fixes the crash and would have made the
	 * duplication <em>invisible</em>: a resolver that quietly picks the first row is a resolver that
	 * never mentions there were five. So the state is reported instead — once per boot, over a
	 * closed catalogue of sixteen rows, which is a cost nobody will notice and the only thing that
	 * turns "why is this widget wrong" into a line somebody can read.</p>
	 *
	 * <p><b>Not a unique index, and that is a decision.</b> An instance that already carries
	 * duplicates would fail the migration on upgrade, so the index needs a de-duplication step
	 * ahead of it — and de-duplicating means choosing which row the {@code ShPostTypeAttr}s
	 * pointing at the losers should be repointed to. That is a migration, not a seed, and SH289's
	 * rule is that this class is additive only.</p>
	 *
	 * @return name to row count, only for names with more than one row; empty when the catalogue is
	 *         what everything assumes it is
	 */
	public Map<String, Long> duplicateNames() {
		Map<String, Long> byName = shWidgetRepository.findAll().stream()
				.collect(Collectors.groupingBy(ShWidget::getName, TreeMap::new, Collectors.counting()));
		byName.values().removeIf(count -> count <= 1);
		return byName;
	}
}
