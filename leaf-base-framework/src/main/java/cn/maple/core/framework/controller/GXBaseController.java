package cn.maple.core.framework.controller;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrPool;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.dto.protocol.res.GXBaseResProtocol;
import cn.maple.core.framework.dto.protocol.res.GXPaginationResProtocol;
import cn.maple.core.framework.dto.res.GXBaseResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 基础控制器接口
 * <p>
 * 本接口定义了控制器层通用的功能，包括对象转换、Token处理、树形结构构建等。
 * 所有业务控制器都应该直接或间接实现此接口，以复用通用功能。
 * </p>
 * 
 * <p>
 * 安全说明：
 * 1. Token处理相关方法实现了多重安全机制，包括密钥验证、过期检查和自动续期
 * 2. 所有从Token中获取的用户信息都经过安全检查，防止伪造或篡改
 * 3. 不同类型的用户（前端用户、管理员）使用不同的密钥和缓存策略
 * 4. Token处理失败会抛出业务异常，确保未授权访问被拦截
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * @RestController
 * @RequestMapping("/api/users")
 * public class UserController implements GXBaseController {
 *     
 *     @Autowired
 *     private UserService userService;
 *     
 *     @GetMapping("/profile")
 *     public ResponseEntity<UserProfileVO> getUserProfile() {
 *         // 从Token中获取当前登录用户ID
 *         Long userId = getFrontEndUserId(GXTokenConstant.USER_TOKEN_SECRET_KEY, Long.class);
 *         
 *         // 获取用户信息
 *         UserEntity user = userService.getUserById(userId);
 *         
 *         // 将实体对象转换为视图对象
 *         UserProfileVO profileVO = convertSourceToTarget(user, UserProfileVO.class);
 *         
 *         return ResponseEntity.ok(profileVO);
 *     }
 * }
 * </pre>
 * </p>
 */
public interface GXBaseController {
    /**
     * 日志对象
     * <p>
     * 用于记录控制器层的日志信息，便于问题排查和性能分析
     * </p>
     */
    Logger LOG = LoggerFactory.getLogger(GXBaseController.class);

    /**
     * 将源对象转换为目标对象
     * <p>
     * 基础的对象转换方法，使用默认的转换选项和空的额外数据
     * </p>
     *
     * @param source 源对象
     * @param clazz  目标对象类型
     * @return 转换后的目标对象
     */
    default <S, T> T convertSourceToTarget(S source, Class<T> clazz) {
        return convertSourceToTarget(source, clazz, null, null, Dict.create());
    }

    /**
     * 将源对象转换为目标对象
     * <p>
     * 可指定自定义处理方法的对象转换
     * </p>
     *
     * @param source     源对象
     * @param clazz      目标对象类型
     * @param methodName 自定义处理方法名
     * @return 转换后的目标对象
     */
    default <S, T> T convertSourceToTarget(S source, Class<T> clazz, String methodName) {
        return convertSourceToTarget(source, clazz, methodName, null, Dict.create());
    }

    /**
     * 将源对象转换为目标对象
     * <p>
     * 可传递额外数据的对象转换
     * </p>
     *
     * @param source    源对象
     * @param clazz     目标对象类型
     * @param extraData 额外数据
     * @return 转换后的目标对象
     */
    default <S, T> T convertSourceToTarget(S source, Class<T> clazz, Dict extraData) {
        return convertSourceToTarget(source, clazz, null, null, extraData);
    }

    /**
     * 将源对象转换为目标对象
     * <p>
     * 可指定自定义处理方法和额外数据的对象转换
     * </p>
     *
     * @param source     源对象
     * @param clazz      目标对象类型
     * @param methodName 自定义处理方法名
     * @param extraData  额外数据
     * @return 转换后的目标对象
     */
    default <S, T> T convertSourceToTarget(S source, Class<T> clazz, String methodName, Dict extraData) {
        return convertSourceToTarget(source, clazz, methodName, null, extraData);
    }

