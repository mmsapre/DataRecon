package com.mms.data.recon.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThrowableUtilsTest {

    private final IllegalArgumentException rootCause = new IllegalArgumentException("root");
    private final IllegalArgumentException causedByRoot = new IllegalArgumentException("causedByRoot", rootCause);
    private final IllegalArgumentException causedByCausedBy =
            new IllegalArgumentException("causedByCausedByRoot", causedByRoot);

    @Test
    void returnsJustMessageForThrowableWithNoCause() {
        assertEquals("root", ThrowableUtils.extractFailureCause(rootCause));
    }

    @Test
    void returnsMessageWithRootCauseForThrowableWithCause() {
        assertEquals("causedByRoot, rootCause=[root]", ThrowableUtils.extractFailureCause(causedByRoot));
    }

    @Test
    void ignoresIntermediaryExceptions() {
        assertEquals("causedByCausedByRoot, rootCause=[root]", ThrowableUtils.extractFailureCause(causedByCausedBy));
    }

    @Test
    void returnsExceptionTypeIfThereIsNoMessage() {
        assertEquals("IllegalArgumentException", ThrowableUtils.extractFailureCause(new IllegalArgumentException()));
    }

    @Test
    void returnsExceptionTypeIfThereIsNoMessageForThrowableWithCause() {
        IllegalArgumentException noMessageRoot = new IllegalArgumentException();
        IllegalCallerException bad = new IllegalCallerException("bad", noMessageRoot);
        assertEquals("bad, rootCause=[IllegalArgumentException]", ThrowableUtils.extractFailureCause(bad));
    }

    @Test
    void rootCauseWalksToInnermost() {
        assertEquals(rootCause, ThrowableUtils.rootCause(causedByCausedBy));
        assertEquals("root", ThrowableUtils.rootMessage(causedByCausedBy));
    }
}
