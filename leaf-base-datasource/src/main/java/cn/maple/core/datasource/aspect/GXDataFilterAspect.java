package cn.maple.core.datasource.aspect;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.datasource.dto.GXDataFilterInnerDto;
import cn.maple.core.datasource.service.GXDataScopeService;
import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 数据权限过滤，切面处理类
 * <p>
 * 该切面用于处理数据权限过滤，通过拦截标注了@GXDataFilter注解的方法，
 * 在方法执行前根据用户权限动态添加SQL过滤条件，实现数据权限控制。
 * 支持超级管理员不受数据权限限制的特性。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在Service方法上使用
 * @GXDataFilter(tableAlias = "t1", userIdFieldNames = {"creator_id", "user_id"})
 * public List<UserEntity> getUserList(Map<String, Object> params) {
 *     return baseDao.selectByMap(params);
 * }
 * 
 * // 2. 在自定义Mapper方法上使用
 * @GXDataFilter(tableAlias = "t", deptIdFieldNames = {"department_id"})
 * List<OrderEntity> getOrdersByDept(Map<String, Object> params);
 * 
 * // 3. 同时过滤用户ID和部门ID
 * @GXDataFilter(tableAlias = "o", userIdFieldNames = {"creator_id"}, deptIdFieldNames = {"dept_id"})
 * List<OrderEntity> getMyDeptOrders(Map<String, Object> params);
 * 
 * // 4. 在复杂查询中使用
 * @GXDataFilter(tableAlias = "u", userIdFieldNames = {"u.creator_id"})
 * @Select("SELECT u.*, d.name as dept_name FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.id WHERE u.status = 1")
 * List<UserDetailVO> getUserDetailList();
 * 
 * // 5. 实现GXDataScopeService接口
 * @Service
 * public class DataScopeServiceImpl implements GXDataScopeService {
 *     @Autowired
 *     private UserService userService;
 *     
 *     @Override
 *     public boolean isSuperAdmin() {
 *         // 判断当前用户是否为超级管理员的逻辑
 *         return SecurityUtils.getCurrentUser().isSuperAdmin();
 *     }
 *     
 *     @Override
 *     public String getSqlFilter(GXDataFilter dataFilter, JoinPoint joinPoint) {
 *         // 获取当前用户ID
 *         Long userId = SecurityUtils.getCurrentUserId();
 *         
 *         // 构建SQL过滤条件
 *         StringBuilder sqlFilter = new StringBuilder();
 *         String tableAlias = dataFilter.tableAlias();
 *         
 *         // 添加用户ID过滤条件
 *         String[] userIdFields = dataFilter.userIdFieldNames();
 *         if (userIdFields.length > 0) {
 *             sqlFilter.append(" AND (");
 *             for (int i = 0; i < userIdFields.length; i++) {
 *                 if (i > 0) {
 *                     sqlFilter.append(" OR ");
 *                 }
 *                 sqlFilter.append(tableAlias).append(".").append(userIdFields[i])
 *                         .append(" = ").append(userId);
 *             }
 *             sqlFilter.append(")");
 *         }
 *         
 *         // 添加部门ID过滤条件
 *         // ...
 *         
 *         return sqlFilter.toString();
 *     }
 * }
 * </pre>
 * 
 * <p>工作原理：</p>
 * <ol>
 *   <li>通过AOP拦截标记了@GXDataFilter注解的方法</li>
 *   <li>根据当前用户的权限信息构建SQL过滤条件</li>
 *   <li>将过滤条件存储在ThreadLocal中</li>
 *   <li>MyBatis拦截器在执行SQL前会从ThreadLocal获取过滤条件并添加到SQL中</li>
 *   <li>方法执行完成后自动清理ThreadLocal，防止内存泄漏</li>
 * </ol>
 * 
 * <p>内存安全：</p>
 * <ol>
 *   <li>使用ThreadLocal存储线程相关的数据过滤条件，确保线程隔离</li>
 *   <li>在方法执行后和异常抛出时都会清理ThreadLocal资源，防止内存泄漏</li>
 *   <li>使用不可变对象存储过滤条件，避免并发修改问题</li>
 *   <li>在所有可能的执行路径上都确保清理ThreadLocal资源，包括正常执行和异常情况</li>
 * </ol>
 * 
 * <p>性能优化：</p>
 * <ol>
 *   <li>对超级管理员用户快速返回，避免不必要的SQL过滤条件构建</li>
 *   <li>使用局部变量存储中间结果，避免跨方法引用导致的内存泄漏</li>
 *   <li>只在必要时才创建和存储过滤条件，减少ThreadLocal操作</li>
 *   <li>日志记录使用debug级别，在生产环境可关闭以提高性能</li>
 * </ol>
 * 
 * <p>安全注意事项：</p>
 * <ol>
 *   <li>SQL过滤条件的生成应防止SQL注入攻击，建议使用参数化查询</li>
 *   <li>敏感数据不应在日志中明文记录，特别是在生产环境</li>
 *   <li>确保GXDataScopeService实现类中的权限判断逻辑安全可靠</li>
 * </ol>
 *
 * @author 塵渊 britton@126.com
 */
