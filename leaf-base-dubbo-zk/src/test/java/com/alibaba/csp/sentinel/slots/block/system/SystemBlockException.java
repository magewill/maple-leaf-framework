package com.alibaba.csp.sentinel.slots.block.system;

import com.alibaba.csp.sentinel.slots.block.BlockException;

public class SystemBlockException extends BlockException {
    private static final long serialVersionUID = 1L;

    public SystemBlockException(String message) {
        super(message);
    }
}
