package cn.maple.core.framework.service;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.core.framework.dto.res.GXBaseResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBeanNotExistsException;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public interface GXBusinessService {
    /**
     * 日志对象
     */
    Logger LOG = LoggerFactory.getLogger(GXBusinessService.class);

    /**
     * 加密手机号码
     *
     * @param phoneNumber 明文手机号
     * @param key         加密key
     * @return String
     */
    String encryptedPhoneNumber(String phoneNumber, String key);

    /**
     * 解密手机号码
     *
     * @param encryptPhoneNumber 加密手机号
     * @param key                解密key
     * @return String
     */
    String decryptedPhoneNumber(String encryptPhoneNumber, String key);

    /**
     * 隐藏手机号码的指定几位为指定的字符
     *
     * @param phoneNumber  手机号码
     * @param startInclude 开始字符位置(包含,从0开始)
     * @param endExclude   结束字符位置
     * @param replacedChar 替换为的字符
     * @return String
     */
    String hiddenPhoneNumber(CharSequence phoneNumber, int startInclude, int endExclude, char replacedChar);

    /**
     * 获取实体中指定指定的值
     * <pre>
     *     {@code
     *     getSingleJSONFieldValueByEntity(
     *       GoodsEntity,
     *       "ext.name",
     *       Integer.class
     *       )
     *     }
     * </pre>
     *
     * @param entity 实体对象
     * @param path   路径
     * @return R
     */
    <T, R> R getSingleFieldValueByEntity(T entity, String path, Class<R> type);

    /**
     * 获取实体中指定指定的值
     * <pre>
     *     {@code
     *     getSingleJSONFieldValueByEntity(
     *       GoodsEntity,
     *       "ext.name",
     *       Integer.class
     *       )
     *     }
     * </pre>
     *
     * @param entity       实体对象
     * @param path         路径
     * @param defaultValue 默认值
     * @return R
     */
    <T, R> R getSingleFieldValueByEntity(T entity, String path, Class<R> type, R defaultValue);

    /**
     * 将任意对象转换为指定类型的对象
     * <p>
     * {@code}
     * eg:
     * Dict source = Dict.create().set("username","britton").set("realName","枫叶思源");
     * convertSourceToTarget( source , PersonResDto.class, "customerProcess" , null);
     * OR
     * PersonReqProtocol req = new PersonReqProtocol();
     * req.setUsername("britton");
     * req.setRealName("枫叶思源")；
     * convertSourceToTarget(req ,  PersonResDto.class);
     * {code}
     *
     * @param source 源对象
     * @param tClass 目标对象类型
     * @return 目标对象
     */
    <S, T> T convertSourceToTarget(S source, Class<T> tClass);

    /**
     * 将任意对象转换为指定类型的对象
     * <p>
     * {@code}
     * eg:
     * Dict source = Dict.create().set("username","britton").set("realName","枫叶思源");
     * convertSourceToTarget( source , PersonResDto.class, "customerProcess" , null);
     * OR
     * PersonReqProtocol req = new PersonReqProtocol();
     * req.setUsername("britton");
     * req.setRealName("枫叶思源")；
     * convertSourceToTarget(req ,  PersonResDto.class, "customerProcess" , null);
     * {code}
     *
     * @param source      源对象
     * @param tClass      目标对象类型
     * @param methodName  需要条用的方法名字
     * @param copyOptions 复制选项
     * @param extraData   额外数据
     * @return 目标对象
     */
    <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions, Dict extraData);

    /**
     * 将任意对象转换为指定类型的对象
     * <p>
     * {@code}
     * eg:
     * Dict source = Dict.create().set("username","britton").set("realName","枫叶思源");
     * convertSourceToTarget( source , PersonResDto.class, "customerProcess" , null);
     * OR
     * PersonReqProtocol req = new PersonReqProtocol();
     * req.setUsername("britton");
     * req.setRealName("枫叶思源")；
     * convertSourceToTarget(req ,  PersonResDto.class, "customerProcess" , null);
     * {code}
     *
     * @param source      源对象
     * @param tClass      目标对象类型
     * @param methodName  需要条用的方法名字
     * @param copyOptions 复制选项
     * @return 目标对象
     */
    <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions);

    /**
     * 将任意对象转换为指定类型的对象
     *
     * @param collection  需要转换的对象列表
     * @param tClass      目标对象的类型
     * @param methodName  需要条用的方法名字
     * @param copyOptions 需要拷贝的选项
     * @param extraData   额外数据
     * @return List
     */
    <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass, String methodName, CopyOptions copyOptions, Dict extraData);

    /**
     * 动态调用指定底层类中的方法
     *
     * @param serveClass 底层类型 一定要是Spring Bean容器中的类型
     * @param methodName 方法名字
     * @param params     调用参数
     * @return Object
     */
    default Object callMethod(Class<?> serveClass, String methodName, Object... params) {
        return GXCommonUtils.reflectCallObjectMethod(serveClass, methodName, params);
    }

    /**
     * 动态调用指定底层类中的方法
     *
     * @param targetObject 被调用对象
     * @param methodName   方法名字
     * @param params       调用参数
     * @return Object
     */
    default Object callMethod(Object targetObject, String methodName, Object... params) {
        return GXCommonUtils.reflectCallObjectMethod(targetObject, methodName, params);
    }

    /**
     * 将GXPaginationDBResDto转换为GXPaginationResDto
     *
     * @param pagination  源分页对象
     * @param targetClazz 目标类型
     * @param methodName  方法名字
     * @param copyOptions copy选项
     * @param extraData   额外数据
     * @return GXPaginationResProtocol对象
     */
    default <S extends GXBaseDBResDto, T extends GXBaseResDto> GXPaginationResDto<T> convertPaginationDBResDtoToResDto(GXPaginationResDto<S> pagination, Class<T> targetClazz, String methodName, CopyOptions copyOptions, Dict extraData) {
        List<S> records = pagination.getRecords();
        long total = pagination.getTotal();
        long pages = pagination.getPages();
        long pageSize = pagination.getPageSize();
        long currentPage = pagination.getCurrentPage();
        List<T> list = convertSourceListToTargetList(records, targetClazz, methodName, copyOptions, extraData);
        return new GXPaginationResDto<>(list, total, pages, pageSize, currentPage);
    }

    /**
     * 将GXPaginationDBResDto转换为GXPaginationResDto
     *
     * @param pagination  源分页对象
     * @param targetClazz 目标类型
     * @return GXPaginationResProtocol对象
     */
    default <S extends GXBaseDBResDto, T extends GXBaseResDto> GXPaginationResDto<T> convertPaginationDBResDtoToResDto(GXPaginationResDto<S> pagination, Class<T> targetClazz, Dict extraData) {
        return convertPaginationDBResDtoToResDto(pagination, targetClazz, null, new CopyOptions(), extraData);
    }

    /**
     * 将GXPaginationDBResDto转换为GXPaginationResDto
     *
     * @param pagination  源分页对象
     * @param targetClazz 目标类型
     * @return GXPaginationResProtocol对象
     */
    default <S extends GXBaseDBResDto, T extends GXBaseResDto> GXPaginationResDto<T> convertPaginationDBResDtoToResDto(GXPaginationResDto<S> pagination, Class<T> targetClazz) {
        return convertPaginationDBResDtoToResDto(pagination, targetClazz, Dict.create());
    }

    /**
     * 获取前端用户的登录ID
     *
     * @param tokenName      token的名字
     * @param tokenSecretKey token密钥
     * @param targetClass    返回的数据类型
     * @return R
     */
    default <R> R getFrontEndUserId(String tokenName, String tokenSecretKey, Class<R> targetClass) {
        if (Objects.isNull(tokenSecretKey)) {
            throw new GXBusinessException("请传递token密钥!");
        }
        return getLoginFieldFromToken(tokenName, GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, targetClass, tokenSecretKey);
    }

    /**
     * 获取前端用户的登录ID
     *
     * @param tokenSecretKey token密钥
     * @param targetClass    返回的数据类型
     * @return R
     */
    default <R> R getFrontEndUserId(String tokenSecretKey, Class<R> targetClass) {
        return getFrontEndUserId(GXTokenConstant.TOKEN_NAME, tokenSecretKey, targetClass);
    }

    /**
     * 获取后端用户的登录ID(管理端)
     *
     * @param tokenName      token的名字
     * @param tokenSecretKey token密钥
     * @param targetClass    返回的数据类型
     * @return R
     */
    default <R> R getManagerUserId(String tokenName, String tokenSecretKey, Class<R> targetClass) {
        if (Objects.isNull(tokenSecretKey)) {
            throw new GXBusinessException("请传递token密钥!");
        }
        return getLoginFieldFromToken(tokenName, GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, targetClass, tokenSecretKey);
    }

    /**
     * 获取后端用户的登录ID(管理端)
     *
     * @param tokenSecretKey token密钥
     * @param targetClass    返回的数据类型
     * @return R
     */
    default <R> R getManagerUserId(String tokenSecretKey, Class<R> targetClass) {
        return getManagerUserId(GXTokenConstant.TOKEN_NAME, tokenSecretKey, targetClass);
    }

    /**
     * 从token中获取登录用户ID
     *
     * @param tokenName      header中Token的名字 eg : Authorization、token、adminToken
     * @param tokenFieldName Token中包含的ID名字 eg : id、userId、adminId....
     * @param clazz          返回值类型
     * @param secretKey      加解密KEY
     * @return R
     */
    default <R> R getLoginFieldFromToken(String tokenName, String tokenFieldName, Class<R> clazz, String secretKey) {
        R fieldFromToken = GXCurrentRequestContextUtils.getLoginFieldFromToken(tokenName, tokenFieldName, clazz, secretKey);
        if (Objects.isNull(fieldFromToken)) {
            LOG.error("token中不存在键为{}的值", tokenFieldName);
        }
        return fieldFromToken;
    }

    /**
     * 自定义获取数据 可以从缓存获取
     *
     * @param cacheKey 缓存key
     * @return 缓存数据
     */
    default Object getDataFromCache(String cacheKey, Object... params) {
        GXBaseCacheService cacheService = getCacheService();
        if (Objects.nonNull(cacheService)) {
            return cacheService.getCache(getCacheBucketName(), cacheKey);
        }
        return null;
    }

    /**
     * 缓存数据操作
     *
     * @param cacheKey 缓存key
     * @param data     需要缓存的数据
     */
    default void setCacheData(String cacheKey, Object data, Object... params) {
        GXBaseCacheService cacheService = getCacheService();
        GXBaseCacheLockService cacheLockService = Objects.requireNonNull(GXSpringContextUtils.getBean(GXBaseCacheLockService.class));
        if (Objects.nonNull(cacheService)) {
            try {
                cacheLockService.tryLock(cacheKey);
                Object dataFromCache = getDataFromCache(cacheKey, params);
                if (Objects.isNull(dataFromCache)) {
                    cacheService.setCache(getCacheBucketName(), cacheKey, data);
                }
            } finally {
                cacheLockService.releaseLock(cacheKey);
            }
        }
    }

    /**
     * 失效缓存数据
     *
     * @param cacheKey 缓存key
     */
    default void evictCacheData(String cacheKey, Object... params) {
        GXBaseCacheService cacheService = getCacheService();
        if (Objects.nonNull(cacheService)) {
            cacheService.deleteCache(getCacheBucketName(), cacheKey);
        }
    }

    /**
     * 获取缓存服务对象
     *
     * @return 缓存服务对象
     */
    default GXBaseCacheService getCacheService() {
        GXBaseCacheService cacheService = GXSpringContextUtils.getBean(GXBaseCacheService.class);
        if (Objects.nonNull(cacheService)) {
            return cacheService;
        }
        LOG.warn("请提供缓存组件");
        return null;
    }

    /**
     * 获取默认的Cache Bucket
     *
     * @return Cache Bucket
     */
    default String getCacheBucketName() {
        String s = ReUtil.replaceAll(getClass().getSimpleName(), "GX|ServiceImpl", "");
        return "mapleaf_default_cache:" + CharSequenceUtil.toUnderlineCase(s) + "_bucket";
    }

    /**
     * 获取登录用户的信息
     *
     * @return 登录用户的token
     */
    default Dict getLoginCredentials() {
        try {
            Class<?> tokenConfigServiceKlass = ClassUtils.forName("cn.maple.sso.service.GXTokenConfigService", ClassUtils.getDefaultClassLoader());
            Object tokenConfigService = GXSpringContextUtils.getBean(tokenConfigServiceKlass);
            if (ObjectUtil.isNull(tokenConfigService)) {
                throw new GXBeanNotExistsException("'cn.maple.sso.service.GXTokenConfigService' Bean is not defined!");
            }
            String tokenSecret = (String) GXCommonUtils.reflectCallObjectMethod(tokenConfigService, "getTokenSecret");
            return GXCurrentRequestContextUtils.getLoginCredentials(GXTokenConstant.TOKEN_NAME, tokenSecret);
        } catch (ClassNotFoundException e) {
            LOG.error("'cn.maple.sso.service.GXTokenConfigService' class not found! please import 'leaf-base-sso' module!");
            return Dict.create();
            //throw new GXBusinessException(e.getMessage(), e);
        }
    }

    /**
     * 获取登录用户的ID
     *
     * @return 登录的用户ID
     */
    default Long getLoginUserId() {
        Dict dict = getLoginCredentials();
        return dict.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
    }

    /**
     * 获取用户登录的用户名名
     *
     * @return 登录的用户名
     */
    default String getLoginUserName() {
        if (GXCurrentRequestContextUtils.isHTTP() && GXCurrentRequestContextUtils.tokenExists()) {
            Dict dict = getLoginCredentials();
            return dict.getStr(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME);
        }
        LOG.info("不是HTTP请求,不能获取登录的用户名!");
        return "RPC_USER";
    }
}
