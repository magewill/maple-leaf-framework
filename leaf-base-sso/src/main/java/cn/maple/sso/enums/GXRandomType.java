package cn.maple.sso.enums;

/**
 * <p>
 * 随机字符生成类型枚举
 * </p>
 * 
 * <p>
 * 定义了系统中随机字符串生成的不同类型，用于：
 * 1. 验证码生成 - 支持数字、字母、混合类型的验证码
 * 2. 临时密码生成 - 可生成不同复杂度的临时密码
 * 3. 随机标识符 - 用于生成各类随机ID和标识符
 * 4. 测试数据生成 - 便于测试时快速生成不同类型的随机数据
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 生成6位数字验证码
 * String numCode = GXRandomUtil.getText(GXRandomType.NUMBER, 6);
 * 
 * // 生成8位字母数字混合密码
 * String password = GXRandomUtil.getText(GXRandomType.MIX, 8);
 * 
 * // 生成4位汉字验证码
 * String chineseCode = GXRandomUtil.getText(GXRandomType.CHINESE, 4);
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public enum GXRandomType {
    /**
     * 字母数字混合
     * 生成的字符串包含大小写字母和数字
     * 适用于：密码生成、标识符生成等需要一定复杂度的场景
     */
    MIX,

    /**
     * 数字
     * 生成的字符串仅包含数字0-9
     * 适用于：短信验证码、数字ID等纯数字场景
     */
    NUMBER,

    /**
     * 字母
     * 生成的字符串仅包含大小写字母
     * 适用于：字母验证码、字母标识符等不含数字的场景
     */
    CHARACTER,

    /**
     * 汉字
     * 生成的字符串包含常用汉字
     * 适用于：中文验证码、随机中文名称生成等场景
     */
    CHINESE;
    
    /**
     * 根据名称获取枚举实例
     * <p>
     * 不区分大小写，支持模糊匹配，找不到时默认返回MIX类型
     * </p>
     *
     * @param name 枚举名称
     * @return 对应的枚举实例，如果找不到则返回MIX
     */
    public static GXRandomType fromName(String name) {
        if (name == null || name.isEmpty()) {
            return MIX;
        }
        
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试模糊匹配
            for (GXRandomType type : values()) {
                if (type.name().toUpperCase().contains(name.toUpperCase())) {
                    return type;
                }
            }
            return MIX; // 默认返回MIX类型
        }
    }
    
    /**
     * 判断当前类型是否为纯数字类型
     *
     * @return 如果是NUMBER类型返回true，否则返回false
     */
    public boolean isNumberOnly() {
        return this == NUMBER;
    }
    
    /**
     * 判断当前类型是否包含字母
     *
     * @return 如果是CHARACTER或MIX类型返回true，否则返回false
     */
    public boolean containsLetters() {
        return this == CHARACTER || this == MIX;
    }
    
    /**
     * 判断当前类型是否为中文类型
     *
     * @return 如果是CHINESE类型返回true，否则返回false
     */
    public boolean isChinese() {
        return this == CHINESE;
    }
}