@Aspect
@Component
@Slf4j
public class GXDataFilterAspect {
    /**
     * 定义数据过滤切点，拦截所有标注了@GXDataFilter注解的方法
     * <p>
     * 该切点用于定义AOP拦截的目标方法范围，即所有标注了@GXDataFilter注解的方法
     * 作为后续前置通知、后置通知和异常通知的切点定义
     * </p>
     */
    @Pointcut("@annotation(cn.maple.core.datasource.annotation.GXDataFilter)")
    public void dataFilterPointCut() {
        // 切点定义，不需要实现
    }

    /**
     * 方法正常执行后的处理
     * <p>
     * 用于清理ThreadLocal中的数据过滤条件，防止内存泄漏
     * 该方法在目标方法正常执行完成后被调用，确保在正常执行路径上释放资源
     * </p>
     * <p>
     * 内存安全说明：
     * 1. 即使在高并发环境下，每个线程都有自己独立的ThreadLocal变量
     * 2. 清理操作是幂等的，即使多次调用也不会有副作用
     * 3. 在使用线程池的场景下，清理ThreadLocal尤为重要，否则可能导致线程复用时数据混淆
     * </p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>从ThreadLocal中获取数据过滤条件</li>
     *   <li>如果存在过滤条件，记录日志并清理</li>
     *   <li>如果不存在过滤条件，也执行预防性清理，确保ThreadLocal一定被清理</li>
     * </ol>
     */
    @After("dataFilterPointCut()")
    public void dataFilterAfter() {
        GXDataFilterInnerDto dataFilterInnerDto = GXDataFilterThreadLocalUtils.getDataFilterInnerDto();
        if (Objects.nonNull(dataFilterInnerDto) && CharSequenceUtil.isNotEmpty(dataFilterInnerDto.getSqlFilter())) {
            log.debug("正常执行完成，清除数据过滤条件: {}", dataFilterInnerDto.getSqlFilter());
            GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
        } else {
            // 即使没有设置过滤条件，也执行清理操作，确保ThreadLocal一定被清理
            GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
            log.trace("正常执行完成，未发现数据过滤条件，执行预防性清理");
        }
    }
    
