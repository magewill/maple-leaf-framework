package com.alibaba.csp.sentinel.slots.block.param;

import com.alibaba.csp.sentinel.slots.block.BlockException;

public class ParamFlowException extends BlockException {
    private static final long serialVersionUID = 1L;

    public ParamFlowException(String message) {
        super(message);
    }
}
