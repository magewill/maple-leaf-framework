# Leaf-Base-Datasource 模块开发手册

## 1. 模块简介

leaf-base-datasource 是 Maple Leaf Framework 框架中负责数据库操作的核心模块，基于 MyBatis-Plus 和 Druid 实现，提供了动态数据源、数据过滤、自动填充、数据加解密等丰富功能，简化了开发人员的数据库操作流程。

## 2. 主要依赖

模块主要依赖以下组件：

- MyBatis-Plus：`3.5.11`
- MySQL Connector：`9.1.0`
- Druid：`1.2.24`
- P6Spy：`3.9.1`

## 3. 核心功能

### 3.1 动态数据源

#### 3.1.1 相关类

- `GXDataSource`：数据源注解，用于切换数据源
- `GXDynamicDataSource`：动态数据源实现类
- `GXDynamicDataSourceConfig`：动态数据源配置类
- `GXDynamicDataSourceFactory`：动态数据源工厂类
- `GXDynamicContextHolder`：动态数据源上下文持有者
- `GXDataSourceAspect`：数据源切面，处理数据源切换逻辑

#### 3.1.2 工作原理

动态数据源的切换主要依赖于 AOP（面向切面编程）和 `ThreadLocal` 技术：

1.  **`@GXDataSource` 注解标记**：开发者在需要切换数据源的方法或类上使用 `@GXDataSource` 注解，并指定目标数据源的名称（例如 `"slave"` 或 `"master"`）。
2.  **`GXDataSourceAspect` 切面拦截**：这是一个 AOP 切面，它会拦截所有标记了 `@GXDataSource` 注解的方法。
    *   **方法进入前**：切面会读取注解中指定的数据源名称。然后，它调用 `GXDynamicContextHolder.push(dataSourceName)` 方法，将当前线程的数据源上下文设置为指定的数据源名称。`GXDynamicContextHolder` 内部使用 `ThreadLocal` 来存储数据源标识，确保数据源的切换仅对当前线程有效，避免线程间干扰。
    *   **方法执行后/异常时**：无论方法是正常结束还是抛出异常，切面都会在 `finally` 块中调用 `GXDynamicContextHolder.poll()` 或 `GXDynamicContextHolder.clear()` 方法，将当前线程的数据源上下文恢复到上一个状态或清除，防止数据源状态泄露到后续操作中。
3.  **`GXDynamicDataSource` 路由**：`GXDynamicDataSource` 类继承自 Spring 的 `AbstractRoutingDataSource`。它在每次数据库操作（如获取连接）时，会调用其 `determineCurrentLookupKey()` 方法。此方法内部通过 `GXDynamicContextHolder.peek()` 获取当前线程绑定的数据源名称。
4.  **数据源选择**：`AbstractRoutingDataSource` 根据 `determineCurrentLookupKey()` 返回的名称，从预先配置好的数据源映射（Map）中查找并返回对应的数据源连接。如果 `GXDynamicContextHolder`中没有设置特定的数据源，则会使用默认数据源。

通过这种方式，框架能够在不修改业务代码主体逻辑的情况下，灵活地实现方法级别或类级别的数据源动态切换。

#### 3.1.3 注解参数说明

`@GXDataSource` 注解包含以下参数：

-   **`value` (或 `name`)**: `String` 类型，必需。用于指定要切换到的数据源的名称。这个名称必须与 `datasource.yml` (或其他配置文件) 中配置的数据源键名一致。

    例如：`@GXDataSource("slave")` 表示切换到名为 "slave" 的数据源。

#### 3.1.4 使用方法

1. 在方法上添加 `@GXDataSource` 注解指定数据源名称：

```java
@GXDataSource("slave")
public List<UserEntity> queryFromSlave() {
    return userMapper.selectList(null);
}
```

2. 也可以通过代码手动切换数据源：

```java
// 切换到指定数据源
GXDynamicContextHolder.push("slave");
try {
    // 执行数据库操作
    return userMapper.selectList(null);
} finally {
    // 恢复数据源
    GXDynamicContextHolder.poll();
}
```

#### 3.1.5 注意事项

-   **事务管理**：当在事务方法中使用 `@GXDataSource` 切换数据源时，需要特别注意事务的传播行为和数据源的一致性。如果一个事务跨越多个数据源，可能需要考虑使用分布式事务解决方案（如 Seata）。通常建议将涉及不同数据源的操作分离到不同的事务中。
-   **嵌套切换**：如果在一个已经切换了数据源的方法内部，再次调用另一个也标记了 `@GXDataSource` 的方法，`GXDynamicContextHolder` 使用栈（Stack）来管理数据源标识，能够正确处理嵌套切换和恢复。
-   **清晰命名**：数据源名称应清晰、有意义，并在配置文件和注解使用中保持一致。

### 3.2 数据过滤

#### 3.2.1 相关类

- `GXDataFilter`：数据过滤注解，用于标记需要进行数据权限过滤的方法
- `GXDataFilterAspect`：数据过滤切面，拦截标记了注解的方法，设置过滤条件
- `GXDataFilterInterceptor`：数据过滤拦截器，在SQL执行前动态添加数据权限过滤条件
- `GXDataFilterInnerDto`：数据过滤内部传输对象，封装过滤条件信息
- `GXDataFilterThreadLocalUtils`：数据过滤线程工具类，通过ThreadLocal存储和传递过滤条件
- `GXDataScopeService`：数据范围服务接口，定义获取数据权限范围的方法

#### 3.2.2 工作原理

数据过滤功能基于AOP和MyBatis拦截器实现，在SQL执行前动态添加数据权限过滤条件，确保用户只能访问其权限范围内的数据。主要流程：

1. 通过`@GXDataFilter`注解标记需要进行数据权限过滤的方法
2. `GXDataFilterAspect`切面拦截标记了注解的方法，获取注解参数
3. 调用`GXDataScopeService`获取当前用户的数据权限范围（如部门ID列表、用户ID等）
4. 根据权限范围构建SQL过滤条件，通过`GXDataFilterThreadLocalUtils`存储到ThreadLocal中
5. `GXDataFilterInterceptor`拦截SQL执行，从ThreadLocal获取过滤条件
6. 使用JSqlParser解析原始SQL，动态添加数据权限过滤条件
7. 执行修改后的SQL，返回过滤后的结果

#### 3.2.3 注解参数说明

`@GXDataFilter` 注解包含以下参数：

-   **`tableAlias`**: `String` 类型，可选。查询SQL中主表的别名。如果SQL中使用了表别名，则必须提供此参数，以便正确构建过滤条件。例如，如果查询是 `SELECT u.* FROM user u`，则 `tableAlias`应为 `"u"`。
-   **`userIdFieldNames`**: `String[]` 类型，可选。表示数据表中与用户ID关联的字段名数组。框架会使用这些字段名结合当前登录用户的ID来构建过滤条件，例如 `(u.creator_id = ? OR u.owner_id = ?)`。如果不需要按用户ID过滤，则留空。
-   **`deptIdFieldNames`**: `String[]` 类型，可选。表示数据表中与部门ID关联的字段名数组。框架会使用这些字段名结合当前登录用户所属的部门ID列表来构建过滤条件，例如 `(u.dept_id IN (?,?,?) OR u.org_id IN (?,?,?))`。如果不需要按部门ID过滤，则留空。
-   **`tenantFilter`**: `boolean` 类型，可选，默认为 `false`。是否启用租户过滤。如果为 `true`，并且系统配置了多租户支持，则会自动添加租户ID过滤条件。通常与多租户功能配合使用。
-   **`selfScope`**: `boolean` 类型，可选，默认为 `true`。是否仅查询用户自身的数据。如果为 `true`，则会强制使用 `userIdFieldNames` 进行过滤。如果 `GXDataScopeService` 中有更复杂的逻辑（如基于角色的数据权限），此参数可以配合调整。
-   **`ignoreFields`**: `String[]` 类型，可选。在某些特定场景下，即使配置了全局的过滤字段，也希望对某些查询中的特定字段不应用过滤，可以通过此参数指定。较少使用。

#### 3.2.4 使用方法

1. 在需要进行数据权限过滤的方法上添加 `@GXDataFilter` 注解：

```java
@GXDataFilter(
    tableAlias = "u",              // 表别名，用于构建过滤条件
    tenantFilter = true,           // 是否启用租户过滤
    userIdFieldNames = {"creator_id", "updater_id"}, // 用户ID字段名
    deptIdFieldNames = {"dept_id"}  // 部门ID字段名
)
public List<UserEntity> list(Map<String, Object> params) {
    return baseMapper.selectList(getWrapper(params));
}
```

2. 实现`GXDataScopeService`接口，提供数据权限范围：

```java
@Service
public class DataScopeServiceImpl implements GXDataScopeService {
    @Autowired
    private UserContextHolder userContextHolder;
    
    @Override
    public Set<Number> getDeptIdLst() {
        // 获取当前用户所属部门ID列表
        UserInfo currentUser = userContextHolder.getCurrentUser();
        if (currentUser == null) {
            return new HashSet<>();
        }
        return new HashSet<>(currentUser.getDeptIds());
    }
    
    @Override
    public Number getUserId() {
        // 获取当前用户ID
        UserInfo currentUser = userContextHolder.getCurrentUser();
        return currentUser != null ? currentUser.getId() : null;
    }
    
    @Override
    public boolean isSuperAdmin() {
        // 判断当前用户是否为超级管理员（跳过权限过滤）
        UserInfo currentUser = userContextHolder.getCurrentUser();
        return currentUser != null && currentUser.isSuperAdmin();
    }
    
    @Override
    public String getDataFilterSql(String tableAlias, String[] userIdFieldNames, String[] deptIdFieldNames) {
        // 自定义数据过滤SQL
        StringBuilder sqlBuilder = new StringBuilder();
        
        // 超级管理员不进行过滤
        if (isSuperAdmin()) {
            return null;
        }
        
        // 获取用户ID和部门ID列表
        Number userId = getUserId();
        Set<Number> deptIds = getDeptIdLst();
        
        // 构建用户ID过滤条件
        if (userId != null && userIdFieldNames != null && userIdFieldNames.length > 0) {
            sqlBuilder.append("(");
            for (int i = 0; i < userIdFieldNames.length; i++) {
                if (i > 0) {
                    sqlBuilder.append(" OR ");
                }
                sqlBuilder.append(tableAlias).append(".").append(userIdFieldNames[i])
                          .append(" = ").append(userId);
            }
            sqlBuilder.append(")");
        }
        
        // 构建部门ID过滤条件
        if (!deptIds.isEmpty() && deptIdFieldNames != null && deptIdFieldNames.length > 0) {
            if (sqlBuilder.length() > 0) {
                sqlBuilder.append(" OR ");
            }
            sqlBuilder.append("(");
            for (int i = 0; i < deptIdFieldNames.length; i++) {
                if (i > 0) {
                    sqlBuilder.append(" OR ");
                }
                sqlBuilder.append(tableAlias).append(".").append(deptIdFieldNames[i])
                          .append(" IN (").append(deptIds.stream().map(String::valueOf)
                          .collect(Collectors.joining(","))).append(")");
            }
            sqlBuilder.append(")");
        }
        
        return sqlBuilder.length() > 0 ? sqlBuilder.toString() : null;
    }
}
```

#### 3.2.4 高级用法

1. **自定义过滤条件**：通过实现`getDataFilterSql`方法，可以根据业务需求自定义复杂的过滤条件

