package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class OutageQueueTest {

    @Test public void queueFromConfigJson() {
        assertEquals("2.2", Config.outageQueue("{\"outage_queue\":\"2.2\"}"));
    }

    @Test public void missingOrMalformed_isNull() {
        assertNull(Config.outageQueue(null));
        assertNull(Config.outageQueue("{}"));
        assertNull(Config.outageQueue("{\"outage_queue\":\"7.3\"}"));
        assertNull(Config.outageQueue("{\"outage_queue\":22}"));
        assertNull(Config.outageQueue("nope"));
    }
}
