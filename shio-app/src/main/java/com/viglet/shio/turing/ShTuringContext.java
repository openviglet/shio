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
package com.viglet.shio.turing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/__tur/sn/{siteName}")
public class ShTuringContext {
	private static final Log logger = LogFactory.getLog(ShTuringContext.class);
	private static final String TURING_ENDPOINT = "http://localhost:2700/api/sn/";

	@GetMapping("/search")
	public ResponseEntity<Object> turSNSiteSearchSelect(@PathVariable String siteName,
			@RequestParam(required = false, name = "q") String q,
			@RequestParam(required = false, name = "p") String strCurrentPage,
			@RequestParam(required = false, name = "fq[]") List<String> fq,
			@RequestParam(required = false, name = "sort") String sort, HttpServletRequest request) {

		URIBuilder turingURL = null;
		try {
			turingURL = new URIBuilder(TURING_ENDPOINT + siteName + "/search").addParameter("q", q)
					.addParameter("p", strCurrentPage).addParameter("sort", sort);

			if (turingURL != null) {
				if (fq != null) {
					for (String fqItem : fq) {
						turingURL.addParameter("fq[]", fqItem);
					}
				}

				final HttpHeaders httpHeaders = new HttpHeaders();
				httpHeaders.setContentType(MediaType.APPLICATION_JSON);

				return new ResponseEntity<>(this.getResults(turingURL.build().toString()), httpHeaders, HttpStatus.OK);

			}
		} catch (URISyntaxException e) {
			logger.error(e);

		}
		return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
	}

	@GetMapping("/ac")
	public ResponseEntity<Object> turSNSiteAutoComplete(@PathVariable String siteName,
			@RequestParam(required = false, name = "q") String q, HttpServletRequest request) {
		URIBuilder turingURL;
		try {
			turingURL = new URIBuilder(TURING_ENDPOINT + siteName + "/ac").addParameter("q", q);
			final HttpHeaders httpHeaders = new HttpHeaders();
			httpHeaders.setContentType(MediaType.APPLICATION_JSON);
			return new ResponseEntity<>(this.getResults(turingURL.build().toString()), httpHeaders, HttpStatus.OK);
		} catch (URISyntaxException e) {
			logger.error(e);
		}

		return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
	}

	private String getResults(String url) {
		StringBuilder result = new StringBuilder();
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			HttpResponse response = client.execute(new HttpGet(url));
			if (response != null) {
				try (BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
					String line = StringUtils.EMPTY;
					while (StringUtils.isNotEmpty((line = rd.readLine())))
						result.append(line);
				}
			}
		} catch (IOException e) {
			logger.error(e);
		}
		return result.toString();
	}
}