```java
@Override
public String getDataFilterSql(String tableAlias, String[] userIdFieldNames, String[] deptIdFieldNames) {
    // 获取当前用户角色
    UserInfo currentUser = userContextHolder.getCurrentUser();
    String userRole = currentUser != null ? currentUser.getRole() : null;
    
    // 根据角色返回不同的过滤条件
    if ("manager".equals(userRole)) {
        // 经理可以查看本部门及子部门数据
        return tableAlias + ".dept_id IN (SELECT id FROM sys_dept WHERE path LIKE '%" + 
               currentUser.getDeptId() + "%')"; 
    } else if ("director".equals(userRole)) {
        // 主管可以查看本部门数据
        return tableAlias + ".dept_id = " + currentUser.getDeptId();
    } else {
        // 普通用户只能查看自己创建的数据
        return tableAlias + ".creator_id = " + currentUser.getId();
    }
}
```

2. **动态忽略过滤**：在特定场景下临时忽略数据权限过滤

```java
// 临时忽略数据权限过滤
GXDataFilterThreadLocalUtils.setIgnoreFilter(true);
try {
    // 执行不受数据权限限制的查询
    return userMapper.selectList(null);
} finally {
    // 恢复数据权限过滤
    GXDataFilterThreadLocalUtils.clear();
}
```

3. **条件性过滤**：根据请求参数决定是否应用数据权限过滤

```java
@GXDataFilter(tableAlias = "t", userIdFieldNames = {"creator_id"})
public List<OrderEntity> getOrders(Map<String, Object> params) {
    // 如果请求参数中包含ignoreFilter=true，则忽略数据权限过滤
    if ("true".equals(params.get("ignoreFilter"))) {
        GXDataFilterThreadLocalUtils.setIgnoreFilter(true);
    }
    
    try {
        return orderMapper.selectList(getWrapper(params));
    } finally {
        GXDataFilterThreadLocalUtils.clear();
    }
}
```

#### 3.2.6 注意事项

-   **`GXDataScopeService` 的实现**：数据过滤的核心逻辑依赖于 `GXDataScopeService` 接口的实现。你需要根据项目的实际用户、角色、部门等权限模型，正确实现 `getDeptIdLst()`、`getUserId()`、`isSuperAdmin()` 以及 `getDataFilterSql()` (如果需要自定义复杂SQL) 等方法。
-   **SQL兼容性**：`GXDataFilterInterceptor` 使用 JSqlParser 解析和修改SQL。对于非常复杂或非标准的SQL语句，可能存在兼容性问题。建议使用标准的SQL语法。
-   **性能考虑**：动态添加过滤条件会略微增加SQL解析和执行的开销。对于性能敏感的查询，应评估其影响。确保数据库表中用于过滤的字段（如 `creator_id`, `dept_id`, `tenant_id`）已建立索引。
-   **表别名**：如果查询中涉及多个表连接，务必确保 `tableAlias` 参数正确指向需要应用数据权限的主表别名。
-   **超级管理员**：`isSuperAdmin()` 方法用于识别超级管理员。超级管理员通常会跳过所有数据权限过滤。请谨慎设计超级管理员的判断逻辑。
-   **与多租户的结合**：当 `tenantFilter = true` 时，数据过滤会与多租户功能协同工作。确保多租户配置正确。

### 3.3 MyBatis 增强

#### 3.3.1 基础组件

- `GXBaseMapper`：基础Mapper接口，扩展了MyBatis-Plus的BaseMapper
- `GXMyBatisDao`：MyBatis DAO层基类
- `GXMyBatisRepository`：MyBatis Repository层基类
- `GXMyBatisModel`：MyBatis 模型基类
- `GXMyBatisBaseService`：MyBatis 服务接口
- `GXMyBatisBaseServiceImpl`：MyBatis 服务实现类

#### 3.3.2 自动填充

##### 3.3.2.1 相关类

- `GXAutoFillMetaObjectHandler`：自动填充处理器，实现MyBatis-Plus的MetaObjectHandler接口
- `GXMyBatisAutoFillMetaObjectService`：自动填充服务接口，定义获取填充值的方法

##### 3.3.2.2 工作原理

自动填充功能基于MyBatis-Plus的元对象处理机制实现，在实体插入和更新操作时自动填充指定字段，无需在业务代码中手动设置。主要流程：

1. 在实体类字段上添加`@TableField`注解，指定填充策略（INSERT、UPDATE或INSERT_UPDATE）
2. 当执行插入或更新操作时，MyBatis-Plus自动调用`GXAutoFillMetaObjectHandler`的相应方法
3. 处理器从`GXMyBatisAutoFillMetaObjectService`获取填充值（如当前用户ID、当前时间等）
4. 将获取的值自动填充到实体对象的相应字段中

##### 3.3.2.3 支持的填充字段

- `createdAt`：创建时间，插入时自动填充
- `createdBy`：创建人，插入时自动填充
- `updatedAt`：更新时间，更新时自动填充
- `updatedBy`：更新人，更新时自动填充
- `tenantId`：租户ID，插入时自动填充（多租户场景）
- `deletedFlag`：删除标记，插入时自动填充默认值

##### 3.3.2.4 使用方法

1. 实体类继承`GXMyBatisModel`或自定义填充字段：

```java
@Data
@TableName("tb_user")
public class UserEntity extends GXMyBatisModel {
    // GXMyBatisModel已包含基础审计字段，如createdAt、updatedAt等
    
    private String username;
    private String email;
    
    // 自定义填充字段
    @TableField(fill = FieldFill.INSERT)
    private String departmentCode;
}
```

2. 实现自动填充服务接口：

```java
@Service
public class MyBatisAutoFillMetaObjectServiceImpl implements GXMyBatisAutoFillMetaObjectService {
    @Autowired
    private UserContextHolder userContextHolder;
    
    @Override
    public String getCreatedBy() {
        // 获取当前用户作为创建人
        UserInfo currentUser = userContextHolder.getCurrentUser();
        return currentUser != null ? currentUser.getUsername() : "system";
    }
    
    @Override
    public String getUpdatedBy() {
        // 获取当前用户作为更新人
        UserInfo currentUser = userContextHolder.getCurrentUser();
        return currentUser != null ? currentUser.getUsername() : "system";
    }
    
    @Override
    public Integer getCreatedAt() {
        // 返回当前时间的秒级时间戳
        return Math.toIntExact(System.currentTimeMillis() / 1000);
    }
    
    @Override
    public Integer getUpdatedAt() {
        // 返回当前时间的秒级时间戳
        return Math.toIntExact(System.currentTimeMillis() / 1000);
    }
    
    @Override
    public Object getTenantId() {
        // 获取当前租户ID
        UserInfo currentUser = userContextHolder.getCurrentUser();
        return currentUser != null ? currentUser.getTenantId() : null;
    }
    
    @Override
    public String getFieldValue(String fieldName, MetaObject metaObject) {
        // 处理自定义填充字段
        if ("departmentCode".equals(fieldName)) {
            UserInfo currentUser = userContextHolder.getCurrentUser();
            return currentUser != null ? currentUser.getDepartmentCode() : "DEFAULT";
        }
        return null;
    }
}
```

3. 使用自动填充功能：

```java
@Service
public class UserServiceImpl extends GXMyBatisBaseServiceImpl<UserMapper, UserEntity> implements UserService {
    public void createUser(UserDTO dto) {
        UserEntity entity = new UserEntity();
        entity.setUsername(dto.getUsername());
        entity.setEmail(dto.getEmail());
        // 无需设置createdAt、createdBy等字段，会自动填充
        save(entity);
    }
}
```

#### 3.3.3 事件监听

##### 3.3.3.1 相关类

- `GXMyBatisListener`：MyBatis 监听器注解，标记一个类为MyBatis事件监听器
- `GXMyBatisBaseListener`：MyBatis 基础监听器接口，定义了处理各种事件的基本方法
- `GXMyBatisAsyncListener`：MyBatis 异步监听器，在独立线程中处理事件，不阻塞主流程
- `GXMyBatisSyncListener`：MyBatis 同步监听器，在当前线程中处理事件，可参与事务
- `GXMybatisListenerService`：MyBatis 监听器服务接口，定义了实体操作的监听方法
- `GXMyBatisAsyncListenerExecutorConfig`：异步监听器线程池配置类

##### 3.3.3.2 支持的事件类型

- `GXMyBatisModelCreatingEntityEvent`：实体创建前事件，在实体保存到数据库前触发
- `GXMyBatisModelSaveEntityEvent`：实体保存事件，在实体保存到数据库后触发
- `GXMyBatisModelSaveBatchEntityEvent`：批量保存事件，在批量保存实体后触发
- `GXMyBatisModelUpdatingEntityEvent`：实体更新前事件，在实体更新前触发
- `GXMyBatisModelUpdateEntityEvent`：实体更新事件，在实体更新后触发
- `GXMyBatisModelUpdateFieldEvent`：字段更新事件，在更新特定字段后触发
- `GXMyBatisModelDeleteEvent`：删除事件，在物理删除实体后触发
- `GXMyBatisModelDeleteSoftEvent`：软删除事件，在软删除实体后触发

##### 3.3.3.3 工作原理

事件监听系统深度整合了 Spring 的事件发布订阅机制与 AOP 技术，以非侵入式的方式对 MyBatis 的核心操作（增、删、改）进行监听。其核心流程如下：

1.  **注解扫描与监听器注册**：
    *   系统启动时，会扫描带有 `@GXMyBatisListener` 注解的类、Service 或 Mapper 方法。
    *   对于标记在类上的注解，如果指定了 `listenerClazz`，则将该服务类注册为对应实体操作的监听器。
    *   对于标记在 Service 类或 Mapper 方法上的注解，系统会动态地将注解中指定的 `listenerClazz` (实现了 `GXMybatisListenerService` 接口的服务) 与这些数据操作关联起来。
    *   `runType` 参数（默认为同步 `GXMyBatisEventConstant.MYBATIS_SYNC_EVENT`）决定了监听器执行的模式（同步或异步）。

2.  **AOP 拦截与事件发布**：
    *   通过一系列精心设计的 AOP 切面（例如 `GXMyBatisPlusSaveEntityAspect` 用于监听保存操作，`GXMyBatisPlusUpdateEntityAspect` 用于监听更新操作，`GXMyBatisPlusDeleteEntityAspect` 用于监听删除操作）来拦截 MyBatis 的 `insert`, `update`, `delete` 等关键数据操作方法。这些切面通常作用于 `GXMyBatisBaseServiceImpl` 中的标准方法或直接作用于 Mapper 接口的方法调用。
    *   在被拦截的方法执行前或执行后，切面会根据操作类型和结果，构建并发布相应的事件对象（如 `GXMyBatisModelSaveEntityEvent`, `GXMyBatisModelUpdateEntityEvent` 等）。这些事件对象封装了操作的上下文信息，如被操作的实体、更新的字段等。

3.  **事件分发与处理**：
    *   Spring 的 `ApplicationEventMulticaster` 负责将发布的事件分发给所有匹配的监听器。
    *   `GXMyBatisBaseListener` (及其子类 `GXMyBatisSyncListener`, `GXMyBatisAsyncListener`) 实现了 Spring 的 `ApplicationListener` 接口，能够接收这些事件。
    *   监听器内部会根据事件的具体类型（如 `instanceof UserEntity`）和注解配置，调用 `GXMybatisListenerService` 实现类中对应的方法（如 `saveEntityListener`, `updateEntityListener`）。

4.  **同步与异步执行**：
    *   **同步监听器** (`GXMyBatisSyncListener` 或 `runType` 为同步时)：事件处理逻辑在当前业务线程中执行。这意味着监听器的执行会阻塞主流程，并且可以参与到主流程的事务中。适用于需要强一致性、事务性保障的后置处理。
    *   **异步监听器** (`GXMyBatisAsyncListener` 或 `runType` 为异步时)：事件处理逻辑会被提交到专门的异步监听器线程池 (`GXMyBatisAsyncListenerExecutorConfig` 配置的 `myBatisEventAsyncTaskExecutor`) 中执行。这使得监听器操作与主业务流程解耦，不会阻塞主线程，适用于耗时较长或不需要立即反馈的非核心业务逻辑，如发送通知、记录日志等。异步监听器通常需要自行管理事务。

