package com.alibaba.csp.sentinel.slots.block.authority;

/**
 * Test-only stand-in that reproduces the class name of Sentinel's authority exception without
 * adding a compile dependency on sentinel-core.
 */
public class AuthorityException extends RuntimeException {
    public AuthorityException(String message) {
        super(message);
    }
}
