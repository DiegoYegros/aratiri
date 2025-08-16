package com.aratiri.aratiri.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasGeneratorTest {

    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-z]+[a-z]+\\d{2}$");

    @Test
    void shouldGenerateAliasMatchingPattern() {
        String alias = AliasGenerator.generateAlias();
        assertNotNull(alias, "Alias should not be null");
        assertTrue(ALIAS_PATTERN.matcher(alias).matches(),
                "Alias should match the pattern <adjective><animal><2-digit number>");
    }

    @Test
    void shouldContainValidAdjectiveAndAnimal() {
        String alias = AliasGenerator.generateAlias();

        String[] adjectives = {
                "silent", "magic", "fuzzy", "brave", "rapid", "shy", "fierce", "cosmic", "lucky"
        };
        String[] animals = {
                "koala", "penguin", "lynx", "otter", "panda", "fox", "eagle", "owl", "cat"
        };

        boolean valid = false;
        for (String adj : adjectives) {
            if (alias.startsWith(adj)) {
                for (String animal : animals) {
                    if (alias.substring(adj.length()).matches(animal + "\\d{2}")) {
                        valid = true;
                        break;
                    }
                }
            }
        }
        assertTrue(valid, "Alias should be formed from a valid adjective + animal");
    }

    @Test
    void numberShouldBeBetween10And99() {
        String alias = AliasGenerator.generateAlias();
        String numberPart = alias.replaceAll("\\D+", "");
        int number = Integer.parseInt(numberPart);
        assertTrue(number >= 10 && number <= 99, "Number should be between 10 and 99");
    }

    @Test
    void shouldProduceDifferentAliasesOverMultipleCalls() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            generated.add(AliasGenerator.generateAlias());
        }
        assertTrue(generated.size() > 1, "Should generate more than one unique alias");
    }
}