通过这种机制，开发者可以方便地对数据持久化操作的各个生命周期点进行扩展，实现如操作审计、缓存更新、消息通知等附加功能，而无需修改核心的业务代码。

##### 3.3.3.4 同步与异步监听器

**同步监听器**：
- 在当前线程中执行，会阻塞主流程
- 可以参与当前事务，适合需要事务支持的操作
- 异常会直接影响主流程，需要妥善处理异常
- 适用场景：与主业务强相关的操作，如更新关联数据、校验数据一致性等

**异步监听器**：
- 在独立线程池中执行，不阻塞主流程
- 无法参与主流程的事务，需要自行处理事务
- 异常不会影响主流程，但需要妥善记录和处理异常
- 适用场景：与主业务弱相关的操作，如发送通知、记录日志、统计分析等

### 3.3.2 MyBatis 事件监听与 `@GXMyBatisListener`

`@GXMyBatisListener` 注解用于定义和配置MyBatis操作事件的监听器。通过此注解，开发者可以方便地在数据持久化操作（如增、删、改）的不同阶段插入自定义逻辑，例如记录审计日志、更新缓存、发送通知等。

该注解可以标记在实现了 `cn.maple.core.datasource.listener.GXMybatisListenerService` 接口的监听器类上，或者直接标记在 `Service` 类或 `Mapper` 接口的方法上，以指定特定的监听行为。框架会自动扫描带有此注解的组件，并根据配置的事件类型（同步/异步）和作用范围来触发相应的监听器方法。

##### 3.3.2.1 核心接口与类

- **`cn.maple.core.datasource.annotation.GXMyBatisListener`**: 核心注解，用于声明一个类或方法作为MyBatis事件监听器，并配置其行为。
- **`cn.maple.core.datasource.listener.GXMyBatisBaseListener`**: 监听器基础接口，定义了MyBatis操作（如保存、更新、删除等）的事件回调方法。开发者需要实现此接口中的一个或多个方法来处理特定事件。它包含以下主要方法：
    - `onSaveEntity(GXMyBatisModelSaveEntityEvent event)`: 监听实体保存操作。
    - `onUpdateEntity(GXMyBatisModelUpdateEntityEvent event)`: 监听实体更新操作。
    - `onUpdateField(GXMyBatisModelUpdateFieldEvent event)`: 监听实体特定字段更新操作。
    - `onDeleteLogic(GXMyBatisModelDeleteLogicEvent event)`: 监听实体逻辑删除操作。
    - `onSaveBatch(GXMyBatisModelSaveBatchEvent event)`: 监听实体批量保存操作。
- **`cn.maple.core.datasource.listener.GXMyBatisSyncListener`**: 同步监听器抽象类，实现了 `GXMyBatisBaseListener`。同步监听器的逻辑会在主业务操作的同一个事务内、同一个线程中执行。适用于需要强一致性或事务保证的场景。
- **`cn.maple.core.datasource.listener.GXMyBatisAsyncListener`**: 异步监听器抽象类，实现了 `GXMyBatisBaseListener`。异步监听器的逻辑会在独立的线程中执行，不阻塞主业务流程，通常通过配置的线程池进行管理。适用于耗时操作或对主流程性能影响较大的场景。
- **`cn.maple.core.datasource.listener.GXMybatisListenerService`**: 一个标记接口。如果 `@GXMyBatisListener` 注解的 `listenerClazz` 属性指定的是一个实现了此接口的类，框架会认为这是一个需要被管理的监听器服务。通常，监听器类会直接继承 `GXMyBatisSyncListener` 或 `GXMyBatisAsyncListener`，并被Spring容器管理（例如使用 `@Component` 注解）。
- **事件对象 (位于 `cn.maple.core.datasource.event` 包下)**:
    - `GXMyBatisModelEvent`: 所有MyBatis模型事件的基类。
    - `GXMyBatisModelSaveEntityEvent`: 实体保存事件对象，包含被保存的实体信息。
    - `GXMyBatisModelUpdateEntityEvent`: 实体更新事件对象，包含更新前后的实体信息或更新参数。
    - `GXMyBatisModelUpdateFieldEvent`: 实体特定字段更新事件对象。
    - `GXMyBatisModelDeleteLogicEvent`: 实体逻辑删除事件对象。
    - `GXMyBatisModelSaveBatchEvent`: 实体批量保存事件对象，包含被批量保存的实体列表。
- **`cn.maple.core.datasource.constant.GXMyBatisEventConstant`**: 事件常量类，定义了监听器执行类型的常量：
    - `MYBATIS_SYNC_EVENT`: 表示同步执行事件。
    - `MYBATIS_ASYNC_EVENT`: 表示异步执行事件。

1. 创建监听器服务接口实现：

```java
public interface UserListenerService extends GXMybatisListenerService<UserEntity> {
    // 可以定义特定于用户实体的监听方法
}

@Service
public class UserListenerServiceImpl implements UserListenerService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    @Override
    public void saveEntityListener(UserEntity entity) {
        // 处理用户保存事件
        logger.info("用户已保存: {}", entity.getUsername());
        // 执行其他业务逻辑...
    }
    
    @Override
    public void updateEntityListener(UserEntity entity, Dict keyValuePairs, Dict keyOperatorPairs) {
        // 处理用户更新事件
        logger.info("用户已更新: {}", entity.getUsername());
        // 执行其他业务逻辑...
    }
    
    @Override
    public void deleteEntityListener(Dict condition) {
        // 处理用户删除事件
        logger.info("用户已删除，条件: {}", condition);
        // 执行其他业务逻辑...
    }
}
```

##### 3.3.2.2 `@GXMyBatisListener` 注解使用示例

1.  **创建监听器实现类**：
    继承 `GXMyBatisSyncListener` (同步) 或 `GXMyBatisAsyncListener` (异步)，并重写需要关注的事件处理方法。使用 `@Component` (或其他Spring stereotype注解) 使其被Spring容器管理，并使用 `@GXMyBatisListener` 标记该类。

    **同步监听器示例：**
    ```java
    package com.example.listener;

    import cn.maple.core.datasource.annotation.GXMyBatisListener;
    import cn.maple.core.datasource.event.GXMyBatisModelSaveEntityEvent;
    import cn.maple.core.datasource.event.GXMyBatisModelUpdateEntityEvent;
    import cn.maple.core.datasource.listener.GXMyBatisSyncListener;
    import com.example.entity.UserEntity; // 假设的实体类
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.stereotype.Component;
    import org.apache.commons.collections.MapUtils; // 假设使用commons-collections
    import java.util.Map;

    @Component
    @GXMyBatisListener // 标记此类为MyBatis事件监听器，默认同步执行
    public class UserAuditSyncListener extends GXMyBatisSyncListener {

        private static final Logger logger = LoggerFactory.getLogger(UserAuditSyncListener.class);

        @Override
        public void onSaveEntity(GXMyBatisModelSaveEntityEvent event) {
            if (event.getSource() instanceof UserEntity) {
                UserEntity user = (UserEntity) event.getSource();
                logger.info("[SYNC] User saved: {}", user.getUsername());
                // 在同一事务中执行审计日志记录等操作
            }
        }

        @Override
        public void onUpdateEntity(GXMyBatisModelUpdateEntityEvent event) {
            Object source = event.getSource();
            if (source instanceof Map) { // 更新操作可能传递Map类型的参数
                Map<?, ?> params = (Map<?, ?>) source;
                Object entityData = params.get("entityData"); // GXMyBatisBaseServiceImpl中更新方法传递的实体通常在entityData中
                if (entityData instanceof UserEntity) {
                    UserEntity user = (UserEntity) entityData;
                    logger.info("[SYNC] User updated: {}", user.getUsername());
                    // 在同一事务中执行审计日志记录等操作
                }
            } else if (source instanceof UserEntity) { // 也可能直接是实体对象
                 UserEntity user = (UserEntity) source;
                 logger.info("[SYNC] User updated directly: {}", user.getUsername());
            }
        }
        // 可以根据需要重写其他事件方法，如 onDeleteLogic, onUpdateField 等
    }
    ```

    **异步监听器示例：**
    ```java
    package com.example.listener;

    import cn.maple.core.datasource.annotation.GXMyBatisListener;
    import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
    import cn.maple.core.datasource.event.GXMyBatisModelSaveEntityEvent;
    import cn.maple.core.datasource.listener.GXMyBatisAsyncListener;
    import com.example.entity.UserEntity;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.stereotype.Component;

    @Component
    @GXMyBatisListener(
        runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT, // 设置为异步执行
        asyncExecutor = "myBatisEventAsyncTaskExecutor" // 指定异步执行器Bean名称 (可选)
    )
    public class WelcomeNotificationAsyncListener extends GXMyBatisAsyncListener {

        private static final Logger logger = LoggerFactory.getLogger(WelcomeNotificationAsyncListener.class);

        @Override
        public void onSaveEntity(GXMyBatisModelSaveEntityEvent event) {
            if (event.getSource() instanceof UserEntity) {
                UserEntity user = (UserEntity) event.getSource();
                logger.info("[ASYNC] New user registered: {}. Preparing to send welcome email.", user.getUsername());
                try {
                    // 模拟发送邮件等耗时操作
                    Thread.sleep(2000); // 模拟耗时
                    logger.info("[ASYNC] Welcome email sent to {}.
", user.getEmail());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("[ASYNC] Error sending welcome email for user {}: {}", user.getUsername(), e.getMessage());
                } catch (Exception e) {
                    logger.error("[ASYNC] Unexpected error sending welcome email for user {}: {}", user.getUsername(), e.getMessage());
                }
            }
        }
        // 其他异步事件处理
    }
    ```

2.  **在Service类或Mapper方法上使用 `@GXMyBatisListener`** (可选，用于更细粒度的控制)

    如果不想全局监听某个实体的所有操作，或者想为特定的Service操作或Mapper方法指定不同的监听器，可以直接在它们上面使用 `@GXMyBatisListener` 注解，并指定 `listenerClazz`。

    **在Service类上使用示例：**
    ```java
    package com.example.service;

    import cn.maple.core.datasource.annotation.GXMyBatisListener;
    import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
    import cn.maple.core.datasource.service.impl.GXMyBatisBaseServiceImpl;
    import com.example.entity.OrderEntity;
    import com.example.listener.OrderSpecificAsyncListener; // 假设这是为Order定制的异步监听器
    import com.example.mapper.OrderMapper;
    import org.springframework.stereotype.Service;

    @Service
    @GXMyBatisListener(
        listenerClazz = OrderSpecificAsyncListener.class, // 指定特定的监听器类
        runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT
    )
    public class OrderServiceImpl extends GXMyBatisBaseServiceImpl<OrderMapper, OrderEntity> implements OrderService {
        // 此Service中所有GXMyBatisBaseServiceImpl提供的标准增删改查方法，
        // 以及通过OrderMapper执行的数据操作，都将异步触发OrderSpecificAsyncListener。
    }
    ```

    **在Mapper方法上使用示例：**
    ```java
    package com.example.mapper;

    import cn.maple.core.datasource.annotation.GXMyBatisListener;
    import cn.maple.core.datasource.mapper.GXBaseMapper;
    import com.example.entity.ProductEntity;
    import com.example.listener.ProductStockSyncListener; // 假设这是产品库存同步监听器
    import org.apache.ibatis.annotations.Mapper;
    import org.apache.ibatis.annotations.Param;

    @Mapper
    public interface ProductMapper extends GXBaseMapper<ProductEntity> {

        @GXMyBatisListener(listenerClazz = ProductStockSyncListener.class) // 仅此方法触发同步监听
        int decreaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);
    }
    ```

