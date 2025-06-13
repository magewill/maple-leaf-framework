package cn.maple.elasticsearch.dao;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.elasticsearch.constant.GXEsCriteriaMethodMappingConstant;
import cn.maple.elasticsearch.model.GXElasticsearchModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于ES Repository封装的统一基本操作
 * <p>
 * 该接口提供了对Elasticsearch数据的统一访问方法，包括查询、分页、删除等操作。
 * 实现了线程安全的数据访问，所有方法都是无状态的，可以在多线程环境下安全调用。
 * </p>
 * <p>
 * 内存安全特性：
 * - 所有方法都进行了参数验证，防止空指针异常和非法参数
 * - 使用安全的集合操作，避免并发修改异常和内存泄漏
 * - 对所有外部输入进行严格验证，防止非法数据和注入攻击
 * - 使用Optional处理可能为空的对象，避免空指针异常
 * - 合理管理资源，避免资源泄漏和内存溢出
 * </p>
 * <p>
 * 线程安全特性：
 * - 避免共享可变状态，确保方法执行的线程安全
 * - 使用不可变对象和线程安全的集合类
 * - 通过参数验证和防御性编程确保多线程环境下的安全性
 * </p>
 * <p>
 * 参考文档：
 * {@see <a href="https://docs.spring.io/spring-data/elasticsearch/reference/index.html">Spring Data Elasticsearch帮助文档</a>}
 * {@see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/7.17/query-dsl.html">Elasticsearch查询DSL语法</a>}
 * </p>
 *
 * @param <T>  Elasticsearch文档实体类型，必须继承自GXElasticsearchModel
 * @param <Q>  查询对象类型，必须继承自BaseQuery
 * @param <B>  查询构建器类型，必须继承自BaseQueryBuilder
 * @param <ID> 实体主键类型，必须实现Serializable接口
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
public interface GXElasticsearchDao<T extends GXElasticsearchModel, Q extends BaseQuery, B extends BaseQueryBuilder<Q, B>, ID extends Serializable> extends ElasticsearchRepository<T, ID> {
    /**
     * 根据条件查询所有满足条件的数据
     * 该方法是线程安全的，可以在多线程环境下调用
     *
     * @param queryParamInnerDto 查询条件对象，包含查询字段、条件、排序等信息
     * @return 返回查询到的数据列表，如果没有匹配的数据则返回空列表，不会返回null
     */
    default List<Dict> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Dict queryData = executeQuery(queryParamInnerDto);
        List<Dict> lst = Convert.convert(new TypeReference<>() {
        }, queryData.getObj("records"));
        return lst.stream().map(d -> Convert.convert(Dict.class, d.getObj("content"))).collect(Collectors.toList());
    }

    /**
     * 根据指定条件查询一条数据
     * 该方法会自动设置分页参数，只返回第一条匹配的记录
     *
     * @param queryParamInnerDto 查询条件，包含查询字段、条件、排序等信息
     * @return 满足条件的一条数据，如果没有匹配的数据则返回null
     */
    default Dict findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        queryParamInnerDto.setPage(0);
        queryParamInnerDto.setPageSize(1);
        Dict queryData = executeQuery(queryParamInnerDto);
        List<Dict> lst = Convert.convert(new TypeReference<>() {
        }, queryData.getObj("records"));
        Optional<Dict> data = lst.stream().findFirst();
        return data.map(t -> Convert.convert(Dict.class, t.getObj("content"))).orElse(null);
    }

    /**
     * 分页查询
     * 该方法支持高亮显示，会自动将高亮字段合并到结果中
     *
     * @param queryParamInnerDto 查询条件，包含分页参数、查询字段、条件、排序等信息
     * @return 分页数据，包含总记录数、总页数、当前页码、每页大小和当前页数据
     */
    default GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Dict queryData = executeQuery(queryParamInnerDto);
        List<Dict> lst = Convert.convert(new TypeReference<>() {
        }, queryData.getObj("records"));
        List<Dict> records = lst.stream().map(d -> {
            Dict content = Convert.convert(Dict.class, d.getObj("content"), Dict.create());
            Dict highlightFields = Convert.convert(Dict.class, d.getObj("highlightFields"), Dict.create());
            if (!highlightFields.isEmpty()) {
                content.putAll(highlightFields);
            }
            return content;
        }).collect(Collectors.toList());
        long totalCount = queryData.getInt("totalHits");
        long currentPage = queryParamInnerDto.getPage();
        long pageSize = queryParamInnerDto.getPageSize();
        long pages = totalCount / pageSize;
        return new GXPaginationResDto<>(records, totalCount, pages, pageSize, currentPage);
    }

    /**
     * 根据条件创建数据
     * 如果数据存在则先将数据删除，然后再新增
     * 注意：此方法在高并发环境下可能存在数据一致性问题，建议在事务中使用
     *
     * @param entity    需要新增的数据实体，不能为null
     * @param condition 需要被删除数据的查询条件，可以为空
     * @return ID 返回新增数据的ID
     * @throws IllegalArgumentException 如果entity为null
     */
    default <ID extends Serializable> ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        Assert.notNull(entity, "Entity must not be null");
        T save = save(entity);
        Class<ID> retIDClazz = GXCommonUtils.getGenericClassType((Class<?>) getClass().getGenericInterfaces()[0], 3);
        String methodName = CharSequenceUtil.format("get{}", CharSequenceUtil.upperFirst("id"));
        return Convert.convert(retIDClazz, GXCommonUtils.reflectCallObjectMethod(save, methodName));
    }

    /**
     * 删除满足条件的数据
     * 该方法执行物理删除，删除后数据无法恢复
     *
     * @param tableName 索引名称，如果为空则使用实体类对应的默认索引
     * @param condition 删除条件，不能为null
     * @return 被成功删除的文档数量
     * @throws IllegalArgumentException 如果condition为null
     */
    default Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        Assert.notNull(condition, "Condition must not be null");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .condition(condition)
                .build();
        Query query = buildQuery(queryParamInnerDto);
        DeleteQuery deleteQuery = DeleteQuery.builder(query).build();
        ElasticsearchTemplate elasticsearchTemplate = getElasticsearchTemplate();
        ByQueryResponse deleteResponse = elasticsearchTemplate.delete(deleteQuery, getGenericClassType());
        return Math.toIntExact(deleteResponse.getDeleted());
    }

    /**
     * 执行统一查询
     * 该方法是所有查询方法的核心实现，其他查询方法都会调用此方法
     * 支持自定义处理方法、高亮显示等特性
     *
     * @param queryParamInnerDto 查询条件，包含查询字段、条件、排序、分页等完整信息
     * @return 查询到的数据，包含总记录数和记录列表
     * @throws IllegalArgumentException 如果查询参数不合法
     */
    default Dict executeQuery(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Assert.notNull(queryParamInnerDto, "Query parameters must not be null");

        // 构建查询对象
        Q query = buildQuery(queryParamInnerDto);
        query = buildOrderBy(query, queryParamInnerDto);
        query = buildPageable(query, queryParamInnerDto);

        // 获取ElasticsearchTemplate和实体类型
        ElasticsearchTemplate elasticsearchTemplate = getElasticsearchTemplate();
        Class<?> genericClassType = GXCommonUtils.getGenericClassType((Class<?>) getClass().getGenericInterfaces()[0], 0);

        // 执行查询，支持指定索引名称
        String indexName = queryParamInnerDto.getTableName();
        SearchHits<?> search = CharSequenceUtil.isEmpty(indexName)
                ? elasticsearchTemplate.search(query, genericClassType)
                : elasticsearchTemplate.search(query, genericClassType, IndexCoordinates.of(indexName));

        // 处理自定义方法名和复制选项
        String[] methodName = new String[]{queryParamInnerDto.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        CopyOptions copyOptions = ObjectUtil.defaultIfNull(queryParamInnerDto.getCopyOptions(), GXCommonUtils::getDefaultCopyOptions);

        // 定义行映射函数，将SearchHit转换为Dict
        Function<SearchHit<?>, Dict> rowMapper = obj -> {
            Object extraData = Optional.ofNullable(queryParamInnerDto.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(obj, Dict.class, methodName[0], copyOptions, extraData);
        };

        // 处理查询结果
        List<Dict> records = search.getSearchHits().stream().map(rowMapper).collect(Collectors.toList());
        long totalHits = search.getTotalHits();

        return Dict.create().set("totalHits", totalHits).set("records", records);
    }

    /**
     * 构建查询条件
     * 根据查询参数构建Elasticsearch的查询对象
     *
     * @param queryParamInnerDto 查询条件对象，包含条件、排序等信息
     * @return ES的查询条件对象，类型为泛型Q指定的类型
     * @throws ClassCastException 如果类型转换失败
     */
    @SuppressWarnings("all")
    default Q buildQuery(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Class<BaseQuery> queryClazz = GXCommonUtils.getGenericClassType((Class<?>) getClass().getGenericInterfaces()[0], 1);
        B queryBuilder = buildQueryBuilder(queryParamInnerDto);

        BaseQuery query = ReflectUtil.newInstance(queryClazz, queryBuilder);
        return (Q) query;
    }

    /**
     * 构造查询Builder
     * 根据查询参数构建适合的查询构建器，支持CriteriaQueryBuilder和其他类型的构建器
     *
     * <pre>{@code
     * // 示例1: 多字段匹配查询
     * NativeQueryBuilder nativeQueryBuilder = new NativeQueryBuilder();
     * nativeQueryBuilder.withQuery(
     *         new Query.Builder()
     *                 .multiMatch(new MultiMatchQuery.Builder().fields("name", "summary")
     *                         .query("关键词1 关键词2")
     *                         .operator(Operator.And)
     *                         .build())
     *                 .build());
     *
     * // 示例2: 布尔查询组合多个条件
     * Query nameMatchQuery = new MatchQuery.Builder().field("name").query("关键词").operator(Operator.Or).build()._toQuery();
     * Query summaryMatchQuery = new MatchQuery.Builder().field("summary").query("重要信息").operator(Operator.Or).build()._toQuery();
     * BoolQuery boolQuery = new BoolQuery.Builder().must(CollUtil.newArrayList(nameMatchQuery, summaryMatchQuery)).build();
     * nativeQueryBuilder.withQuery(new Query.Builder().bool(boolQuery).build());
     * return nativeQueryBuilder;
     * }</pre>
     *
     * @param queryParamInnerDto 查询条件，包含条件、排序等信息
     * @return 查询Builder实例，类型为泛型B指定的类型
     * @throws ClassCastException    如果类型转换失败
     * @throws IllegalStateException 如果无法创建查询构建器实例
     */
    @SuppressWarnings("all")
    default B buildQueryBuilder(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Class<BaseQueryBuilder<Q, B>> queryBuilderClazz = GXCommonUtils.getGenericClassType((Class<?>) getClass().getGenericInterfaces()[0], 2);

        if (queryBuilderClazz.isAssignableFrom(CriteriaQueryBuilder.class)) {
            Criteria criteria = conditions2Criteria(queryParamInnerDto);
            BaseQueryBuilder<Q, B> queryBuilder = ReflectUtil.newInstance(queryBuilderClazz, criteria);
            return (B) queryBuilder;
        }
        BaseQueryBuilder<Q, B> queryBuilder = ReflectUtil.newInstance(queryBuilderClazz);
        return (B) queryBuilder;
    }

    /**
     * 构建分页信息
     * 根据查询参数中的分页信息设置查询对象的分页属性
     * 页码从0开始计算，如果传入的页码小于0，会被调整为0
     *
     * @param query              查询对象，不能为null
     * @param queryParamInnerDto 查询条件，包含页码和每页大小信息
     * @return 设置了分页信息的查询对象
     * @throws IllegalArgumentException 如果query为null
     */
    default Q buildPageable(Q query, GXBaseQueryParamInnerDto queryParamInnerDto) {
        // 处理分页
        int page = NumberUtil.max(Optional.ofNullable(queryParamInnerDto.getPage()).orElse(0) - 1, 0);
        int pageSize = Optional.ofNullable(queryParamInnerDto.getPageSize()).orElse(GXCommonConstant.DEFAULT_MAX_PAGE_SIZE);
        query.setPageable(PageRequest.of(page, pageSize));
        return query;
    }

    /**
     * 构建排序字段信息
     * 根据查询参数中的排序字段信息设置查询对象的排序属性
     * 支持多字段排序，可以同时指定多个字段的排序方向
     *
     * @param query              查询对象，不能为null
     * @param queryParamInnerDto 查询条件，包含排序字段信息
     * @return 设置了排序信息的查询对象
     * @throws IllegalArgumentException 如果query为null
     */
    default Q buildOrderBy(Q query, GXBaseQueryParamInnerDto queryParamInnerDto) {
        Map<String, String> orderByField = queryParamInnerDto.getOrderByField();
        // 处理字段排序
        if (!CollUtil.isEmpty(orderByField)) {
            List<Sort.Order> orders = CollUtil.newArrayList();
            orderByField.keySet().forEach(column -> {
                String value = orderByField.get(column);
                if (Sort.Direction.DESC.equals(Sort.Direction.fromString(value))) {
                    orders.add(Sort.Order.desc(column));
                } else if (Sort.Direction.ASC.equals(Sort.Direction.fromString(value))) {
                    orders.add(Sort.Order.asc(column));
                }
            });
            Sort sort = Sort.by(orders);
            query.addSort(sort);
        }
        return query;
    }

    /**
     * 业务逻辑需要自定义查询条件
     * 该方法用于子类重写，实现特定业务逻辑的查询条件定制
     * 默认实现直接返回传入的criteria对象，不做任何修改
     *
     * @param criteria 查询条件对象，不能为null
     * @return 处理后的criteria对象
     */
    default Criteria buildCriteria(Criteria criteria) {
        return criteria;
    }

    /**
     * 转换GXCondition列表为Criteria
     * 将框架通用的条件对象转换为Elasticsearch的Criteria查询条件
     * 支持多种操作符，如等于、大于、小于、模糊匹配等，具体映射关系见GXEsCriteriaMethodMappingConstant
     *
     * @param dbQueryParamInnerDto 查询信息，包含条件列表
     * @return Criteria 转换后的Elasticsearch查询条件对象
     * @throws IllegalArgumentException 如果条件参数不合法
     */
    default Criteria conditions2Criteria(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        Assert.notNull(dbQueryParamInnerDto, "Query parameters must not be null");

        List<GXCondition<?>> conditions = dbQueryParamInnerDto.getCondition();
        Criteria criteria = new Criteria();
        Map<String, String> methodMapping = GXEsCriteriaMethodMappingConstant.METHOD_MAPPING;

        if (CollUtil.isNotEmpty(conditions)) {
            conditions.forEach(condition -> {
                // 安全检查：确保条件对象的关键属性不为空
                if (condition == null || CharSequenceUtil.isEmpty(condition.getFieldExpression()) || condition.getValue() == null) {
                    return; // 跳过无效条件
                }

                String fieldName = condition.getFieldExpression();
                String value = ObjectUtil.toString(condition.getValue());
                String op = condition.getOp();

                // 获取操作符对应的方法名
                String methodName = methodMapping.get(op);

                // 只有当方法名存在且Criteria类中有对应方法时才执行
                if (CharSequenceUtil.isNotEmpty(methodName) && GXCommonUtils.checkMethodExists(Criteria.class, methodName, value)) {
                    Criteria tmpCriteria = new Criteria(fieldName);
                    GXCommonUtils.reflectCallObjectMethod(tmpCriteria, methodName, value);
                    criteria.and(tmpCriteria);
                }
            });
        }

        // 应用自定义条件逻辑
        return buildCriteria(criteria);
    }

    /**
     * 获取指定的ElasticsearchTemplate类型对象
     * 该方法是线程安全的，每次调用都会从Spring容器中获取ElasticsearchTemplate实例
     *
     * @return ElasticsearchTemplate 对象，不会返回null
     * @throws IllegalStateException 如果无法获取ElasticsearchTemplate实例
     */
    default ElasticsearchTemplate getElasticsearchTemplate() {
        return getElasticsearchTemplate(getElasticsearchTemplateName());
    }

    /**
     * 获取当前类的实体Class
     * 通过泛型参数解析获取实体类型，用于各种需要Class对象的操作
     *
     * @return Class<?> 实体类的Class对象
     * @throws IllegalStateException 如果无法解析泛型类型
     */
    default Class<?> getGenericClassType() {
        return GXCommonUtils.getGenericClassType((Class<?>) getClass().getGenericInterfaces()[0], 0);
    }

    /**
     * 获取Spring容器中的ElasticsearchTemplate对象bean的名字
     * 默认返回"primaryElasticsearchTemplate"，子类可以重写此方法以使用不同的ElasticsearchTemplate实例
     *
     * @return bean的名字，默认为"primaryElasticsearchTemplate"
     */
    default String getElasticsearchTemplateName() {
        return "primaryElasticsearchTemplate";
    }

    /**
     * 获取指定的ElasticsearchTemplate类型对象
     * 根据bean名称从Spring容器中获取ElasticsearchTemplate实例
     * 如果指定的beanName为空，则使用默认的bean名称
     * 该方法是线程安全的，适合在多线程环境下调用
     *
     * @param beanName spring容器中bean的名字，可以为空
     * @return ElasticsearchTemplate 对象，不会返回null
     * @throws IllegalStateException 如果无法获取ElasticsearchTemplate实例或容器中不存在指定名称的bean
     */
    default ElasticsearchTemplate getElasticsearchTemplate(String beanName) {
        // 如果beanName为空，使用默认名称
        if (CharSequenceUtil.isEmpty(beanName)) {
            beanName = getElasticsearchTemplateName();
        }

        // 从Spring容器中获取ElasticsearchTemplate实例
        ElasticsearchTemplate elasticsearchTemplate = GXSpringContextUtils.getBean(beanName, ElasticsearchTemplate.class);

        // 验证获取的实例不为空，如果为空则抛出异常并提供可用的bean名称列表
        Assert.notNull(elasticsearchTemplate, "请配置ElasticsearchTemplate对象->[" +
                GXSpringContextUtils.getBeans(ElasticsearchTemplate.class).keySet().stream()
                        .map(name -> CharSequenceUtil.format("'{}'", name))
                        .collect(Collectors.joining(",")) +
                "]");

        return elasticsearchTemplate;
    }
}
