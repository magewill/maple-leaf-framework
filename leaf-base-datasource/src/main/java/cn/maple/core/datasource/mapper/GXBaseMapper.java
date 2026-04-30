package cn.maple.core.datasource.mapper;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.builder.GXBaseBuilder;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.model.GXBaseModel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface GXBaseMapper<T extends GXBaseModel> extends BaseMapper<T> {
    @UpdateProvider(type = GXBaseBuilder.class, method = "updateFieldByCondition")
    Integer updateFieldByCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto, @Param("fieldList") List<GXUpdateField<?>> fieldList);

    @SelectProvider(type = GXBaseBuilder.class, method = "checkRecordIsExists")
    Boolean checkRecordIsExists(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    @SelectProvider(type = GXBaseBuilder.class, method = "findOneByCondition")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    Dict findOneByCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    @SelectProvider(type = GXBaseBuilder.class, method = "findByCondition")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    List<Dict> findByCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    @SelectProvider(type = GXBaseBuilder.class, method = "paginate")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    List<Dict> paginate(IPage<Dict> page, @Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    @UpdateProvider(type = GXBaseBuilder.class, method = "deleteSoftCondition")
    Integer deleteSoftCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto, @Param("updateFieldList") List<GXUpdateField<?>> updateFieldList);

    @DeleteProvider(type = GXBaseBuilder.class, method = "deleteCondition")
    Integer deleteCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    @SelectProvider(type = GXBaseBuilder.class, method = "unionFindOneByCondition")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    Dict unionFindOneByCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto, @Param("unionQueryParamInnerDtoLst") List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, @Param("unionTypeEnums") GXUnionTypeEnums unionTypeEnums);

    @SelectProvider(type = GXBaseBuilder.class, method = "unionFindByCondition")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    List<Dict> unionFindByCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto, @Param("unionQueryParamInnerDtoLst") List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, @Param("unionTypeEnums") GXUnionTypeEnums unionTypeEnums);

    @SelectProvider(type = GXBaseBuilder.class, method = "unionPaginate")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    List<Dict> unionPaginate(IPage<Dict> page, @Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto, @Param("unionQueryParamInnerDtoLst") List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, @Param("unionTypeEnums") GXUnionTypeEnums unionTypeEnums);
}
