package com.dogdog.nomat.domain.room.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoomAnswerHintTest {

    @Test
    void createsHangulInitialHint() {
        assertThat(RoomAnswerHint.from("뉴진스")).isEqualTo("ㄴㅈㅅ");
    }

    @Test
    void createsEnglishMaskHintWithFirstLetter() {
        assertThat(RoomAnswerHint.from("Shake It Off")).isEqualTo("S____ __ ___");
    }

    @Test
    void keepsDigitsAndMasksSymbols() {
        assertThat(RoomAnswerHint.from("APT. 2024")).isEqualTo("A___ 2024");
    }
}