3.  **配置异步监听器线程池** (如果使用异步监听器)

    如果使用了异步监听器 (`runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT`)，建议配置一个专用的线程池来执行这些异步任务。可以在Spring配置类中定义一个 `Executor` Bean，并将其名称通过 `@GXMyBatisListener` 的 `asyncExecutor` 属性指定。

    ```java
    package com.example.config;

    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
    import java.util.concurrent.Executor;

    @Configuration
    public class AsyncExecutionConfig {

        public static final String ASYNC_EXECUTOR_NAME = "myBatisEventAsyncTaskExecutor";

        @Bean(name = ASYNC_EXECUTOR_NAME)
        public Executor myBatisEventAsyncTaskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(5);       // 核心线程数
            executor.setMaxPoolSize(10);      // 最大线程数
            executor.setQueueCapacity(25);    // 队列容量
            executor.setThreadNamePrefix("MyBatisEvent-"); // 线程名前缀
            executor.initialize();
            return executor;
        }
    }
    ```
    如果未指定 `asyncExecutor` 或找不到对应的Bean，框架可能会尝试使用默认的异步执行机制（例如Spring的 `@Async` 默认线程池，但这取决于具体实现和版本）。

##### 3.3.2.3 注解参数说明

| 参数名          | 类型                                      | 是否必填 | 默认值                                   | 描述                                                                                                                                                              |
|-----------------|-------------------------------------------|----------|------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------| 
| `listenerClazz` | `Class<? extends cn.maple.core.datasource.listener.GXMybatisListenerService>` | 否       | `cn.maple.core.datasource.listener.GXMybatisListenerService.class`         | 指定实现了 `GXMybatisListenerService` 接口的监听器服务类。当注解直接标记在监听器类上时（监听器类本身实现了 `GXMyBatisBaseListener` 并通常被Spring管理），此参数可省略。当注解标记在Service或Mapper方法上，用于指定一个外部的监听器类时，此参数是必需的。 |
| `runType`       | `String`                                  | 否       | `GXMyBatisEventConstant.MYBATIS_SYNC_EVENT` | 指定监听器的执行类型，可选值为：<br> - `GXMyBatisEventConstant.MYBATIS_SYNC_EVENT` (默认): 同步执行，监听器逻辑与主业务逻辑在同一事务中，会影响主流程性能。<br> - `GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT`: 异步执行，监听器逻辑在独立的线程中执行，不阻塞主业务流程，通常用于耗时操作或非核心业务。                               | 
| `order`         | `int`                                     | 否       | `Ordered.LOWEST_PRECEDENCE`              | 定义多个监听器时的执行顺序，值越小，优先级越高。                                                                                                                            |
| `asyncExecutor` | `String`                                  | 否       | `"myBatisEventAsyncTaskExecutor"`        | 当 `runType` 为异步时，指定执行异步任务的线程池Bean名称。如果未指定或找不到对应的Bean，会尝试使用默认的异步执行器。                                                                 |

##### 3.3.2.4 注意事项

- **事务传播**：
    - 同步监听器 (`GXMyBatisSyncListener`) 默认情况下会参与到当前主业务的事务中。如果监听器内部发生异常，可能会导致主事务回滚。
    - 异步监听器 (`GXMyBatisAsyncListener`) 在独立的线程中执行，默认不参与主业务事务。如果异步监听器需要事务支持，需要在其内部方法上单独配置事务注解（如 `@Transactional`），并确保异步线程池能够正确传播事务上下文。
- **异步执行与线程池**：
    - 使用异步监听器时，建议配置专用的线程池（通过 `asyncExecutor` 参数指定），并合理设置线程池参数（核心线程数、最大线程数、队列容量等），以避免资源耗尽或性能瓶颈。
    - 异步任务的异常处理需要特别注意，因为它们不会直接影响主流程。应在异步监听器内部做好充分的异常捕获和日志记录。
- **监听范围**：
    - `@GXMyBatisListener` 可以标记在实现了 `GXMyBatisListenerService` 的监听器类上，也可以直接标记在 `Service` 类或 `Mapper` 接口的方法上。
    - 当标记在 `Service` 类上时，该 `Service` 中所有通过 `GXMyBatisBaseServiceImpl` 提供的标准增删改查方法，以及其调用的 `Mapper` 方法，都会触发监听。
    - 当标记在 `Mapper` 方法上时，仅该特定方法会触发监听。
- **事件对象**：
    - 监听器方法接收的事件对象（如 `GXMyBatisModelSaveEntityEvent`、`GXMyBatisModelUpdateEntityEvent` 等）包含了触发事件的实体、原始SQL、执行结果等信息，可以根据这些信息执行相应的业务逻辑。
- **性能考虑**：
    - 同步监听器会增加主业务流程的执行时间，应避免在同步监听器中执行耗时操作。
    - 过多的监听器或复杂的监听逻辑可能会对系统性能产生影响，需要谨慎设计和使用。
- **与 `@Transactional` 的结合**：
    - 当 `@GXMyBatisListener` 与 `@Transactional` 同时作用于一个方法或类时，需要注意它们的执行顺序和事务边界。通常，AOP拦截的顺序会影响行为，建议通过 `order` 参数明确控制监听器的执行顺序。


        executor.initialize();
        return executor;
    }
}
```

#### 3.3.3 整体使用流程概览

1.  **定义实体类**：确保实体类继承自 `cn.maple.core.datasource.model.GXMyBatisModel` (或其子类)，这是事件能够正确识别和处理实体的前提。

    ```java
    package com.example.entity;

    import cn.maple.core.datasource.model.GXMyBatisModel;
    import com.baomidou.mybatisplus.annotation.TableName;
    import lombok.Data;
    import lombok.EqualsAndHashCode;

    @Data
    @EqualsAndHashCode(callSuper = true)
    @TableName("tb_user")
    public class UserEntity extends GXMyBatisModel {
        private String username;
        private String password;
        private String email;
        // 其他字段...
    }
    ```

2.  **创建Mapper接口**：继承 `cn.maple.core.datasource.mapper.GXBaseMapper<T>`。

    ```java
    package com.example.mapper;

    import cn.maple.core.datasource.mapper.GXBaseMapper;
    import com.example.entity.UserEntity;
    import org.apache.ibatis.annotations.Mapper;

    @Mapper
    public interface UserMapper extends GXBaseMapper<UserEntity> {
        // 可以定义自定义的SQL方法
    }
    ```

3.  **创建Service接口与实现**：
    - Service接口继承 `cn.maple.core.datasource.service.GXMyBatisBaseService<T>`。
    - Service实现类继承 `cn.maple.core.datasource.service.impl.GXMyBatisBaseServiceImpl<M, T>`。

    ```java
    package com.example.service;

    import cn.maple.core.datasource.service.GXMyBatisBaseService;
    import com.example.entity.UserEntity;

    public interface UserService extends GXMyBatisBaseService<UserEntity> {
        // 自定义服务方法
        void registerUser(UserEntity user);
    }
    ```

    ```java
    package com.example.service.impl;

    import cn.maple.core.datasource.service.impl.GXMyBatisBaseServiceImpl;
    import com.example.entity.UserEntity;
    import com.example.mapper.UserMapper;
    import com.example.service.UserService;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;

    @Service
    public class UserServiceImpl extends GXMyBatisBaseServiceImpl<UserMapper, UserEntity> implements UserService {

        @Override
        @Transactional // 示例：注册用户操作通常需要事务
        public void registerUser(UserEntity user) {
            // 进行一些业务校验或处理
            // ...
            this.save(user); // 调用GXMyBatisBaseServiceImpl的save方法，会触发onSaveEntity事件
            // 如果配置了监听器，此时相关的监听逻辑会被执行
        }
    }
    ```

4.  **创建并配置监听器**：按照 **3.3.2.2** 中的示例创建监听器类，并使用 `@GXMyBatisListener` 注解。

5.  **(可选) 配置异步线程池**：如果使用了异步监听器，按照 **3.3.2.2 (步骤3)** 中的示例配置线程池。

6.  **执行操作触发事件**：当调用 `GXMyBatisBaseServiceImpl` 提供的标准增删改查方法（如 `save`, `updateById`, `removeById` 等），或者调用被 `@GXMyBatisListener` 注解的 `Mapper` 方法时，相应的事件会被发布，并由匹配的监听器处理。

1. 创建实体类继承 `GXMyBatisModel`：

```java
@Data
@TableName("tb_user")
public class UserEntity extends GXMyBatisModel {
    private String username;
    private String password;
    // 其他字段...
}
```

2. 创建Mapper接口继承 `GXBaseMapper`：

```java
@Mapper
public interface UserMapper extends GXBaseMapper<UserEntity> {
    // 自定义方法...
}
```

3. 创建Service接口继承 `GXMyBatisBaseService`：

```java
public interface UserService extends GXMyBatisBaseService<UserEntity> {
    // 自定义方法...
}
```

4. 创建Service实现类继承 `GXMyBatisBaseServiceImpl`：

```java
@Service
public class UserServiceImpl extends GXMyBatisBaseServiceImpl<UserMapper, UserEntity> implements UserService {
    // 实现自定义方法...
}
```

### 3.4 JSON与Java对象类型处理

### 3.5 数据校验

#### 3.5.1 数据库存在性校验 (`@GXValidateDBExists`)

##### 3.5.1.1 简介

`@GXValidateDBExists` 注解提供了一种便捷的方式来验证数据库中是否存在符合特定条件的记录。它通常用于DTO（数据传输对象）的字段上，结合JSR 303/JSR 380 Bean Validation规范使用，可以在数据持久化之前进行有效性检查。

主要应用场景：
- **唯一性验证**：确保某个字段的值在数据库表中是唯一的（例如，用户名、邮箱不能重复）。
- **关联性验证**：确保某个字段的值在另一个关联表中存在（例如，创建订单时，用户ID必须在用户表中存在）。
- **条件性验证**：根据一个或多个附加条件来验证记录是否存在（例如，检查产品ID是否存在并且状态为“已上架”）。

##### 3.5.1.2 相关类

- `GXValidateDBExists`：核心注解，用于标记需要进行数据库存在性校验的字段。
- `GXValidateDBExistsValidator`：实现了 `jakarta.validation.ConstraintValidator` 接口的校验器，负责执行实际的数据库查询和验证逻辑。
- `GXValidateDBExistsService`：一个服务接口，用户需要实现此接口来提供具体的数据库查询能力。框架会调用此接口的实现来检查数据是否存在。

##### 3.5.1.3 注解参数说明

| 参数名             | 类型                                      | 是否必填 | 默认值                                        | 描述                                                                                                                               |
|--------------------|-------------------------------------------|----------|-----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `message`          | `String`                                  | 否       | `"{fieldName}对应的数据已经存在或是参数已经存在存在"` | 验证失败时的错误提示信息。可以使用占位符 `{fieldName}`。                                                                                   |
| `groups`           | `Class<?>[]`                              | 否       | `{}`                                          | JSR 303/380的分组校验功能。                                                                                                            |
| `payload`          | `Class<? extends Payload>[]`               | 否       | `{}`                                          | JSR 303/380的payload功能。                                                                                                           |
| `service`          | `Class<? extends GXValidateDBExistsService>` | 是       |                                               | 指定一个实现了 `GXValidateDBExistsService` 接口的类，用于执行实际的数据库查询。                                                              |
| `fieldName`        | `String`                                  | 是       |                                               | 要在数据库中校验的字段名（通常与DTO中的字段名一致，但也可以是数据库中的列名）。                                                                      |
| `tableName`        | `String`                                  | 是       |                                               | 要查询的数据库表名。                                                                                                                     |
| `condition`        | `String`                                  | 否       | `""`                                        | 附加的静态查询条件，格式为 `column1=value1,column2=value2`。例如：`status=1,type='ACTIVE'`。                                                 |
| `spEL`             | `String`                                  | 否       | `""`                                        | Spring表达式语言（SpEL）条件。用于更复杂的动态条件判断，可以引用当前校验对象 (`#root`) 或查询结果 (`#result`)。例如：`#result.balance >= #root.amount`。 |
| `dependOnFields`   | `String[]`                                | 否       | `{}`                                          | 依赖的其他字段名。当这些字段的值发生变化时，可能会影响校验结果，通常与缓存配合使用，确保缓存键的唯一性。                                                       |
| `enableCache`      | `boolean`                                 | 否       | `false`                                       | 是否启用缓存。启用后，校验结果会被缓存，以提高重复校验的性能。适用于数据不经常变化的场景。                                                                 |
| `cacheExpireSeconds` | `int`                                     | 否       | `300`                                         | 缓存的过期时间（秒），仅在 `enableCache` 为 `true` 时有效。                                                                                     |

