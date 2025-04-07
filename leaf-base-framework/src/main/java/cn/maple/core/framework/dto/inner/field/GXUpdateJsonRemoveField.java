package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON字段路径移除更新类
 * <p>
 * 用于从JSON类型字段中移除特定路径的值。
 * 使用MySQL的JSON_REMOVE函数实现，能够正确处理JSON路径的转义。
 * </p>
 *
 * @author 塵子曦
 */
@Slf4j
public class GXUpdateJsonRemoveField extends GXUpdateField<String> {
    /**
     * JSON路径，不包含根路径标识($)
     */
    private final String path;

    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，可以为空
     * @param fieldName 字段名，会自动转换为下划线格式
     * @param path JSON路径，不包含根路径标识
     */
    public GXUpdateJsonRemoveField(String tableNameAlias, String fieldName, String path) {
        super(tableNameAlias, fieldName, null);
        this.path = path;
    }

    /**
     * 获取JSON_REMOVE函数调用表达式
     * <p>
     * 构建JSON_REMOVE函数调用，从JSON字段中移除指定路径的值。
     * </p>
     *
     * @return JSON_REMOVE函数调用表达式
     * @throws GXSqlInjectionException 当检测到JSON路径中有SQL注入风险时抛出
     */
    @Override
    public String getFieldValue() {
        // 构建完整的JSON路径
        String jsonPathStr = CharSequenceUtil.format("$.{}", path);
        
        try {
            // 使用专门的JSON路径转义方法处理
            String escapedPath = GXDBStringEscapeUtils.escapeJsonPath(jsonPathStr);
            // 根据是否有表名别名构建不同的SQL表达式
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("JSON_REMOVE({} , '{}')" , fieldName, escapedPath);
            } else {
                return CharSequenceUtil.format("JSON_REMOVE({}.{} , '{}')" , tableNameAlias, fieldName, escapedPath);
            }
        } catch (GXSqlInjectionException e) {
            log.error("JSON字段路径移除更新时检测到SQL注入风险: {}", jsonPathStr);
            throw e; // 重新抛出异常
        }
    }
}
