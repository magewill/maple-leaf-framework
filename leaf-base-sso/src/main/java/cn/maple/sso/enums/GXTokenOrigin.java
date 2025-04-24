package cn.maple.sso.enums;

/**
 * <p>
 * Token 登录授权来源枚举
 * </p>
 * 
 * <p>
 * 定义了SSO系统中Token的不同来源平台，用于区分不同客户端的登录方式。
 * 主要应用场景：
 * 1. 区分不同平台的登录验证逻辑
 * 2. 针对不同平台设置不同的Token有效期
 * 3. 支持多平台同时登录的用户会话管理
 * 4. 统计分析不同平台的用户登录情况
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 根据请求头判断Token来源
 * String platform = request.getHeader("platform");
 * GXTokenOrigin origin = GXTokenOrigin.fromValue(platform);
 * 
 * // 根据来源设置不同的Token有效期
 * int expireTime;
 * switch (origin) {
 *     case COOKIE:
 *         expireTime = 30 * 60; // Web端30分钟
 *         break;
 *     case HTML5:
 *         expireTime = 60 * 60; // H5端1小时
 *         break;
 *     case IOS:
 *     case ANDROID:
 *         expireTime = 7 * 24 * 60 * 60; // 移动端7天
 *         break;
 *     default:
 *         expireTime = 2 * 60 * 60; // 默认2小时
 * }
 * 
 * // 设置Token数据
 * Dict tokenData = Dict.create()
 *     .set("userId", userId)
 *     .set("platform", origin.value())
 *     .set("expireTime", expireTime);
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public enum GXTokenOrigin {
    /**
     * Cookie方式
     * 传统Web应用通过Cookie传递Token的方式
     */
    COOKIE("0", "cookie"),
    
    /**
     * HTML5方式
     * 移动端H5应用或单页应用通过Header传递Token的方式
     */
    HTML5("1", "html5"),
    
    /**
     * iOS方式
     * 苹果iOS原生应用通过Header传递Token的方式
     */
    IOS("2", "apple ios"),
    
    /**
     * Android方式
     * 谷歌Android原生应用通过Header传递Token的方式
     */
    ANDROID("3", "google android");

    /**
     * 来源值
     * 用于在存储和传输中标识Token来源
     */
    private final String value;

    /**
     * 来源描述
     * 对来源的文字说明
     */
    private final String desc;

    GXTokenOrigin(final String value, final String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 根据来源值获取对应的Token来源枚举
     * <p>
     * 如果找不到对应的来源值，则默认返回COOKIE来源
     * </p>
     *
     * @param value 来源值字符串
     * @return 对应的Token来源枚举，如果未找到则返回COOKIE
     */
    public static GXTokenOrigin fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return COOKIE;
        }
        
        for (GXTokenOrigin it : values()) {
            if (it.value().equals(value)) {
                return it;
            }
        }
        return COOKIE;
    }
    
    /**
     * 根据来源描述获取对应的Token来源枚举
     * <p>
     * 不区分大小写，支持部分匹配，如果找不到对应的描述，则默认返回COOKIE来源
     * </p>
     *
     * @param desc 来源描述字符串
     * @return 对应的Token来源枚举，如果未找到则返回COOKIE
     */
    public static GXTokenOrigin fromDesc(String desc) {
        if (desc == null || desc.isEmpty()) {
            return COOKIE;
        }
        
        String lowerDesc = desc.toLowerCase();
        for (GXTokenOrigin it : values()) {
            if (it.desc().toLowerCase().contains(lowerDesc)) {
                return it;
            }
        }
        return COOKIE;
    }

    /**
     * 获取来源值
     *
     * @return 来源值字符串
     */
    public String value() {
        return this.value;
    }

    /**
     * 获取来源描述
     *
     * @return 来源描述字符串
     */
    public String desc() {
        return this.desc;
    }
    
    /**
     * 判断当前来源是否为移动端
     * <p>
     * 移动端包括IOS和ANDROID
     * </p>
     *
     * @return 如果是移动端返回true，否则返回false
     */
    public boolean isMobile() {
        return this == IOS || this == ANDROID;
    }
    
    /**
     * 判断当前来源是否为Web端
     * <p>
     * Web端包括COOKIE和HTML5
     * </p>
     *
     * @return 如果是Web端返回true，否则返回false
     */
    public boolean isWeb() {
        return this == COOKIE || this == HTML5;
    }
}
