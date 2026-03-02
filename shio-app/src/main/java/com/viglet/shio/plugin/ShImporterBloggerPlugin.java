package com.viglet.shio.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;

import com.viglet.shio.exchange.post.ShPostExchange;

public class ShImporterBloggerPlugin implements ShImporterPlugin {

	@Override
	public ShPostExchange process(ShPostExchange shPostExchange) {
		shPostExchange.setPostType("WHISKY");
		Map<String, String> keyWords = new HashMap<>();
		keyWords.put("Country", "COUNTRY");
		keyWords.put("Brand", "BRAND");
		keyWords.put("Type", "TYPE");
		keyWords.put("Taste", "TASTE");
		keyWords.put("Alcohol By Volume (ABV)", "ABV");
		keyWords.put("Maturation", "MATURATION");
		keyWords.put("Chill Filtration", "CHILL_FILTRATION");
		keyWords.put("Price Range", "PRICE_RANGE");
		keyWords.put("Price Average", "PRICE_RANGE");
		keyWords.put("Price/Quality Ratio", "PRICE_QUALITY_RATIO");
		keyWords.put("Buying Advice", "BUYING_ADVICE");
		keyWords.put("Main Aromas", "MAIN_AROMAS");
		keyWords.put("Supportive Aroma Accents", "SUPPORTIVE_AROMA_ACCENTS");
		keyWords.put("Main Flavours", "MAIN_FLAVOURS");
		keyWords.put("Supportive Flavour Accents", "SUPPORTIVE_FLAVOR_ACCENTS");
		keyWords.put("Drinking Advice", "DRINKING_ADVICE");
		keyWords.put("Rating", "RATING");
		keyWords.put("Drinking Experience On the Rocks", "DRINKING_XP_ROCKS");
		keyWords.put("Overall", "OVERALL_NOTE");
		keyWords.put("Region", "REGION");
		keyWords.put("Age", "AGE");
		keyWords.put("Tasting Date", "TASTING_DATE");
		keyWords.put("Colour", "COLOUR");
		keyWords.put("Nose", "NOSE");
		keyWords.put("Finish", "FINISH");
		keyWords.put("Palate", "PALATE");
		keyWords.put("General Remarks", "GENERAL_REMARKS");
		keyWords.put("Drinking Experience Neat", "DRINKING_XP_NEAT");
		keyWords.put("Conclusion", "CONCLUSION");
		keyWords.put("Bottled By/For", "BOTTLED_BY_FOR");
		Map<String, Object> newFields = new HashMap<>();
		for (Entry<String, Object> field : shPostExchange.getFields().entrySet()) {
			newFields.put(field.getKey(), field.getValue());
					if (field.getKey().equals("TEXT")) {
				String plainText = Jsoup.parse(field.getValue().toString()).text();
				for (Entry<String, String> keyWord : keyWords.entrySet()) {
					plainText = plainText.replace(keyWord.getKey() + ":", ";" + keyWord.getKey() + ":");
				}

				for (Entry<String, String> keyWord : keyWords.entrySet()) {
					final String regex = keyWord.getKey() + ":[^;]*";

					final Pattern pattern = Pattern.compile(regex);
					final java.util.regex.Matcher matcher = pattern.matcher(plainText);

					while (matcher.find()) {
						newFields.put(keyWord.getValue(),
								matcher.group(0).replace(keyWord.getKey() + ":", "").trim());
					}
				}
			}
		}
		shPostExchange.setFields(newFields);
		return shPostExchange;
	}

}
