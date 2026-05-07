package cn.maple.sso.dto;

import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class GXUserInfoDto extends GXBaseDto {
    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    private String mobile;

    private String email;

    private List<String> roles;

    private List<String> permissions;

    private Integer status;

    private String loginIp;

    private LocalDateTime loginTime;

    private LocalDateTime expireTime;

    private String loginDevice;

    private String loginOrigin;

    private String extraData;
}
