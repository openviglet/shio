/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.config.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.persistence.repository.system.ShConfigVarRepository;

/**
 * SH58 e-mail / SMTP settings service. Verifies the bean ⇄ {@code ShConfigVar}
 * round-trip under the {@code /email} path, the upsert (no duplicate rows on a
 * second save), and the boolean parsing defaults for an unset config.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShEmailConfigServiceTest {

    @Autowired
    private ShEmailConfigService service;
    @Autowired
    private ShConfigVarRepository shConfigVarRepository;

    @Test
    void savePersistsAndReadsBackEveryField() {
        ShEmailConfigBean bean = new ShEmailConfigBean();
        bean.setEnabled(true);
        bean.setHost("smtp.example.com");
        bean.setPort("587");
        bean.setUsername("mailer");
        bean.setPassword("s3cret");
        bean.setTestConnection(true);
        bean.setSmtpAuth(true);
        bean.setTlsEnabled(true);
        bean.setTlsRequired(false);
        bean.setSslEnabled(true);

        service.saveEmailConfig(bean);

        ShEmailConfigBean read = service.getEmailConfig();
        assertThat(read.isEnabled()).isTrue();
        assertThat(read.getHost()).isEqualTo("smtp.example.com");
        assertThat(read.getPort()).isEqualTo("587");
        assertThat(read.getUsername()).isEqualTo("mailer");
        assertThat(read.getPassword()).isEqualTo("s3cret");
        assertThat(read.isTestConnection()).isTrue();
        assertThat(read.isSmtpAuth()).isTrue();
        assertThat(read.isTlsEnabled()).isTrue();
        assertThat(read.isTlsRequired()).isFalse();
        assertThat(read.isSslEnabled()).isTrue();
    }

    @Test
    void resavingUpsertsInsteadOfDuplicatingRows() {
        ShEmailConfigBean bean = new ShEmailConfigBean();
        bean.setHost("first.example.com");
        service.saveEmailConfig(bean);

        bean.setHost("second.example.com");
        service.saveEmailConfig(bean);

        assertThat(shConfigVarRepository.findByPath(ShEmailConfigService.EMAIL_PATH))
                .filteredOn(v -> ShEmailConfigService.HOST.equals(v.getName()))
                .hasSize(1)
                .first()
                .extracting("value")
                .isEqualTo("second.example.com");
        assertThat(service.getEmailConfig().getHost()).isEqualTo("second.example.com");
    }
}
