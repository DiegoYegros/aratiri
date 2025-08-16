package com.aratiri.aratiri.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NostrUtilTest {
    @Test
    void testNpubToHex() {
        String npub = "npub1p3rfw7wscmzfn9z3fa74nzgyqe70p57j8mws0e88dh7awjepmzcq7jgxl9";
        String expectedHex = "0c469779d0c6c49994514f7d598904067cf0d3d23edd07e4e76dfdd74b21d8b0";
        assertEquals(expectedHex, NostrUtil.npubToHex(npub));
    }
}
