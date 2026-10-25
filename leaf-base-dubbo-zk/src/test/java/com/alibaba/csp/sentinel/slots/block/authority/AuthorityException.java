package com.alibaba.csp.sentinel.slots.block.authority;

import com.alibaba.csp.sentinel.slots.block.BlockException;

public class AuthorityException extends BlockException {
    private static final long serialVersionUID = 1L;

    public AuthorityException(String message) {
        super(message);
    }
}
