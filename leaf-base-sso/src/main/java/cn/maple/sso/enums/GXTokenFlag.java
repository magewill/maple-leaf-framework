package cn.maple.sso.enums;

/**
 * <p>
 * Token 状态标记枚举
 * </p>
 * 
 * <p>
 * 定义了SSO系统中Token的不同状态，用于标识Token的处理方式和有效性。
 * 主要应用场景：
 * 1. 标识Token的正常/异常状态
 * 2. 处理缓存系统异常情况下的降级策略
 * 3. 在分布式环境中同步Token状态
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 检查Token状态并处理
 * Dict tokenData = GXSSOHelperUtil.getSSOToken(request);
 * Integer flagValue = tokenData.getInt("flag");
 * GXTokenFlag flag = GXTokenFlag.fromValue(flagValue.toString());
 * 
 * switch (flag) {
 *     case NORMAL:
 *         // 正常处理逻辑
 *         processNormalToken(tokenData);
 *         break;
 *     case CACHE_SHUT:
 *         // 缓存宕机时的降级处理
 *         processCacheShutdownToken(tokenData);
 *         break;
 *     default:
 *         // 未知状态处理
 *         handleUnknownTokenState(tokenData);
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public enum GXTokenFlag {
    /**
     * 正常状态
     * 表示Token处于正常状态，可以正常使用
     */
    NORMAL(0, "正常"),

    /**
     * 缓存宕机状态
     * 表示缓存系统不可用，Token处于降级模式
     */
    CACHE_SHUT(1, "缓存宕机");

    /**
     * 状态值
     * 用于在存储和传输中标识Token状态
     */
    private final Integer value;

    /**
     * 状态描述
     * 对状态的文字说明
     */
    private final String desc;

    GXTokenFlag(final Integer value, final String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 根据状态值获取对应的Token状态枚举
     * <p>
     * 如果找不到对应的状态值，则默认返回NORMAL状态
     * </p>
     *
     * @param value 状态值字符串
     * @return 对应的Token状态枚举，如果未找到则返回NORMAL
     */
    public static GXTokenFlag fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return NORMAL;
        }
        
        try {
            int intValue = Integer.parseInt(value);
            return fromValue(intValue);
        } catch (NumberFormatException e) {
            return NORMAL;
        }
    }
    
    /**
     * 根据状态值获取对应的Token状态枚举
     * <p>
     * 如果找不到对应的状态值，则默认返回NORMAL状态
     * </p>
     *
     * @param value 状态值整数
     * @return 对应的Token状态枚举，如果未找到则返回NORMAL
     */
    public static GXTokenFlag fromValue(Integer value) {
        if (value == null) {
            return NORMAL;
        }
        
        for (GXTokenFlag it : values()) {
            if (it.value().equals(value)) {
                return it;
            }
        }
        return NORMAL;
    }

    /**
     * 获取状态值
     *
     * @return 状态值整数
     */
    public Integer value() {
        return this.value;
    }

    /**
     * 获取状态描述
     *
     * @return 状态描述字符串
     */
    public String desc() {
        return this.desc;
    }
    
    /**
     * 判断当前状态是否为正常状态
     *
     * @return 如果是NORMAL状态返回true，否则返回false
     */
    public boolean isNormal() {
        return this == NORMAL;
    }
    
    /**
     * 判断当前状态是否为缓存宕机状态
     *
     * @return 如果是CACHE_SHUT状态返回true，否则返回false
     */
    public boolean isCacheShut() {
        return this == CACHE_SHUT;
    }
}
