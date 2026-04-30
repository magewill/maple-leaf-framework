package cn.maple.core.framework.dto.protocol.req;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;

public abstract class GXBaseReqProtocol extends GXBaseReqDto {
    protected void beforeRepair() {
    }

    protected void afterRepair() {
    }

    protected Dict getLoginCredentials(String tokenName, String tokenSecretKey) {
        return GXCurrentRequestContextUtils.getLoginCredentials(tokenName, tokenSecretKey);
    }
}
