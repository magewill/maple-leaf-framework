package cn.maple.sso.web.support;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.support.GXCustomerHandlerMethodArgumentResolver;
import cn.maple.sso.annotation.GXLoginUserAnnotation;
import cn.maple.sso.dto.GXUserInfoDto;
import cn.maple.sso.service.GXUUserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 登录用户参数解析器
 * <p>
 * 该解析器用于处理带有@GXLoginUserAnnotation注解的方法参数，自动注入当前登录用户信息。
 * 主要功能：
 * 1. 检测方法参数是否需要注入用户信息
 * 2. 从请求中获取用户ID
 * 3. 根据用户ID获取完整的用户信息并注入到方法参数
 * </p>
 * <p>
 * 安全说明：
 * - 支持从请求属性和请求头两种方式获取用户ID
 * - 对用户ID进行有效性验证
 * - 确保用户服务可用时才进行注入
 * </p>
 */
@Component
@ConditionalOnBean(value = {GXUUserService.class})
public class GXLoginUserHandlerMethodArgumentResolver implements GXCustomerHandlerMethodArgumentResolver {
    /**
     * 判断是否支持参数注入
     * <p>
     * 检查参数是否同时满足以下条件：
     * 1. 参数上有@GXLoginUserAnnotation注解
     * 2. 参数类型是GXUserInfoDto的子类
     * </p>
     *
     * @param parameter 方法参数
     * @return 如果支持注入返回true，否则返回false
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        /*return parameter.getParameterType().getSuperclass().isAssignableFrom(GXUUserEntity.class)
                && parameter.hasParameterAnnotation(GXLoginUserAnnotation.class);*/
        return parameter.hasParameterAnnotation(GXLoginUserAnnotation.class) &&
                parameter.getParameterType().getSuperclass().isAssignableFrom(GXUserInfoDto.class);
    }

    /**
     * 解析参数值
     * <p>
     * 从请求中获取用户ID，然后通过用户服务获取完整的用户信息
     * 获取用户ID的优先级：
     * 1. 首先从请求属性中获取
     * 2. 如果请求属性中没有，则尝试从请求头中获取并解析
     * </p>
     * <p>
     * 安全说明：
     * - 多重检查确保用户ID有效
     * - 使用专用的解密工具处理token
     * - 确保用户服务存在且可用
     * </p>
     *
     * @param parameter 方法参数
     * @param container 模型和视图容器
     * @param request   原生Web请求
     * @param factory   数据绑定工厂
     * @return 用户信息对象，如果无法获取则返回null
     * @throws Exception 如果解析过程中发生错误
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory factory) throws Exception {
        // 首先尝试从请求属性中获取用户ID
        Object object = request.getAttribute(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, RequestAttributes.SCOPE_REQUEST);

        // 如果请求属性中没有用户ID，则尝试从请求头中获取
        if (object == null) {
            final String header = request.getHeader(GXTokenConstant.USER_TOKEN_NAME);
            if (null == header) {
                return null; // 请求头中也没有token，无法获取用户信息
            }

            // 解析请求头中的token
            final Dict tokenData = JSONUtil.toBean(
                    GXAuthCodeUtils.authCodeDecode(header, GXTokenConstant.USER_TOKEN_SECRET_KEY),
                    Dict.class
            );

            // 从token中提取用户ID
            object = tokenData.getObj(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
            if (null == object) {
                return null; // token中没有用户ID，无法获取用户信息
            }
        }

        // 转换用户ID为Long类型
        Long userId = Convert.toLong(object);

        // 通过用户服务获取完整的用户信息
        GXUUserService userService = GXSpringContextUtils.getBean(GXUUserService.class);
        if (userService == null) {
            return null; // 用户服务不可用
        }

        return userService.getUserByUserId(userId);
    }
}