##### 3.5.1.4 使用方法

1.  **定义 `GXValidateDBExistsService` 实现**

    你需要创建一个类实现 `GXValidateDBExistsService` 接口。这个服务类将负责根据注解提供的参数（如表名、字段名、条件等）去数据库查询记录是否存在。

    ```java
    import cn.maple.core.datasource.service.GXValidateDBExistsService;
    import org.springframework.stereotype.Service;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.jdbc.core.JdbcTemplate;
    import java.util.Map;
    import java.util.List;

    @Service("userExistsValidateService") // Bean的名称可以自定义，在注解中通过service属性引用
    public class UserExistsValidateServiceImpl implements GXValidateDBExistsService {

        @Autowired
        private JdbcTemplate jdbcTemplate; // 或者使用MyBatis Mapper

        @Override
        public boolean exists(String tableName, String fieldName, Object fieldValue, String condition, String spEL, Map<String, Object> rootObject) {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(tableName)
                                 .append(" WHERE ").append(fieldName).append(" = ?");
            List<Object> params = new ArrayList<>();
            params.add(fieldValue);

            if (condition != null && !condition.isEmpty()) {
                // 简单处理 condition，实际项目中可能需要更复杂的解析
                String[] conditions = condition.split(",");
                for (String cond : conditions) {
                    String[] parts = cond.split("=");
                    if (parts.length == 2) {
                        sql.append(" AND ").append(parts[0].trim()).append(" = ?");
                        params.add(parts[1].trim()); // 注意类型转换，这里简化为字符串
                    }
                }
            }

            // 注意：spEL 的执行通常在查询到数据后，用于对查询结果进行判断，
            // 或者在查询前动态构建更复杂的查询条件，这里仅作示例。
            // 如果spEL用于查询后的判断，则此处查询逻辑可能需要调整为先查出数据再用SpEL评估。
            // 此处简化为直接查询count。

            Integer count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Integer.class);
            return count != null && count > 0;
        }

        @Override
        public Map<String, Object> queryData(String tableName, String fieldName, Object fieldValue, String condition, Map<String, Object> rootObject) {
            // 如果spEL需要查询结果进行判断，则实现此方法返回查询到的数据Map
            // 例如：SELECT * FROM tableName WHERE fieldName = ? AND ...
            // 返回null或空Map表示未查到数据
            return null; // 根据实际需求实现
        }
    }
    ```

2.  **在DTO字段上使用注解**

    ```java
    import jakarta.validation.constraints.NotBlank;
    import cn.maple.core.datasource.annotation.GXValidateDBExists;

    public class UserCreateDTO {

        @NotBlank(message = "用户名不能为空")
        @GXValidateDBExists(
            service = UserExistsValidateServiceImpl.class, // 引用上面定义的Service实现类
            fieldName = "username",                      // 数据库中的用户名字段
            tableName = "tb_user",                       // 用户表名
            message = "用户名已存在，请使用其他用户名"
        )
        private String username;

        @NotBlank(message = "邮箱不能为空")
        @GXValidateDBExists(
            service = UserExistsValidateServiceImpl.class,
            fieldName = "email",
            tableName = "tb_user",
            message = "该邮箱已被注册"
        )
        private String email;

        // 其他字段...
    }
    ```

    ```java
    import cn.maple.core.datasource.annotation.GXValidateDBExists;
    import java.math.BigDecimal;

    // 假设有一个 AccountExistsValidateServiceImpl 实现了 GXValidateDBExistsService
    // 并且 queryData 方法能够根据 accountId 查询账户信息（包括余额）
    public class TransferDTO {

        @GXValidateDBExists(
            service = AccountExistsValidateServiceImpl.class,
            fieldName = "accountId",
            tableName = "tb_account",
            message = "转出账户不存在"
        )
        private Long accountId;

        private BigDecimal amount;

        // 示例：使用SpEL校验账户余额是否足够，假设 #result 是 queryData 返回的Map
        @GXValidateDBExists(
            service = AccountExistsValidateServiceImpl.class,
            fieldName = "accountId", // 实际查询依赖 accountId
            tableName = "tb_account",
            spEL = "#result != null && #result.balance >= #root.amount", // #result是查询结果，#root是TransferDTO实例
            message = "账户余额不足以完成转账"
        )
        private Long accountIdForBalanceCheck; // 可以是一个辅助字段，或者直接用在accountId上（取决于校验逻辑）
                                            // 注意：如果直接用在accountId上，且message不同，需要用groups区分

        // Getter and Setter
        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; this.accountIdForBalanceCheck = accountId; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public Long getAccountIdForBalanceCheck() { return accountIdForBalanceCheck; }
    }
    ```

3.  **在Controller中使用 `@Valid` 或 `@Validated` 触发校验**

    ```java
    import org.springframework.validation.annotation.Validated;
    import org.springframework.web.bind.annotation.PostMapping;
    import org.springframework.web.bind.annotation.RequestBody;
    import org.springframework.web.bind.annotation.RestController;

    @RestController
    public class UserController {

        @PostMapping("/users")
        public String createUser(@Validated @RequestBody UserCreateDTO userCreateDTO) {
            // 如果校验通过，执行创建用户逻辑
            // ...
            return "User created successfully";
        }
    }
    ```

##### 3.5.1.5 高级用法

-   **带条件的唯一性校验**：
    例如，验证在某个特定部门（`dept_id=100`）内，员工工号（`employee_no`）是否唯一。
    ```java
    @GXValidateDBExists(
        service = EmployeeValidateServiceImpl.class,
        fieldName = "employee_no",
        tableName = "tb_employee",
        condition = "dept_id=100", // 静态条件
        message = "该部门下员工工号已存在"
    )
    private String employeeNo;
    ```

-   **使用SpEL进行复杂校验**：
    例如，在更新操作时，校验新的邮箱地址是否已被其他用户（排除当前用户）使用。
    ```java
    // In UserUpdateDTO
    private Long userId; // 当前用户ID

    @GXValidateDBExists(
        service = UserValidateServiceImpl.class,
        fieldName = "email",
        tableName = "tb_user",
        spEL = "#result == null || #result.user_id == #root.userId", // #result是按email查到的记录，#root是UserUpdateDTO
        message = "邮箱已被其他用户占用"
    )
    private String email;
    ```
    对应的 `GXValidateDBExistsService` 的 `queryData` 方法需要实现根据 `email` 查询用户记录（包含 `user_id`）。

-   **启用缓存**：
    对于一些不经常变动但校验频繁的数据，如产品分类是否存在，可以启用缓存。
    ```java
    @GXValidateDBExists(
        service = CategoryValidateServiceImpl.class,
        fieldName = "category_code",
        tableName = "tb_category",
        enableCache = true,
        cacheExpireSeconds = 3600, // 缓存1小时
        message = "产品分类编码不存在"
    )
    private String categoryCode;
    ```

通过 `@GXValidateDBExists` 注解，可以大大简化服务端数据校验逻辑，使其更声明式和易于维护。



#### 3.4.1 JSON类型处理

##### 3.4.1.1 相关类

- `GXJSONToListTypeHandler`：JSON转`List<Map<String, Object>>`类型处理器。用于将数据库中的JSON字符串（通常存储为CLOB或TEXT类型）与Java中的`List<Map<String, Object>>`类型进行双向转换。在读取时，如果数据库值为`NULL`或空JSON对象字符串，会返回空List。
- `GXJSONToMapTypeHandler`：JSON转`Map<String, Object>`类型处理器。用于将数据库中的JSON字符串（通常存储为CLOB或TEXT类型）与Java中的`Map<String, Object>`类型进行双向转换。在读取时，会将JSON对象的键名转换为驼峰式命名（camelCase）。如果数据库值为`NULL`或空JSON对象字符串，会返回空Map。
- `GXJsonNodeValueTypeHandler`：JsonNode值类型处理器，用于处理Jackson库的`JsonNode`类型与数据库存储之间的转换。
- `GXTreeNodeTypeHandler`：树节点类型处理器，用于处理自定义的树形结构数据（如`TreeNode`对象）与数据库存储之间的转换。

##### 3.4.1.2 工作原理

这些类型处理器基于MyBatis的`TypeHandler`机制，实现了Java特定类型与数据库JDBC类型之间的自动转换。它们主要处理将Java对象序列化为JSON字符串存入数据库，以及从数据库读取JSON字符串反序列化为Java对象的过程。

**工作流程概述：**

1.  **参数设置 (Java对象 -> 数据库记录)**:
    *   当MyBatis执行插入或更新操作，需要将Java对象（如 `Map<String, Object>` 或 `List<Map<String, Object>>`）作为参数传递给SQL语句时：
    *   对应的TypeHandler（例如 `GXJSONToMapTypeHandler`）的 `setNonNullParameter` 方法被调用。
    *   该方法将Java对象使用JSON库（如Hutool JSON）序列化为JSON字符串。
    *   序列化后的JSON字符串通过 `PreparedStatement.setCharacterStream()` 方法设置到SQL参数中，通常对应数据库的TEXT、CLOB或JSON类型的字段。

2.  **结果获取 (数据库记录 -> Java对象)**:
    *   当MyBatis执行查询操作，从 `ResultSet` 中获取数据并映射到Java实体字段时：
    *   对应的TypeHandler的 `getNullableResult` 方法被调用。
    *   该方法从 `ResultSet` 中读取指定列的值（通常是CLOB类型，通过 `ResultSet.getClob()` 读取并转换为字符串）。
    *   读取到的JSON字符串使用JSON库反序列化为目标Java类型（如 `Map<String, Object>` 或 `List<Map<String, Object>>`）。
    *   `GXJSONToMapTypeHandler` 在反序列化时，还会将JSON对象的键名转换为驼峰式命名。
    *   如果数据库中的值为NULL或无效JSON，处理器会返回null或空集合/Map，以避免空指针异常。

##### 3.4.1.3 使用方法

1. 在实体类字段上使用 `@TableField` 注解指定类型处理器：

```java
@Data
@TableName("tb_user")
public class UserEntity extends GXMyBatisModel {
    private String username;
    
    // 存储用户扩展属性，如地址、联系方式等。数据库中存储为JSON字符串。
    // 读取时，JSON对象的键名会自动转换为驼峰式。
    @TableField(typeHandler = GXJSONToMapTypeHandler.class)
    private Map<String, Object> attributes;
    
    // 存储用户标签列表，每个标签是一个Map。数据库中存储为JSON数组字符串。
    @TableField(typeHandler = GXJSONToListTypeHandler.class)
    private List<Map<String, Object>> tags;
    
    // 存储用户权限树，具体结构取决于TreeNode的定义和GXTreeNodeTypeHandler的实现
    @TableField(typeHandler = GXTreeNodeTypeHandler.class)
    private TreeNode permissionTree;
}
```

2. 数据库表结构设计：

