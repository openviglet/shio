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
package com.viglet.shio.onstartup.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.system.ShLocale;
import com.viglet.shio.persistence.repository.system.ShLocaleRepository;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShLocaleOnStartup {
	private final ShLocaleRepository shLocaleRepository;

	@Autowired
	public ShLocaleOnStartup(ShLocaleRepository shLocaleRepository) {
		this.shLocaleRepository = shLocaleRepository;
	}

	public void createDefaultRows() {

		if (shLocaleRepository.findAll().isEmpty()) {

			shLocaleRepository.save(new ShLocale("ar", "العربية - Arabic", "العربية - Árabe"));
			shLocaleRepository.save(new ShLocale("bn", "বাংলা - Bengali", "বাংলা - Bengali"));
			shLocaleRepository.save(new ShLocale("ca", "Català - Catalan (beta)", "Català - Catalão (beta)"));
			shLocaleRepository.save(new ShLocale("cs", "Čeština - Czech", "Čeština - Tcheco"));
			shLocaleRepository.save(new ShLocale("da", "Dansk - Danish", "Dansk - Dinamarquês"));
			shLocaleRepository.save(new ShLocale("de", "Deutsch - German", "Deutsch - Alemão"));
			shLocaleRepository.save(new ShLocale("el", "Ελληνικά - Greek", "Ελληνικά - Grego"));
			shLocaleRepository.save(new ShLocale("en", "English", "English - Inglês"));
			shLocaleRepository
					.save(new ShLocale("en-gb", "English UK - British English", "English UK - Inglês britânico"));
			shLocaleRepository.save(new ShLocale("es", "Español - Spanish", "Español - Espanhol"));
			shLocaleRepository.save(new ShLocale("eu", "Euskara - Basque", "Euskara - Basco"));
			shLocaleRepository.save(new ShLocale("fa", "فارسی - Persian", "فارسی - Persa"));
			shLocaleRepository.save(new ShLocale("fi", "Suomi - Finnish", "Suomi - Finlandês"));
			shLocaleRepository.save(new ShLocale("fil", "Filipino", "Filipino"));
			shLocaleRepository.save(new ShLocale("fr", "Français - French", "Français - Francês"));
			shLocaleRepository.save(new ShLocale("ga", "Gaeilge - Irish", "Gaeilge - Irlandês"));
			shLocaleRepository.save(new ShLocale("gl", "Galego - Galician", "Galego"));
			shLocaleRepository.save(new ShLocale("gu", "ગુજરાતી - Gujarati", "ગુજરાતી - Guzerate"));
			shLocaleRepository.save(new ShLocale("he", "עִבְרִית - Hebrew", "עִבְרִית - Hebraico"));
			shLocaleRepository.save(new ShLocale("hi", "िन्दी - Hindi", "िन्दी - Híndi"));
			shLocaleRepository.save(new ShLocale("hu", "Magyar - Hungarian", "Magyar - Húngaro"));
			shLocaleRepository
					.save(new ShLocale("id", "Bahasa Indonesia - Indonesian", "Bahasa Indonesia - Indonésio"));
			shLocaleRepository.save(new ShLocale("it", "Italiano - Italian", "Italiano"));
			shLocaleRepository.save(new ShLocale("ja", "日本語 - Japanese", "日本語 - Japonês"));
			shLocaleRepository.save(new ShLocale("kn", "ಕನ್ನಡ - Kannada", "ಕನ್ನಡ - Canarês"));
			shLocaleRepository.save(new ShLocale("ko", "한국어- Korean", "한국어- Coreano"));
			shLocaleRepository.save(new ShLocale("mr", "मराठी - Marathi", "मराठी - Marata"));
			shLocaleRepository.save(new ShLocale("msa", "Bahasa Melayu - Malay", "Bahasa Melayu - Malaio"));
			shLocaleRepository.save(new ShLocale("nl", "Nederlands - Dutch", "Nederlands - Holandês"));
			shLocaleRepository.save(new ShLocale("no", "Norsk - Norwegian", "Norsk - Norueguês"));
			shLocaleRepository.save(new ShLocale("pl", "Polski - Polish", "Polski - Polonês"));
			shLocaleRepository.save(new ShLocale("pt-pt", "Português - Portuguese (Portugal)", "Português (Portugal)"));
			shLocaleRepository.save(new ShLocale("pt-br", "Português - Portuguese (Brazil)", "Português (Brasil)"));
			shLocaleRepository.save(new ShLocale("ro", "Română - Romanian", "Română - Romeno"));
			shLocaleRepository.save(new ShLocale("ru", "Русский - Russian", "Русский - Russo"));
			shLocaleRepository.save(new ShLocale("sv", "Svenska - Swedish", "Svenska - Sueco"));
			shLocaleRepository.save(new ShLocale("ta", "தமிழ் - Tamil", "தமிழ் - Tâmil"));
			shLocaleRepository.save(new ShLocale("th", "ภาษาไทย - Thai", "ภาษาไทย - Tailandês"));
			shLocaleRepository.save(new ShLocale("tr", "Türkçe - Shkish", "Türkçe - Shco"));
			shLocaleRepository.save(new ShLocale("uk", "Українська мова - Ukrainian", "Українська мова - Ucraniano"));
			shLocaleRepository.save(new ShLocale("ur", "اردو - Urdu", "اردو - Urdu"));
			shLocaleRepository.save(new ShLocale("vi", "Tiếng Việt - Vietnamese", "Tiếng Việt - Vietnamita"));
			shLocaleRepository.save(new ShLocale("xx-lc", "LOLCATZ - Lolcat", "LOLCATZ - Lolcat"));
			shLocaleRepository.save(new ShLocale("zh-cn", "简体中文 - Simplified Chinese", "简体中文 - Chinês simplificado"));
			shLocaleRepository.save(new ShLocale("zh-tw", "简体中文 - Traditional Chinese", "繁體中文 - Chinês tradicional"));
		}
	}

}
