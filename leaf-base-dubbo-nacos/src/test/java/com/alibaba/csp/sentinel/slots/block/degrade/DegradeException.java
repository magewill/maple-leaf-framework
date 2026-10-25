package com.alibaba.csp.sentinel.slots.block.degrade;

/**
 * Test-only stand-in that reproduces the class name of Sentinel's degrade exception without
 * adding a compile dependency on sentinel-core.
 */
public class DegradeException extends RuntimeException {
    public DegradeException(String message) {
        super(message);
    }
}