```sql
CREATE TABLE `tb_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `attributes` text COMMENT '用户属性，JSON格式',
  `tags` text COMMENT '用户标签，JSON格式',
  `permission_tree` text COMMENT '权限树，JSON格式',
  `created_at` int DEFAULT NULL COMMENT '创建时间',
  `updated_at` int DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

3. 使用示例：

```java
@Service
public class UserServiceImpl extends GXMyBatisBaseServiceImpl<UserMapper, UserEntity> implements UserService {
    public void createUserWithAttributes(UserDTO dto) {
        UserEntity entity = new UserEntity();
        entity.setUsername(dto.getUsername());
        
        // 设置用户属性
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("address", dto.getAddress());
        attributes.put("phone", dto.getPhone());
        attributes.put("age", dto.getAge());
        entity.setAttributes(attributes);
        
        // 设置用户标签
        List<Map<String, Object>> tags = new ArrayList<>();
        Map<String, Object> tag1 = new HashMap<>();
        tag1.put("name", "VIP");
        tag1.put("level", 1);
        tags.add(tag1);
        entity.setTags(tags);
        
        // 保存用户，属性和标签会自动转换为JSON存储
        save(entity);
    }
    
    public Map<String, Object> getUserAttributes(Long userId) {
        UserEntity user = getById(userId);
        if (user == null) {
            return Collections.emptyMap();
        }
        // 直接获取属性Map，无需手动解析JSON
        return user.getAttributes();
    }
}
```

#### 3.4.2 高级用法

##### 3.4.2.1 自定义类型处理器

可以根据业务需求实现自定义类型处理器：

```java
@MappedTypes(YourCustomType.class)
public class CustomTypeHandler extends BaseTypeHandler<YourCustomType> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, YourCustomType parameter, JdbcType jdbcType) throws SQLException {
        // 将自定义类型转换为数据库类型
        String json = convertToJson(parameter);
        ps.setString(i, json);
    }
    
    @Override
    public YourCustomType getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 将数据库数据转换为自定义类型
        String json = rs.getString(columnName);
        return convertFromJson(json);
    }
    
    // 其他必要方法实现...
}
```

##### 3.4.2.2 处理复杂嵌套结构

对于复杂的嵌套JSON结构，可以结合Jackson或Hutool等工具库使用：

```java
// 实体类定义
@Data
public class ComplexStructure {
    private List<Department> departments;
    private Map<String, List<Employee>> employeesByDept;
    
    @Data
    public static class Department {
        private String name;
        private String code;
    }
    
    @Data
    public static class Employee {
        private String name;
        private Integer age;
    }
}

// 在实体类中使用
@TableField(typeHandler = GXJSONToMapTypeHandler.class)
private ComplexStructure structure;

// 使用时需要进行类型转换
ComplexStructure structure = JSONUtil.toBean(JSONUtil.toJsonStr(entity.getStructure()), ComplexStructure.class);
```

### 3.5 数据加解密

#### 3.5.1 相关类

- `GXMyBatisEncryptInterceptor`：加密拦截器，在MyBatis执行SQL前自动加密标记了`@GXSensitiveData`注解的实体类中的敏感字段。通过拦截`ParameterHandler`的`setParameters`方法实现。
- `GXMyBatisDecryptInterceptor`：解密拦截器，在MyBatis查询结果返回前自动解密标记了`@GXSensitiveData`注解的实体类中的敏感字段。通过拦截`ResultSetHandler`的`handleResultSets`方法实现。
- `GXSensitiveData`：敏感数据注解（通常位于`cn.maple.core.framework.annotation.GXSensitiveData`），用于标记实体类，表明该类包含需要进行加解密的字段。
- `GXSensitiveDataEncryptService`：敏感数据加密服务接口（通常位于`cn.maple.core.framework.service.GXSensitiveDataEncryptService`），定义了加密操作。需要开发者提供此接口的实现。
- `GXSensitiveDataDecryptService`：敏感数据解密服务接口（通常位于`cn.maple.core.framework.service.GXSensitiveDataDecryptService`），定义了解密操作。需要开发者提供此接口的实现。

#### 3.5.2 工作原理

1. **加密流程**：
   - 拦截 `ParameterHandler` 的 `setParameters` 方法
   - 检查参数对象是否标记了 `@GXSensitiveData` 注解
   - 如果是，则调用 `GXSensitiveDataEncryptService` 对敏感字段进行加密
   - 加密后的数据写入数据库

2. **解密流程**：
   - 拦截 `ResultSetHandler` 的 `handleResultSets` 方法
   - 检查结果对象是否标记了 `@GXSensitiveData` 注解
   - 如果是，则调用 `GXSensitiveDataDecryptService` 对敏感字段进行解密
   - 解密后的数据返回给应用程序

#### 3.5.3 使用方法

1. 在需要加解密字段的实体类上添加 `@GXSensitiveData` 注解：

```java
@Data
@TableName("tb_user")
@GXSensitiveData
public class UserEntity extends GXMyBatisModel {
    private String username;
    private String password;  // 将被加解密
    private String idCard;    // 将被加解密
    // 其他字段...
}
```

2. 实现加密和解密服务接口，并注册为Spring Bean：

   开发者需要提供 `GXSensitiveDataEncryptService` 和 `GXSensitiveDataDecryptService` 接口的实现。这些实现类应包含具体的加密和解密逻辑 (例如，使用AES、SM4等算法)。

```java
import cn.maple.core.framework.service.GXSensitiveDataEncryptService;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;

@Service
public class MySensitiveDataEncryptServiceImpl implements GXSensitiveDataEncryptService {
    // 假设这是你的加密密钥，实际项目中应从安全配置中获取
    private final String secretKey = "your-secret-key"; 

    @Override
    public void encrypt(Field[] declaredFields, Object parameterObject) throws IllegalAccessException {
        // 遍历对象的所有字段
        for (Field field : declaredFields) {
            // 假设我们只加密String类型的字段，并且字段名为password或idCard
            // 实际项目中，你可能需要更复杂的逻辑来判断哪些字段需要加密，
            // 例如基于字段上的特定注解，或者根据字段名列表
            if (field.getType().equals(String.class) && 
                ("password".equals(field.getName()) || "idCard".equals(field.getName()))) {
                field.setAccessible(true);
                String originalValue = (String) field.get(parameterObject);
                if (originalValue != null) {
                    // 调用你的加密方法
                    String encryptedValue = encryptValue(originalValue);
                    field.set(parameterObject, encryptedValue);
                }
            }
        }
    }

    private String encryptValue(String value) {
        // 实现具体的加密算法，例如AES加密
        // return AESUtil.encrypt(value, secretKey); // 替换为你的加密实现
        return "encrypted_" + value; // 示例加密
    }
}

import cn.maple.core.framework.service.GXSensitiveDataDecryptService;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;

@Service
public class MySensitiveDataDecryptServiceImpl implements GXSensitiveDataDecryptService {
    // 假设这是你的解密密钥
    private final String secretKey = "your-secret-key";

    @Override
    public void decrypt(Object resultObject) throws IllegalAccessException {
        if (resultObject == null) return;
        Field[] declaredFields = resultObject.getClass().getDeclaredFields();
        for (Field field : declaredFields) {
            if (field.getType().equals(String.class) && 
                ("password".equals(field.getName()) || "idCard".equals(field.getName()))) {
                field.setAccessible(true);
                String encryptedValue = (String) field.get(resultObject);
                if (encryptedValue != null) {
                    // 调用你的解密方法
                    String decryptedValue = decryptValue(encryptedValue);
                    field.set(resultObject, decryptedValue);
                }
            }
        }
    }

    private String decryptValue(String value) {
        // 实现具体的解密算法，例如AES解密
        // return AESUtil.decrypt(value, secretKey); // 替换为你的解密实现
        if (value.startsWith("encrypted_")) {
            return value.substring("encrypted_".length()); // 示例解密
        }
        return value;
    }
}
```

   **注意**：上述示例中的加密解密逻辑非常简单，仅用于演示。在实际项目中，请使用经过验证的强加密算法，并妥善管理密钥。`GXSensitiveDataEncryptService` 的 `encrypt` 方法和 `GXSensitiveDataDecryptService` 的 `decrypt` 方法会直接操作传入的对象实例，修改其字段值。

3. 在配置类中注册加解密拦截器：

```java
@Configuration
public class MybatisConfig {
    @Bean
    public GXMyBatisEncryptInterceptor encryptInterceptor() {
        return new GXMyBatisEncryptInterceptor();
    }
    
    @Bean
    public GXMyBatisDecryptInterceptor decryptInterceptor() {
        return new GXMyBatisDecryptInterceptor();
    }
}
```

### 3.6 数据库验证

#### 3.6.1 相关类

- `GXValidateDBExists`：数据库存在性验证注解（位于`cn.maple.core.datasource.annotation.GXValidateDBExists`），用于标记DTO中的字段，以验证其值在数据库中是否满足特定条件（如存在、唯一等）。
- `GXValidateDBExistsService`：验证数据库存在服务接口（位于`cn.maple.core.datasource.service.GXValidateDBExistsService`），定义了执行实际数据库查询以进行验证的方法。开发者可以提供此接口的自定义实现。
- `GXValidateDBExistsValidator`：验证数据库存在验证器（位于`cn.maple.core.datasource.service.impl.GXValidateDBExistsValidator`），实现了Jakarta Validation的`ConstraintValidator`接口，用于处理`@GXValidateDBExists`注解的验证逻辑。

#### 3.6.2 工作原理

数据库验证功能基于Jakarta Validation框架实现，通过自定义注解和验证器，在数据绑定和验证阶段自动检查字段值在数据库中是否存在。主要流程：

1. 在DTO字段上添加 `@GXValidateDBExists` 注解，指定验证参数
2. 当进行数据绑定和验证时，框架自动调用 `GXValidateDBExistsValidator` 验证器
3. 验证器根据注解参数构建查询条件，调用 `GXValidateDBExistsService` 执行实际的数据库查询
4. 根据查询结果返回验证成功或失败
5. 支持缓存验证结果、异步验证和条件验证等高级特性

#### 3.6.3 使用方法

#### 3.6.3 注解参数说明

`@GXValidateDBExists` 注解包含以下主要参数：

-   **`message`**: `String` 类型，可选。验证失败时返回的错误消息。可以包含占位符，如 `{fieldName}`。
-   **`groups`**: `Class<?>[]` 类型，可选。指定验证所属的分组，用于Jakarta Validation的分组验证功能。
-   **`payload`**: `Class<? extends Payload>[]` 类型，可选。Jakarta Validation的payload机制。
-   **`service`**: `Class<? extends GXValidateDBExistsService>` 类型，必需。指定用于执行数据库查询的 `GXValidateDBExistsService` 实现类。
-   **`fieldName`**: `String` 类型，必需。指定当前被注解字段在数据库表中对应的列名。如果DTO字段名与数据库列名一致，也需要填写。
-   **`tableName`**: `String` 类型，必需。指定要查询的数据库表名。
-   **`condition`**: `String` 类型，可选。附加的SQL查询条件（WHERE子句的一部分），例如 `status=1 AND type='A'`。注意SQL注入风险，通常用于固定条件的拼接。
-   **`spEL`**: `String` 类型，可选。Spring Expression Language (SpEL) 表达式，用于对查询结果进行更复杂的判断。表达式的上下文包含查询结果（通常是数量，`#result`）以及根对象（DTO实例，`#root`）。例如，`#result > 0` 表示记录必须存在，`#result == 0` 表示记录必须不存在（唯一性校验）。
-   **`dependOnFields`**: `String[]` 类型，可选。指定当前字段验证所依赖的其他DTO字段名。这些依赖字段的值会从当前请求上下文中获取，并可用于构建动态的`condition`或在`GXValidateDBExistsService`实现中使用。
-   **`enableCache`**: `boolean` 类型，可选，默认为 `false`。是否启用验证结果缓存。启用后，`GXValidateDBExistsValidator` 会缓存验证结果以提高性能。
-   **`cacheExpireSeconds`**: `int` 类型，可选，默认为 `60`。缓存过期时间（秒），仅当 `enableCache` 为 `true` 时有效。

