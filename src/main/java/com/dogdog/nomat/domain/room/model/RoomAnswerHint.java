package com.dogdog.nomat.domain.room.model;

public final class RoomAnswerHint {

    private static final char HANGUL_BASE = 0xAC00;
    private static final char HANGUL_END = 0xD7A3;
    private static final int HANGUL_SYLLABLE_COUNT_PER_INITIAL = 21 * 28;
    private static final char[] HANGUL_INITIALS = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private RoomAnswerHint() {
    }

    public static String from(String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }

        String normalizedAnswer = answer.trim();
        StringBuilder hint = new StringBuilder();
        boolean revealed = false;
        for (int index = 0; index < normalizedAnswer.length(); index++) {
            char value = normalizedAnswer.charAt(index);
            if (Character.isWhitespace(value)) {
                hint.append(value);
            } else if (isHangulSyllable(value)) {
                hint.append(HANGUL_INITIALS[(value - HANGUL_BASE) / HANGUL_SYLLABLE_COUNT_PER_INITIAL]);
            } else if (isEnglishLetter(value)) {
                if (revealed) {
                    hint.append('_');
                } else {
                    hint.append(value);
                    revealed = true;
                }
            } else if (Character.isDigit(value)) {
                hint.append(value);
            } else {
                hint.append('_');
            }
        }

        return hint.toString();
    }

    private static boolean isHangulSyllable(char value) {
        return value >= HANGUL_BASE && value <= HANGUL_END;
    }

    private static boolean isEnglishLetter(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }
}
