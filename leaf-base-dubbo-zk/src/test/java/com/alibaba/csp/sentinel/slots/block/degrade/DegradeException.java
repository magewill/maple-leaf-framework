package com.alibaba.csp.sentinel.slots.block.degrade;

import com.alibaba.csp.sentinel.slots.block.BlockException;

public class DegradeException extends BlockException {
    private static final long serialVersionUID = 1L;

    public DegradeException(String message) {
        super(message);
    }
}
