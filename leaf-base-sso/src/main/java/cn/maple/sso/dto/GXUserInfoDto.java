package cn.maple.sso.dto;

import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户信息数据传输对象
 * </p>
 * 
 * <p>
 * 用于在SSO系统中传递用户基本信息和认证信息，包括：
 * 1. 用户基本信息（ID、用户名、昵称等）
 * 2. 认证相关信息（登录时间、过期时间等）
 * 3. 权限相关信息（角色、权限列表等）
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建用户信息对象
 * GXUserInfoDto userInfo = new GXUserInfoDto();
 * userInfo.setUserId(10001L);
 * userInfo.setUsername("zhangsan");
 * userInfo.setNickname("张三");
 * userInfo.setRoles(Arrays.asList("admin", "user"));
 * 
 * // 设置登录信息
 * userInfo.setLoginIp("192.168.1.100");
 * userInfo.setLoginTime(LocalDateTime.now());
 * userInfo.setExpireTime(LocalDateTime.now().plusHours(2));
 * 
 * // 转换为Token数据
 * Dict tokenData = Dict.create()
 *     .set("userId", userInfo.getUserId())
 *     .set("username", userInfo.getUsername())
 *     .set("roles", String.join(",", userInfo.getRoles()));
 * 
 * // 设置到SSO系统
 * GXSSOHelperUtil.setCookie(request, response, tokenData, true);
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GXUserInfoDto extends GXBaseDto {
    /**
     * 用户ID
     * 系统中唯一标识用户的主键
     */
    private Long userId;
    
    /**
     * 用户名
     * 用户登录系统的账号名
     */
    private String username;
    
    /**
     * 用户昵称
     * 用户在系统中显示的名称
     */
    private String nickname;
    
    /**
     * 用户头像URL
     * 用户头像的访问地址
     */
    private String avatar;
    
    /**
     * 用户手机号
     * 用于接收验证码或通知
     */
    private String mobile;
    
    /**
     * 用户邮箱
     * 用于接收系统通知或找回密码
     */
    private String email;
    
    /**
     * 用户角色列表
     * 用户在系统中拥有的角色，用于权限控制
     */
    private List<String> roles;
    
    /**
     * 用户权限列表
     * 用户在系统中拥有的具体权限
     */
    private List<String> permissions;
    
    /**
     * 用户状态
     * 0-禁用，1-正常
     */
    private Integer status;
    
    /**
     * 登录IP
     * 用户本次登录的IP地址
     */
    private String loginIp;
    
    /**
     * 登录时间
     * 用户本次登录的时间
     */
    private LocalDateTime loginTime;
    
    /**
     * 过期时间
     * 本次登录的过期时间
     */
    private LocalDateTime expireTime;
    
    /**
     * 登录设备
     * 用户登录的设备信息
     */
    private String loginDevice;
    
    /**
     * 登录来源
     * 参考GXTokenOrigin枚举
     */
    private String loginOrigin;
    
    /**
     * 额外数据
     * 存储不固定的扩展信息
     */
    private String extraData;
}