#### 3.6.4 使用方法

1.  在需要验证的DTO字段上添加 `@GXValidateDBExists` 注解：

```java
import cn.maple.core.datasource.annotation.GXValidateDBExists;
import cn.maple.core.datasource.service.GXValidateDBExistsService; // 假设你有一个默认实现或自定义实现
import jakarta.validation.constraints.NotBlank;

// 假设这是你的自定义验证服务实现类
// @Service
// public class MyCustomValidateServiceImpl implements GXValidateDBExistsService { /* ... */ }

public class UserDTO {

    @NotBlank(message = "部门ID不能为空")
    @GXValidateDBExists(
        service = GXDefaultValidateDBExistsServiceImpl.class, // 使用框架提供的默认实现或你的自定义实现
        tableName = "tb_department",
        fieldName = "id",               // 对应数据库表中的 id 列
        message = "指定的部门不存在",
        spEL = "#result > 0"          // 期望记录存在
    )
    private Long departmentId;

    @GXValidateDBExists(
        service = GXDefaultValidateDBExistsServiceImpl.class,
        tableName = "tb_user",
        fieldName = "username",
        message = "用户名已存在，请使用其他用户名",
        spEL = "#result == 0"         // 期望记录不存在 (用于唯一性校验)
    )
    private String username;

    @GXValidateDBExists(
        service = GXDefaultValidateDBExistsServiceImpl.class,
        tableName = "tb_role",
        fieldName = "id",
        condition = "status = 1",   // 附加条件，确保角色是启用状态
        message = "角色不存在或未启用",
        spEL = "#result > 0"
    )
    private Long roleId;
    
    // 其他字段...
}
```

   **注意**：`GXDefaultValidateDBExistsServiceImpl` 是一个假设的默认服务实现，你需要根据实际框架提供的或自己实现的 `GXValidateDBExistsService` 来替换。

2. 实现自定义验证服务（可选）：

```java
@Service
public class DepartmentExistsService implements GXValidateDBExistsService {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public boolean validateExists(GXValidateExistsDto dto) {
        // 自定义验证逻辑
        String sql = "SELECT COUNT(1) FROM " + dto.getTableName() + 
                     " WHERE " + dto.getFieldName() + " = ? AND is_deleted = 0";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, dto.getValue());
        return count != null && count > 0;
    }
    
    @Override
    public CompletableFuture<Boolean> validateExistsAsync(GXValidateExistsDto dto) {
        // 异步验证实现
        return CompletableFuture.supplyAsync(() -> validateExists(dto));
    }
}
```

#### 3.6.5 高级特性

1.  **条件验证 (`condition`)**：通过`condition`属性添加静态的SQL WHERE子句。

    ```java
    @GXValidateDBExists(
        service = YourValidateService.class, // 替换为你的验证服务实现
        tableName = "tb_product",
        fieldName = "id",
        condition = "status = 1 AND stock > 0",  // 确保商品有库存且已上架
        message = "商品不存在、已下架或缺货",
        spEL = "#result > 0"
    )
    private Long productId;
    ```

2.  **SpEL表达式验证 (`spEL`)**：使用Spring Expression Language对查询结果（通常是记录数）进行判断。

    ```java
    // 验证用户名是否唯一 (期望查询结果为0)
    @GXValidateDBExists(
        service = YourValidateService.class, // 替换为你的验证服务实现
        tableName = "tb_users",
        fieldName = "username",
        message = "用户名已存在",
        spEL = "#result == 0" 
    )
    private String username;
    ```

3.  **依赖字段 (`dependOnFields`)**：验证可以依赖DTO中的其他字段值。这些值可以在自定义的`GXValidateDBExistsService`中通过`GXValidateExistsDto.getDependOnValues()`获取。

    ```java
    public class OrderDTO {
        private String orderType;

        @GXValidateDBExists(
            service = OrderItemValidateService.class, // 自定义服务处理依赖逻辑
            tableName = "tb_items",
            fieldName = "item_code",
            dependOnFields = {"orderType"}, // 依赖 orderType 字段
            message = "根据订单类型，该物料编码无效",
            spEL = "#result > 0"
        )
        private String itemCode;
    }
    ```
    在`OrderItemValidateService`中，你可以获取`orderType`的值来动态调整查询逻辑。

4.  **验证缓存 (`enableCache`, `cacheExpireSeconds`)**：`GXValidateDBExistsValidator`内置了对验证结果的缓存机制，通过设置`enableCache = true`来启用。

    ```java
    @GXValidateDBExists(
        service = YourValidateService.class, // 替换为你的验证服务实现
        tableName = "tb_config",
        fieldName = "config_key",
        message = "配置项不存在",
        spEL = "#result > 0",
        enableCache = true,
        cacheExpireSeconds = 3600 // 缓存1小时
    )
    private String configKey;
    ```
    注意：缓存是在`GXValidateDBExistsValidator`级别实现的，是基于内存的。对于分布式环境或需要更精细控制的缓存，可以考虑在`GXValidateDBExistsService`的实现中集成如Redis等外部缓存。`GXValidateDBExistsValidator`的缓存键是根据注解参数和字段值生成的。

3. **异步验证**：`GXValidateDBExistsService` 接口定义了 `validateExistsAsync` 方法，允许实现异步的数据库验证逻辑。这对于耗时较长的验证操作非常有用，可以避免阻塞请求处理线程。

```java
// 在你的 GXValidateDBExistsService 实现中：
@Override
public CompletableFuture<Boolean> validateExistsAsync(GXValidateExistsDto dto) {
    return CompletableFuture.supplyAsync(() -> {
        // 执行实际的数据库查询逻辑
        // 例如: return jdbcTemplate.queryForObject(sql, params, Integer.class) > 0;
        return true; // 示例
    }, asyncExecutor); // asyncExecutor 是你配置的线程池
}
```
当使用异步验证时，Jakarta Validation框架通常需要特定的集成来处理`CompletableFuture`。如果框架本身不直接支持，你可能需要在调用验证的地方手动处理异步结果，或者确保验证器能够正确处理异步完成的信号。

```java
@GXValidateDBExists(
    table = "tb_user",
    field = "username",
    async = true,  // 启用异步验证
    message = "用户名已存在"
)
private String username;
```

### 3.7 多租户支持

#### 3.7.1 相关类

- `GXTenantIdService`：租户ID服务接口，用于获取当前租户ID
- `TenantLineHandler`：MyBatis-Plus多租户处理器
- `TenantLineInnerInterceptor`：MyBatis-Plus多租户SQL拦截器

#### 3.7.2 工作原理

多租户功能基于MyBatis-Plus的多租户插件实现，通过SQL拦截的方式在查询、插入、更新和删除操作时自动添加租户条件，确保数据隔离。主要流程：

1. 通过 `GXTenantIdService` 获取当前租户ID
2. `TenantLineInnerInterceptor` 拦截SQL语句
3. 根据租户ID动态修改SQL，添加租户条件
4. 支持表级别的租户过滤控制，可以指定哪些表不需要进行租户过滤
5. 使用缓存优化表结构检查，提高性能

#### 3.7.3 使用方法

1. 实现 `GXTenantIdService` 接口并注册为Spring Bean：

```java
@Service
public class TenantIdServiceImpl implements GXTenantIdService {
    @Autowired
    private UserContextHolder userContextHolder;
    
    @Override
    public Object getTenantId() {
        // 从当前用户上下文中获取租户ID
        UserInfo currentUser = userContextHolder.getCurrentUser();
        return currentUser != null ? currentUser.getTenantId() : null;
    }
    
    @Override
    public Expression getTenantExpression(String tableName, String tenantIdColumn) {
        // 获取当前租户ID
        Object tenantId = getTenantId();
        if (tenantId == null) {
            return null; // 如果没有租户ID，则不添加租户条件
        }
        // 返回租户条件表达式
        return new LongValue(Convert.toLong(tenantId));
    }
    
    @Override
    public boolean ignoreTable(String tableName) {
        // 指定哪些表不需要进行租户过滤
        return "sys_tenant".equalsIgnoreCase(tableName) || 
               "sys_config".equalsIgnoreCase(tableName);
    }
}
```

2. 在配置类中启用多租户插件：

```java
@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 添加多租户插件
        TenantLineInnerInterceptor tenantLineInnerInterceptor = new TenantLineInnerInterceptor();
        tenantLineInnerInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // 通过GXTenantIdService获取租户ID表达式
                GXTenantIdService tenantIdService = GXSpringContextUtils.getBean(GXTenantIdService.class);
                return tenantIdService.getTenantExpression(null, null);
            }
            
            @Override
            public String getTenantIdColumn() {
                // 指定租户ID的列名
                return "tenant_id";
            }
            
            @Override
            public boolean ignoreTable(String tableName) {
                // 通过GXTenantIdService判断是否忽略表
                GXTenantIdService tenantIdService = GXSpringContextUtils.getBean(GXTenantIdService.class);
                return tenantIdService.ignoreTable(tableName);
            }
        });
        interceptor.addInnerInterceptor(tenantLineInnerInterceptor);
        
        return interceptor;
    }
}
```

3. 确保实体类中包含租户ID字段：

```java
@Data
@TableName("tb_user")
public class UserEntity extends GXMyBatisModel {
    private String username;
    private String password;
    
    // 租户ID字段，通常由框架自动填充
    private Long tenantId;
    
    // 其他字段...
}
```

#### 3.7.4 高级特性

1. **动态租户ID列名**：可以为不同的表指定不同的租户ID列名

```java
@Override
public String getTenantIdColumn(String tableName) {
    // 根据表名返回不同的租户ID列名
    if ("tb_order".equals(tableName)) {
        return "order_tenant_id";
    }
    return "tenant_id"; // 默认列名
}
```

2. **条件性租户过滤**：可以根据业务需求动态决定是否应用租户过滤

```java
// 临时禁用租户过滤
GXTenantContextHolder.setIgnore(true);
try {
    // 执行跨租户查询
    return userMapper.selectList(null);
} finally {
    // 恢复租户过滤
    GXTenantContextHolder.clear();
}
```

### 3.8 缓存支持

#### 3.8.1 相关类

- `GXMybatisPlusRedissonCache`：基于Redisson实现的MyBatis二级缓存
- `GXMyBatisPlusConfig`：MyBatis-Plus配置类，包含缓存配置

#### 3.8.2 工作原理

缓存功能基于MyBatis的二级缓存机制和Redisson实现，通过缓存查询结果减少数据库访问，提高系统性能。主要流程：

1. 在Mapper接口上添加`@CacheNamespace`注解，指定缓存实现类和属性
2. 当执行查询操作时，MyBatis首先检查二级缓存中是否存在结果
3. 如果缓存命中，直接返回缓存结果，不访问数据库
4. 如果缓存未命中，执行数据库查询，并将结果存入缓存
5. 当执行更新、插入或删除操作时，自动清除相关缓存

#### 3.8.3 使用方法

1. 在Mapper接口上添加`@CacheNamespace`注解：

```java
@Mapper
@CacheNamespace(implementation = GXMybatisPlusRedissonCache.class, flushInterval = 300000) // 缓存5分钟
public interface UserMapper extends GXBaseMapper<UserEntity> {
    // 自定义方法...
}
```

2. 配置Redisson客户端（在Spring配置文件中）：

