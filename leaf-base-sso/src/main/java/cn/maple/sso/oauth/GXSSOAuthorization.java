package cn.maple.sso.oauth;

import cn.hutool.core.lang.Dict;

public interface GXSSOAuthorization {
    boolean isPermitted(Dict token, String permission);
}