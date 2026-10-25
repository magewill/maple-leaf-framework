package com.alibaba.csp.sentinel.slots.block.flow;

import com.alibaba.csp.sentinel.slots.block.BlockException;

public class FlowException extends BlockException {
    private static final long serialVersionUID = 1L;

    public FlowException(String message) {
        super(message);
    }
}