```java
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://localhost:6379")
              .setDatabase(0);
        return Redisson.create(config);
    }
    
    @Bean
    public GXRedissonCacheService redissonCacheService(RedissonClient redissonClient) {
        return new GXRedissonCacheServiceImpl(redissonClient);
    }
}
```

3. 使用缓存：

```java
@Service
public class UserServiceImpl extends GXMyBatisBaseServiceImpl<UserMapper, UserEntity> implements UserService {
    // 查询方法会自动使用缓存
    public UserEntity getUserById(Long id) {
        return getById(id); // 结果会被缓存
    }
    
    // 更新方法会自动清除缓存
    public boolean updateUser(UserEntity user) {
        return updateById(user); // 会自动清除相关缓存
    }
}
```

#### 3.8.4 高级特性

1. **自定义缓存键生成**：通过MD5处理缓存键，减少内存占用

```java
// GXMybatisPlusRedissonCache内部实现
private String getStringKey(Object key) {
    // 使用MD5处理缓存键，减少内存占用
    return DigestUtils.md5DigestAsHex(key.toString().getBytes());
}
```

2. **缓存过期时间控制**：通过`@CacheNamespace`的`flushInterval`属性控制缓存过期时间

```java
@CacheNamespace(
    implementation = GXMybatisPlusRedissonCache.class, 
    flushInterval = 3600000 // 缓存1小时
)
```

3. **选择性缓存**：对于特定方法禁用缓存

```java
@Mapper
@CacheNamespace(implementation = GXMybatisPlusRedissonCache.class)
public interface ProductMapper extends GXBaseMapper<ProductEntity> {
    // 使用缓存
    @Override
    ProductEntity selectById(Serializable id);
    
    // 禁用缓存
    @Options(useCache = false)
    List<ProductEntity> selectHotProducts();
}
```

## 4. 配置说明

### 4.1 数据源配置

在 `application.yml` 或 `datasource.yml` 中配置数据源：

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/maple?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
      username: root
      password: root
      initial-size: 10
      max-active: 100
      min-idle: 10
      max-wait: 60000
      pool-prepared-statements: true
      max-pool-prepared-statement-per-connection-size: 20
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
      filter:
        stat:
          log-slow-sql: true
          slow-sql-millis: 1000
          merge-sql: false
        wall:
          config:
            multi-statement-allow: true
```

Merge rule for Druid datasource options:

- `dynamic.datasource.<name>.*` has the highest priority for each datasource.
- Missing `dynamic.datasource.<name>.*` options inherit from `spring.datasource.druid.*`.
- Framework defaults are used only when neither of the above is configured.
- Use `connection-properties: ""` under a dynamic datasource to explicitly disable inheritance from `spring.datasource.druid.connection-properties`.
- `filter.stat.*`, `filter.wall.*`, and `filter.slf4j.*` follow the same priority rule; explicit filter options override values read from Druid `connection-properties`.
- To print Druid SQL statements, keep `logging.level.druid.sql.Statement: debug` and set `filter.slf4j.statement-log-enabled: true`; keep it `false` by default for production performance.

### 4.2 多数据源配置

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://localhost:3306/maple_master?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
          username: root
          password: root
        slave:
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://localhost:3306/maple_slave?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
          username: root
          password: root
```

### 4.3 MyBatis-Plus配置

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: cn.maple.**.entity
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    call-setters-on-nulls: true
    jdbc-type-for-null: null
```

## 5. 最佳实践

### 5.1 实体类设计

```java
@Data
@TableName("tb_user")
public class UserEntity extends GXMyBatisModel {
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 用户属性，使用JSON存储
     */
    @TableField(typeHandler = GXJSONToMapTypeHandler.class)
    private Map<String, Object> attributes;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
}
```

### 5.2 Service层实现

```java
@Service
public class UserServiceImpl extends GXMyBatisBaseServiceImpl<UserMapper, UserEntity> implements UserService {
    /**
     * 创建用户
     */
    @Override
    public boolean createUser(UserDTO userDTO) {
        UserEntity entity = convertToEntity(userDTO);
        return save(entity);
    }
    
    /**
     * 更新用户
     */
    @Override
    @GXDataSource("master") // 指定使用主库
    public boolean updateUser(UserDTO userDTO) {
        UserEntity entity = convertToEntity(userDTO);
        return updateById(entity);
    }
    
    /**
     * 查询用户列表
     */
    @Override
    @GXDataFilter(tableAlias = "u", tenantFilter = true) // 数据权限过滤
    public List<UserEntity> listUsers(Map<String, Object> params) {
        return list(getWrapper(params));
    }
    
    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<UserEntity> getWrapper(Map<String, Object> params) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        
        // 添加查询条件
        String username = (String) params.get("username");
        if (StringUtils.isNotBlank(username)) {
            wrapper.like(UserEntity::getUsername, username);
        }
        
        Boolean enabled = (Boolean) params.get("enabled");
        if (enabled != null) {
            wrapper.eq(UserEntity::getEnabled, enabled);
        }
        
        return wrapper;
    }
    
    /**
     * DTO转Entity
     */
    private UserEntity convertToEntity(UserDTO dto) {
        UserEntity entity = new UserEntity();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }
}
```

### 5.3 事件监听实现

```java
@Component
@GXMyBatisListener
public class UserEventListener extends GXMyBatisSyncListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    /**
     * 监听用户创建事件
     */
    @Override
    public void onSaveEntity(GXMyBatisModelSaveEntityEvent event) {
        if (event.getSource() instanceof UserEntity) {
            UserEntity user = (UserEntity) event.getSource();
            logger.info("用户创建事件：{}", user.getUsername());
            // 执行其他业务逻辑
        }
    }
    
    /**
     * 监听用户更新事件
     */
    @Override
    public void onUpdateEntity(GXMyBatisModelUpdateEntityEvent event) {
        if (event.getSource() instanceof UserEntity) {
            UserEntity user = (UserEntity) event.getSource();
            logger.info("用户更新事件：{}", user.getUsername());
            // 执行其他业务逻辑
        }
    }
}
```

## 6. 常见问题

### 6.1 多数据源切换问题

**问题**：在使用 `@GXDataSource` 注解切换数据源时，事务管理可能会失效。

**解决方案**：
1. 确保在同一个事务中不要切换数据源，或者使用分布式事务管理
2. 在方法级别上使用 `@Transactional` 注解时，确保该方法不会切换数据源
3. 如果必须在事务中切换数据源，考虑使用 Spring 的 `TransactionTemplate` 手动管理事务

```java
@Autowired
private TransactionTemplate transactionTemplate;

public void complexOperation() {
    // 主库事务
    transactionTemplate.execute(status -> {
        // 主库操作
        return null;
    });
    
    // 从库事务
    GXDynamicContextHolder.push("slave");
    try {
        transactionTemplate.execute(status -> {
            // 从库操作
            return null;
        });
    } finally {
        GXDynamicContextHolder.poll();
    }
}
```

### 6.2 数据过滤条件不生效

**问题**：使用 `@GXDataFilter` 注解但数据过滤条件不生效。

**解决方案**：
1. 确保正确实现了 `GXDataScopeService` 接口
2. 检查表别名是否正确设置
3. 确保切面被正确注册为Spring Bean
4. 检查 SQL 语句是否被正确拦截，可以开启 SQL 日志查看实际执行的 SQL
5. 确认 `GXDataFilterInterceptor` 已正确注册并且优先级设置合理

### 6.3 自动填充字段不生效

**问题**：实体类继承了 `GXMyBatisModel` 但自动填充字段不生效。

**解决方案**：
1. 确保 `GXAutoFillMetaObjectHandler` 被正确注册为Spring Bean
2. 检查字段是否使用了正确的注解（如 `@TableField(fill = FieldFill.INSERT)`）
3. 确认实体类中的字段名与 `GXMyBatisModel` 中定义的一致
4. 检查 `GXMyBatisAutoFillMetaObjectService` 实现是否正确返回填充值
5. 使用 MyBatis-Plus 的 SQL 打印功能查看实际执行的 SQL 语句

### 6.4 缓存相关问题

**问题**：使用 MyBatis 二级缓存但缓存不生效或出现脏数据。

**解决方案**：
1. 确保 Redisson 客户端和缓存服务正确配置
2. 检查 Mapper 接口是否正确添加了 `@CacheNamespace` 注解
3. 确认实体类实现了 `Serializable` 接口
4. 对于频繁变化的数据，考虑禁用缓存或设置较短的过期时间
5. 在多实例部署环境中，确保使用分布式缓存（如 Redis）而非本地缓存

### 6.5 多租户数据隔离问题

**问题**：多租户环境下数据隔离不完全，出现跨租户数据访问。

**解决方案**：
1. 确保所有表都包含租户 ID 字段
2. 检查 `GXTenantIdService` 实现是否正确返回当前租户 ID
3. 确认 `TenantLineInnerInterceptor` 已正确配置并注册
4. 对于需要跨租户访问的表，在 `ignoreTable` 方法中明确排除
5. 检查是否有代码手动设置了 `GXTenantContextHolder.setIgnore(true)` 但未恢复

### 6.6 数据加解密问题

**问题**：敏感数据加解密不生效或解密后数据错误。

**解决方案**：
1. 确保实体类正确标记了 `@GXSensitiveData` 注解
2. 检查加解密服务实现是否正确注册为 Spring Bean
3. 确认加解密算法的一致性，避免加密和解密使用不同的算法或密钥
4. 对于已有数据的迁移，考虑编写脚本进行批量加密
5. 检查拦截器注册顺序，确保加解密拦截器优先级合理

## 7. 性能优化建议

### 7.1 数据源配置优化

1. 根据应用特点合理设置连接池参数
2. 对于读多写少的应用，增加从库数量并配置读写分离
3. 使用 Druid 的防火墙功能过滤不安全的 SQL
4. 开启慢 SQL 日志，定期分析并优化性能问题

### 7.2 查询性能优化

1. 合理使用 MyBatis-Plus 的分页插件，避免大数据量查询
2. 对于热点数据，配置合适的缓存策略
3. 使用 `LambdaQueryWrapper` 构建查询条件，避免硬编码 SQL
4. 对于复杂查询，考虑使用自定义 SQL 而非实体查询

### 7.3 事务管理优化

1. 合理设置事务隔离级别，避免长事务
2. 对于只读操作，使用 `@Transactional(readOnly = true)` 提高性能
3. 在多数据源环境中，避免跨数据源事务

## 8. 总结

Leaf-Base-Datasource 模块作为 Maple Leaf Framework 的核心数据库操作组件，提供了丰富的功能特性：

1. **动态数据源**：支持多数据源配置和动态切换，满足复杂业务场景需求
2. **数据权限过滤**：提供细粒度的数据访问控制，确保数据安全
3. **MyBatis 增强**：扩展 MyBatis-Plus 功能，简化开发流程
4. **自动填充**：自动处理审计字段，减少重复代码
5. **事件监听**：支持同步和异步事件处理，便于业务解耦
6. **数据类型处理**：提供 JSON 等复杂类型的处理能力
7. **数据加解密**：保护敏感数据安全
8. **数据库验证**：简化数据验证逻辑
9. **多租户支持**：内置多租户数据隔离机制
10. **缓存支持**：基于 Redisson 的高性能缓存实现

通过合理使用这些功能，开发人员可以显著提高开发效率，减少重复代码，同时保证应用的性能和安全性。在实际应用中，应根据业务需求选择合适的功能组合，并遵循最佳实践进行开发。

## 9. 参考资料

- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [Druid 官方文档](https://github.com/alibaba/druid/wiki)
- [P6Spy 官方文档](https://p6spy.readthedocs.io/)
- [Redisson 官方文档](https://github.com/redisson/redisson/wiki)
- [Spring Transaction 官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction)


本文档旨在帮助开发者理解和使用 `leaf-base-datasource` 模块。如有疑问或建议，请联系模块维护者。
