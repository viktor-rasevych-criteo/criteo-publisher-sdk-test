package com.criteo.publisher.model;

import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;

public class BidTokenTest {

    private BidToken token1;
    private BidToken token2;

    @Test
    public void testTokensWithDifferentUUID() {
        UUID uuid1 = UUID.nameUUIDFromBytes("TEST_STRING1".getBytes());
        UUID uuid2 = UUID.nameUUIDFromBytes("TEST_STRING2".getBytes());
        token1 = new BidToken(uuid1);
        token2 = new BidToken(uuid2);

        Assert.assertNotEquals(token1, token2);
    }

    @Test
    public void testTokensWithNullUUID() {
        UUID uuid1 = UUID.nameUUIDFromBytes("TEST_STRING1".getBytes());
        UUID uuid2 = null;
        token1 = new BidToken(uuid1);
        token2 = new BidToken(uuid2);

        Assert.assertNotEquals(token1, token2);
    }

    @Test
    public void testTokensWithSameUUID() {
        UUID uuid1 = UUID.nameUUIDFromBytes("TEST_STRING".getBytes());
        UUID uuid2 = UUID.nameUUIDFromBytes("TEST_STRING".getBytes());
        token1 = new BidToken(uuid1);
        token2 = new BidToken(uuid2);

        Assert.assertEquals(token1, token2);
    }

}