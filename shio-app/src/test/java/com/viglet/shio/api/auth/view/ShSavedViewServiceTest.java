/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.auth.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.auth.view.ShSavedViewService.NotYoursException;
import com.viglet.shio.api.auth.view.ShSavedViewService.Row;
import com.viglet.shio.api.auth.view.ShSavedViewService.Save;
import com.viglet.shio.api.auth.view.ShSavedViewService.UnknownListException;

/**
 * SH1024 — saved views: a person's named URLs for one list, optionally offered to the tenant.
 */
@SpringBootTest
class ShSavedViewServiceTest {

    @Autowired
    private ShSavedViewService service;

    @Test
    @DisplayName("SH1024: a view is its owner's and the shared ones, and saving a name twice replaces it")
    void viewsArePerPersonAndSharedByChoice() {
        String ana = "ana-" + UUID.randomUUID();
        String bruno = "bruno-" + UUID.randomUUID();

        service.save(ana, new Save("browser", "Drafts", "/console/content/folder/f1", "?state=draft", false));
        Row again = service.save(ana, new Save("browser", " Drafts ", "/console/content/folder/f1",
                "state=draft&type=Article", true));
        service.save(bruno, new Save("browser", "Mine", "/console/content/folder/f2", "", false));

        assertThat(service.visible(ana, "browser")).extracting(Row::name)
                .as("SH1024: saving under a name already used replaces it, so pressing save twice "
                        + "does not make two (P5), and bruno's private view is not ana's to see")
                .containsExactly("Drafts");
        assertThat(again.query()).as("the query is stored without its ?, whichever way it arrived")
                .isEqualTo("state=draft&type=Article");

        assertThat(service.visible(bruno, "browser")).extracting(Row::name, Row::mine)
                .as("SH1024: a shared view is offered to everyone in the tenant, and says whose it is")
                .contains(org.assertj.core.groups.Tuple.tuple("Drafts", false),
                        org.assertj.core.groups.Tuple.tuple("Mine", true));
        assertThat(service.visible(ana, "search")).as("each list offers only its own").isEmpty();

        assertThatThrownBy(() -> service.delete(bruno, again.id()))
                .as("SH1024: sharing offers a view; it does not hand it over")
                .isInstanceOf(NotYoursException.class);
        service.delete(ana, again.id());
        assertThat(service.visible(bruno, "browser")).extracting(Row::name).containsExactly("Mine");

        assertThatThrownBy(() -> service.visible(ana, "browsr"))
                .as("SH1024: a list that keeps no views is refused with the ones that do")
                .isInstanceOf(UnknownListException.class);
    }
}
