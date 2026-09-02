package com.sacolao.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugifyTest {

    @Test
    void generatesSafeUniqueFriendlySlug() {
        assertThat(Slugify.from("Sacolão do João")).isEqualTo("sacolao-do-joao");
        assertThat(Slugify.from("  Hortifruti!!!  ")).isEqualTo("hortifruti");
        assertThat(Slugify.from("")).isEqualTo("loja");
    }
}
