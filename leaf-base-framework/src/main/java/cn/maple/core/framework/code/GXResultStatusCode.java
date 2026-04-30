package cn.maple.core.framework.code;

import cn.hutool.core.lang.Dict;

@SuppressWarnings("all")
public interface GXResultStatusCode {
    int getCode();

    String getMsg();
    
    default Dict getExtraData() {
        return Dict.create();
    }
}
