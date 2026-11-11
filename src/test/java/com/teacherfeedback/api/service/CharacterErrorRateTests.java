package com.teacherfeedback.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharacterErrorRateTests {

    @Test
    void normalizesPunctuationWhitespaceAndLatinCase() {
        assertEquals("unit1感叹句", CharacterErrorRate.normalize(" Unit 1，感叹句！ "));
    }

    @Test
    void separatesSubstitutionsDeletionsAndInsertions() {
        CharacterErrorRate.Result substitution = CharacterErrorRate.compare("欢迎大家", "欢迎大加");
        CharacterErrorRate.Result deletion = CharacterErrorRate.compare("欢迎大家", "欢迎大");
        CharacterErrorRate.Result insertion = CharacterErrorRate.compare("欢迎大家", "欢迎大家们");

        assertEquals(1, substitution.substitutions());
        assertEquals(0.25, substitution.rate(), 0.0001);
        assertEquals(1, deletion.deletions());
        assertEquals(0.25, deletion.rate(), 0.0001);
        assertEquals(1, insertion.insertions());
        assertEquals(0.25, insertion.rate(), 0.0001);
    }

    @Test
    void treatsTwoEmptyTextsAsACompleteMatch() {
        CharacterErrorRate.Result result = CharacterErrorRate.compare("，。", " ");

        assertEquals(0, result.errors());
        assertEquals(0, result.referenceCharacters());
        assertEquals(0, result.rate(), 0.0001);
    }
}
