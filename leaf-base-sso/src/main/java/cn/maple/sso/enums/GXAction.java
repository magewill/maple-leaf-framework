package cn.maple.sso.enums;

/**
 * <p>
 * SSO 执行状态枚举
 * </p>
 * 
 * <p>
 * 定义了SSO系统中权限验证的执行状态，用于控制是否执行权限验证逻辑。
 * 主要应用场景：
 * 1. 拦截器中判断是否需要进行权限验证
 * 2. 配置某些URL或资源是否需要登录验证
 * 3. 特殊场景下临时跳过权限验证
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 判断当前请求是否需要执行权限验证
 * String actionKey = request.getParameter("action");
 * GXAction action = GXAction.fromKey(actionKey);
 * if (action == GXAction.Skip) {
 *     // 跳过权限验证逻辑
 *     return true;
 * }
 * 
 * // 执行正常的权限验证
 * Dict token = GXSSOHelperUtil.getSSOToken(request);
 * if (token.isEmpty()) {
 *     // 未登录，重定向到登录页
 *     GXSSOHelperUtil.clearRedirectLogin(request, response);
 *     return false;
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-15
 */
public enum GXAction {
    /**
     * 正常（默认）
     * 表示需要执行权限验证，确保用户已登录
     */
    Normal("0", "执行权限验证"),

    /**
     * 跳过
     * 表示跳过权限验证，允许未登录访问
     */
    Skip("1", "跳过权限验证");

    /**
     * 主键
     * 用于在配置和请求参数中标识执行状态
     */
    private final String key;

    /**
     * 描述
     * 对执行状态的文字说明
     */
    private final String desc;

    GXAction(final String key, final String desc) {
        this.key = key;
        this.desc = desc;
    }

    /**
     * 获取执行状态的键值
     *
     * @return 执行状态的键值字符串
     */
    public String getKey() {
        return this.key;
    }

    /**
     * 获取执行状态的描述
     *
     * @return 执行状态的描述字符串
     */
    public String getDesc() {
        return this.desc;
    }
    
    /**
     * 根据键值获取对应的执行状态枚举
     * <p>
     * 如果找不到对应的键值，则默认返回Normal状态
     * </p>
     *
     * @param key 执行状态的键值
     * @return 对应的执行状态枚举，如果未找到则返回Normal
     */
    public static GXAction fromKey(String key) {
        if (key == null || key.isEmpty()) {
            return Normal;
        }
        
        for (GXAction action : values()) {
            if (action.getKey().equals(key)) {
                return action;
            }
        }
        return Normal; // 默认返回Normal状态
    }
    
    /**
     * 判断当前执行状态是否为跳过验证
     *
     * @return 如果是Skip状态返回true，否则返回false
     */
    public boolean isSkip() {
        return this == Skip;
    }
    
    /**
     * 判断当前执行状态是否为正常验证
     *
     * @return 如果是Normal状态返回true，否则返回false
     */
    public boolean isNormal() {
        return this == Normal;
    }
}