    /**
     * 将源对象转换为目标对象
     * <p>
     * 可指定复制选项和额外数据的对象转换
     * </p>
     *
     * @param source      源对象
     * @param clazz       目标对象类型
     * @param copyOptions 复制选项
     * @param extraData   额外数据
     * @return 转换后的目标对象
     */
    default <S, T> T convertSourceToTarget(S source, Class<T> clazz, CopyOptions copyOptions, Dict extraData) {
        return convertSourceToTarget(source, clazz, null, copyOptions, extraData);
    }

    /**
     * 将源对象转换为目标对象
     * <p>
     * 最完整的对象转换方法，支持自定义处理方法、复制选项和额外数据
     * </p>
     *
     * @param source      源对象
     * @param clazz       目标对象类型
     * @param methodName  自定义处理方法名
     * @param copyOptions 复制选项
     * @param extraData   额外数据
     * @return 转换后的目标对象
     */
    default <S, T> T convertSourceToTarget(S source, Class<T> clazz, String methodName, CopyOptions copyOptions, Dict extraData) {
        if (Objects.isNull(methodName)) {
            methodName = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        return GXCommonUtils.convertSourceToTarget(source, clazz, methodName, copyOptions, extraData);
    }

    /**
     * 将源对象集合转换为目标对象集合
     * <p>
     * 最完整的集合转换方法，支持自定义处理方法、复制选项和额外数据
     * </p>
     *
     * @param collection  需要转换的对象集合
     * @param clazz       目标对象的类型
     * @param methodName  自定义处理方法名
     * @param copyOptions 复制选项
     * @param extraData   额外数据
     * @return 转换后的目标对象集合
     */
    default <S, T> List<T> convertSourceListToTargetList(Collection<S> collection, Class<T> clazz, String methodName, CopyOptions copyOptions, Dict extraData) {
        if (Objects.isNull(methodName)) {
            methodName = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        return GXCommonUtils.convertSourceListToTargetList(collection, clazz, methodName, copyOptions, extraData);
    }

    /**
     * 将源对象集合转换为目标对象集合
     * <p>
     * 支持自定义处理方法和复制选项的集合转换
     * </p>
     *
     * @param collection  需要转换的对象集合
     * @param clazz       目标对象的类型
     * @param methodName  自定义处理方法名
     * @param copyOptions 复制选项
     * @return 转换后的目标对象集合
     */
    default <S, T> List<T> convertSourceListToTargetList(Collection<S> collection, Class<T> clazz, String methodName, CopyOptions copyOptions) {
        return convertSourceListToTargetList(collection, clazz, methodName, copyOptions, Dict.create());
    }

    /**
     * 将源对象集合转换为目标对象集合
     * <p>
     * 基础的集合转换方法，使用默认的转换选项
     * </p>
     *
     * @param collection 需要转换的对象集合
     * @param clazz      目标对象的类型
     * @return 转换后的目标对象集合
     */
    default <S, T> List<T> convertSourceListToTargetList(Collection<S> collection, Class<T> clazz) {
        return convertSourceListToTargetList(collection, clazz, null, null, Dict.create());
    }

    /**
     * 将源对象集合转换为目标对象集合
     * <p>
     * 支持额外数据的集合转换
     * </p>
     *
     * @param collection 需要转换的对象集合
     * @param clazz      目标对象的类型
     * @param extraData  额外数据
     * @return 转换后的目标对象集合
     */
    default <S, T> List<T> convertSourceListToTargetList(Collection<S> collection, Class<T> clazz, Dict extraData) {
        return convertSourceListToTargetList(collection, clazz, null, null, extraData);
    }

    /**
     * 从token中获取登录用户字段信息
     * <p>
     * 根据指定的token名称和字段名，从当前请求的token中解析出对应的字段值
     * </p>
     * <p>
     * 安全说明：
     * 1. 使用指定的密钥进行token解密，确保token未被篡改
     * 2. 验证token的完整性和有效性，无效token将返回null
     * 3. 支持不同类型的返回值，适应各种业务场景
     * </p>
     *
     * @param tokenName      header中Token的名字，如Authorization、token、adminToken
     * @param tokenFieldName Token中包含的字段名，如id、userId、adminId等
     * @param clazz          返回值类型
     * @param secretKey      加解密密钥
     * @return 指定类型的字段值，如果token无效或字段不存在则返回null
     */
    default <R> R getLoginFieldFromToken(String tokenName, String tokenFieldName, Class<R> clazz, String secretKey) {
        R fieldFromToken = GXCurrentRequestContextUtils.getLoginFieldFromToken(tokenName, tokenFieldName, clazz, secretKey);
        if (Objects.isNull(fieldFromToken)) {
            LOG.error("token中不存在键为{}的值", tokenFieldName);
        }
        return fieldFromToken;
    }

    /**
     * 将分页数据对象转换为分页协议对象
     * <p>
     * 使用默认的转换选项进行转换
     * </p>
     *
     * @param pagination  源分页对象
     * @param targetClazz 目标类型
     * @return 转换后的分页协议对象
     */
    default <S extends GXBaseResDto, T extends GXBaseResProtocol> GXPaginationResProtocol<T> convertPaginationResToProtocol(GXPaginationResDto<S> pagination, Class<T> targetClazz) {
        return convertPaginationResToProtocol(pagination, targetClazz, null);
    }

    /**
     * 将分页数据对象转换为分页协议对象
     * <p>
     * 可指定复制选项的分页对象转换
     * </p>
     *
     * @param pagination  源分页对象
     * @param targetClazz 目标类型
     * @param copyOptions 复制选项
     * @return 转换后的分页协议对象
     */
    default <S extends GXBaseResDto, T extends GXBaseResProtocol> GXPaginationResProtocol<T> convertPaginationResToProtocol(GXPaginationResDto<S> pagination, Class<T> targetClazz, CopyOptions copyOptions) {
        List<S> records = pagination.getRecords();
        long total = pagination.getTotal();
        long pages = pagination.getPages();
        long pageSize = pagination.getPageSize();
        long currentPage = pagination.getCurrentPage();
        List<T> list = convertSourceListToTargetList(records, targetClazz, null, copyOptions);
        return new GXPaginationResProtocol<>(list, total, pages, pageSize, currentPage);
    }

    /**
     * 构建菜单树形结构
     * <p>
     * 根据源列表和父级字段的获取方法，构建树形结构
     * </p>
     *
     * @param sourceList          源列表
     * @param rootParentValue     根父级的值，通常为0
     * @param getParentMethodName 获取父级ID的方法名
     * @return 构建好的树形结构列表
     */
    default <R extends GXBaseResProtocol> List<R> buildTree(List<R> sourceList, Object rootParentValue, String getParentMethodName) {
        return GXCommonUtils.buildTree(sourceList, rootParentValue, getParentMethodName);
    }

    /**
     * 获取前端用户的登录ID
     * <p>
     * 从指定名称的token中获取用户ID
     * </p>
     * <p>
     * 安全说明：
     * 1. 使用专用的用户token密钥进行解密，确保安全性
     * 2. 验证token的完整性和有效性，防止伪造
     * 3. 支持不同类型的返回值，适应各种业务场景
     * </p>
     *
     * @param tokenName      token名称
     * @param tokenSecretKey token密钥
     * @param targetClass    返回值类型
     * @return 指定类型的用户ID
     * @throws GXBusinessException 当token密钥为空时抛出异常
     */
    default <R> R getFrontEndUserId(String tokenName, String tokenSecretKey, Class<R> targetClass) {
        if (Objects.isNull(tokenSecretKey)) {
            throw new GXBusinessException("请传递token密钥!");
        }
        return getLoginFieldFromToken(tokenName, GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, targetClass, tokenSecretKey);
    }

    /**
     * 获取前端用户的登录ID
     * <p>
     * 使用默认token名称从token中获取用户ID
     * </p>
     * <p>
     * 安全说明：
     * 1. 使用专用的用户token密钥进行解密，确保安全性
     * 2. 使用默认的token名称(GXTokenConstant.TOKEN_NAME)
     * 3. 支持不同类型的返回值，适应各种业务场景
     * </p>
     *
     * @param tokenSecretKey token密钥
     * @param targetClass    返回值类型
     * @return 指定类型的用户ID
     * @throws GXBusinessException 当token密钥为空时抛出异常
     */
    default <R> R getFrontEndUserId(String tokenSecretKey, Class<R> targetClass) {
        return getFrontEndUserId(GXTokenConstant.TOKEN_NAME, tokenSecretKey, targetClass);
    }

    /**
     * 获取管理端用户的登录ID
     * <p>
     * 从指定名称的token中获取管理员ID
     * </p>
     * <p>
     * 安全说明：
     * 1. 使用专用的管理端token密钥进行解密，确保安全性
     * 2. 验证token的完整性和有效性，防止伪造
     * 3. 支持不同类型的返回值，适应各种业务场景
     * </p>
     *
     * @param tokenName      token名称
     * @param tokenSecretKey token密钥
     * @param targetClass    返回值类型
     * @return 指定类型的管理员ID
     * @throws GXBusinessException 当token密钥为空时抛出异常
     */
    default <R> R getManagerUserId(String tokenName, String tokenSecretKey, Class<R> targetClass) {
        if (Objects.isNull(tokenSecretKey)) {
            throw new GXBusinessException("请传递token密钥!");
        }
        return getLoginFieldFromToken(tokenName, GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, targetClass, tokenSecretKey);
    }

    /**
     * 获取管理端用户的登录ID
     * <p>
     * 使用默认token名称从token中获取管理员ID
     * </p>
     * <p>
     * 安全说明：
     * 1. 使用专用的管理端token密钥进行解密，确保安全性
     * 2. 使用默认的token名称(GXTokenConstant.TOKEN_NAME)
     * 3. 支持不同类型的返回值，适应各种业务场景
     * </p>
     *
     * @param tokenSecretKey token密钥
     * @param targetClass    返回值类型
     * @return 指定类型的管理员ID
     * @throws GXBusinessException 当token密钥为空时抛出异常
     */
    default <R> R getManagerUserId(String tokenSecretKey, Class<R> targetClass) {
        return getManagerUserId(GXTokenConstant.TOKEN_NAME, tokenSecretKey, targetClass);
    }

    /**
     * 获取完整的登录凭证信息
     * <p>
     * 从指定名称的token中获取完整的登录信息
     * </p>
     * <p>
     * 安全说明：
     * 1. 使用指定的密钥进行token解密，确保token未被篡改
     * 2. 返回完整的登录凭证信息，包括用户ID、用户名、登录时间等
     * 3. 可用于需要获取更多用户信息的场景
     * </p>
     *
     * @param tokenName      token名称
     * @param tokenSecretKey token密钥
     * @return 包含完整登录信息的Dict对象
     */
    default Dict getLoginCredentials(String tokenName, String tokenSecretKey) {
        return GXCurrentRequestContextUtils.getLoginCredentials(tokenName, tokenSecretKey);
    }

    /**
     * 拼接字段的断言信息
     * <p>
     * 用于生成格式统一的字段验证失败信息
     * </p>
     *
     * @param msg       提示信息
     * @param fieldName 字段名
     * @return 格式化后的断言信息，格式为：字段名:提示信息
     */
    default String concatAssertMsg(String msg, String fieldName) {
        return CharSequenceUtil.format("{}{}{}", fieldName, StrPool.COLON, msg);
    }
}
