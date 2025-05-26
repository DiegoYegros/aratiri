package com.aratiri.aratiri.utils;

import java.util.Random;

public class AliasGenerator {
    private static final String[] ADJECTIVES = {
            "silent", "magic", "fuzzy", "brave", "rapid", "shy", "fierce", "cosmic", "lucky"
    };

    private static final String[] ANIMALS = {
            "koala", "penguin", "lynx", "otter", "panda", "fox", "eagle", "owl", "cat"
    };

    private static final Random random = new Random();

    public static String generateAlias() {
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String animal = ANIMALS[random.nextInt(ANIMALS.length)];
        int number = random.nextInt(90) + 10;
        return adjective + "_" + animal + "_" + number;
    }
}