    /**
     * 方法异常抛出后的处理
     * <p>
     * 用于清理ThreadLocal中的数据过滤条件，防止内存泄漏
     * 该方法在目标方法抛出异常时被调用，确保在异常执行路径上也能正确释放资源
     * 这是防止ThreadLocal内存泄漏的关键保障之一
     * </p>
     * <p>
     * 异常处理策略：
     * 1. 无论发生什么类型的异常，都确保ThreadLocal资源被释放
     * 2. 清理操作本身不应抛出异常，以免干扰主异常的传播
     * 3. 记录详细日志，便于问题排查
     * </p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>使用try-catch包裹清理逻辑，确保清理过程中的异常不会影响主异常的传播</li>
     *   <li>从ThreadLocal中获取数据过滤条件</li>
     *   <li>如果存在过滤条件，记录日志并清理</li>
     *   <li>如果不存在过滤条件，也执行预防性清理，确保ThreadLocal一定被清理</li>
     *   <li>如果清理过程中发生异常，记录错误日志但不抛出异常</li>
     * </ol>
     */
    @AfterThrowing("dataFilterPointCut()")
    public void dataFilterAfterThrowing() {
        try {
            GXDataFilterInnerDto dataFilterInnerDto = GXDataFilterThreadLocalUtils.getDataFilterInnerDto();
            if (Objects.nonNull(dataFilterInnerDto) && CharSequenceUtil.isNotEmpty(dataFilterInnerDto.getSqlFilter())) {
                log.debug("异常执行路径，清除数据过滤条件: {}", dataFilterInnerDto.getSqlFilter());
                GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
            } else {
                // 即使没有设置过滤条件，也执行清理操作，确保ThreadLocal一定被清理
                GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
                log.trace("异常执行路径，未发现数据过滤条件，执行预防性清理");
            }
        } catch (Exception e) {
            // 清理过程中的异常不应影响主异常的传播，但需要记录日志
            log.error("清理ThreadLocal资源时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 方法执行前的处理
     * <p>
     * 根据用户权限获取数据过滤的SQL条件，并存入ThreadLocal中供后续查询使用
     * 超级管理员不受数据权限限制，直接跳过过滤条件设置
     * 该方法是线程安全的，每个请求都有独立的ThreadLocal存储空间
     * </p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>获取数据权限服务实现类，如果未找到则抛出异常</li>
     *   <li>判断当前用户是否为超级管理员，如果是则跳过数据过滤</li>
     *   <li>获取SQL过滤条件，如果为空则跳过数据过滤</li>
     *   <li>创建数据过滤对象并存入ThreadLocal中</li>
     *   <li>记录应用数据过滤条件的日志</li>
     * </ol>
     *
     * @param point 切点对象，包含目标方法的相关信息
     * @throws GXBusinessException 当未实现GXDataScopeService接口或获取SQL过滤条件出错时抛出
     */
    @Before("dataFilterPointCut()")
    public void dataFilterBefore(JoinPoint point) {
        // 获取数据权限服务实现类
        GXDataScopeService dataScopeService = GXSpringContextUtils.getBean(GXDataScopeService.class);
        if (Objects.isNull(dataScopeService)) {
            log.error("数据权限过滤失败：未找到GXDataScopeService接口实现类");
            throw new GXBusinessException(CharSequenceUtil.format("请实现{}接口", GXDataScopeService.class.getName()));
        }
        
        // 判断当前用户是否为超级管理员
        boolean isSuperAdmin = dataScopeService.isSuperAdmin();
        MethodSignature signature = (MethodSignature) point.getSignature();
        String methodName = signature.getDeclaringTypeName() + "." + signature.getName();
        
        // 如果是超级管理员，则不进行数据过滤
        if (isSuperAdmin) {
            log.debug("超级管理员访问，跳过数据权限过滤: {}", methodName);
            return;
        }

        // 否则进行数据过滤
        try {
            // 获取SQL过滤条件
            String sqlFilter = getSqlFilter(point);
            if (CharSequenceUtil.isEmpty(sqlFilter)) {
                log.debug("未获取到SQL过滤条件，跳过数据权限过滤: {}", methodName);
                return;
            }
            
            // 创建数据过滤对象并存入ThreadLocal
            GXDataFilterInnerDto dataScope = new GXDataFilterInnerDto(sqlFilter);
            GXDataFilterThreadLocalUtils.setDataFilterInnerDto(dataScope);
            log.debug("已应用数据权限过滤条件: {}, 方法: {}", sqlFilter, methodName);
        } catch (Exception e) {
            log.error("应用数据权限过滤条件时发生异常: {}", e.getMessage(), e);
            throw new GXBusinessException("应用数据权限过滤条件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取数据过滤的SQL条件
     * <p>
     * 从目标方法上获取@GXDataFilter注解，并通过GXDataScopeService接口获取对应的SQL过滤条件
     * 该方法通过反射获取方法上的注解信息，然后调用GXDataScopeService接口的实现类获取SQL过滤条件
     * 内存安全考虑：使用局部变量存储中间结果，避免跨方法引用导致的内存泄漏
     * </p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>通过反射获取目标方法</li>
     *   <li>获取方法上的@GXDataFilter注解</li>
     *   <li>获取数据权限服务实现类</li>
     *   <li>调用服务获取SQL过滤条件</li>
     *   <li>记录获取到的SQL过滤条件日志</li>
     * </ol>
     *
     * @param point 切点对象，包含目标方法的相关信息
     * @return 返回SQL过滤条件字符串，如果没有过滤条件则返回空字符串
     * @throws Exception 反射获取方法或调用GXDataScopeService.getSqlFilter方法时可能抛出异常
     */
    private String getSqlFilter(JoinPoint point) throws Exception {
        try {
            // 获取方法签名
            MethodSignature signature = (MethodSignature) point.getSignature();
            // 获取目标方法
            Method method = point.getTarget().getClass().getDeclaredMethod(signature.getName(), signature.getParameterTypes());
            // 获取方法上的数据过滤注解
            GXDataFilter dataFilter = method.getAnnotation(GXDataFilter.class);
            // 获取数据权限服务实现类
            GXDataScopeService dataScopeService = GXSpringContextUtils.getBean(GXDataScopeService.class);
            if (Objects.isNull(dataScopeService)) {
                log.error("获取数据过滤条件失败：未找到GXDataScopeService接口实现类");
                throw new GXBusinessException(CharSequenceUtil.format("请实现{}接口", GXDataScopeService.class.getName()));
            }
            // 调用服务获取SQL过滤条件
            String sqlFilter = dataScopeService.getSqlFilter(dataFilter, point);
            log.debug("获取到SQL过滤条件: {}", sqlFilter);
            return sqlFilter;
        } catch (NoSuchMethodException e) {
            log.error("获取目标方法失败: {}", e.getMessage(), e);
            throw new Exception("获取目标方法失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("获取SQL过滤条件时发生异常: {}", e.getMessage(), e);
            throw e;
        }
    }
}