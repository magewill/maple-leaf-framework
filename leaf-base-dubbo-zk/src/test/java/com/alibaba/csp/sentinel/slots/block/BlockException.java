package com.alibaba.csp.sentinel.slots.block;

/**
 * Test stub that mimics the Sentinel block exception base type without adding a Sentinel dependency.
 */
public class BlockException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BlockException(String message) {
        super(message);
    }
}
