package cn.maple.core.framework.api;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.api.dto.res.GXBaseApiResDto;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.protocol.req.GXQueryParamReqProtocol;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GXBaseServeApiImplTest {
    private final TestApi api = new TestApi();

    @AfterEach
    void tearDown() {
        GXBaseServeApiImpl.STATIC_SERVE_SERVICE_CLASS_MAP.clear();
        GXBaseServeApiImpl.DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL.remove();
    }

    @Test
    void dynamicBindSurvivesInternalTableNameLookupAndIsClearedAfterCall() {
        StaticService staticService = new StaticService();
        DynamicService dynamicService = new DynamicService();
        api.staticBindServeServiceClass(StaticService.class);

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(StaticService.class)).thenReturn(staticService);
            springContext.when(() -> GXSpringContextUtils.getBean(DynamicService.class)).thenReturn(dynamicService);

            List<TestResDto> dynamicResult = api.callBindTargetServeSericeClass(DynamicService.class)
                    .findByCondition(null, TestResDto.class);
            List<TestResDto> staticResult = api.findByCondition(null, TestResDto.class);

            assertEquals("dynamic", dynamicResult.getFirst().getName());
            assertEquals("static", staticResult.getFirst().getName());
            assertEquals(StaticService.class, api.getServeServiceClass());
        }
    }

    @Test
    void paginateFillsDefaultTableNameAndConvertsRecords() {
        StaticService staticService = new StaticService();
        api.staticBindServeServiceClass(StaticService.class);

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(StaticService.class)).thenReturn(staticService);

            GXPaginationResDto<TestResDto> result = api.paginate(new GXQueryParamReqProtocol(), TestResDto.class);

            assertEquals("static_table", staticService.lastPaginateParam.getTableName());
            assertEquals(1, result.getRecords().size());
            assertEquals("page", result.getRecords().getFirst().getName());
        }
    }

    @Test
    void aggregateMethodsUseSafeDefaultsForNullOrConvertibleResults() {
        StaticService staticService = new StaticService();
        api.staticBindServeServiceClass(StaticService.class);

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(StaticService.class)).thenReturn(staticService);

            assertEquals(2, api.deleteCondition(null));
            assertEquals(3, api.deleteSoftCondition(null));
            assertEquals(4, api.updateFieldByCondition(Collections.emptyList(), null));
            assertEquals(true, api.checkRecordIsExists(null));
            assertEquals(5L, api.count(null));
            assertEquals("field", api.findSingleFieldByCondition(null, "name", String.class));
        }
    }

    @Test
    void callMethodCleansDynamicBindingAfterDirectInvocation() {
        StaticService staticService = new StaticService();
        DynamicService dynamicService = new DynamicService();
        api.staticBindServeServiceClass(StaticService.class);

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(StaticService.class)).thenReturn(staticService);
            springContext.when(() -> GXSpringContextUtils.getBean(DynamicService.class)).thenReturn(dynamicService);

            Object tableName = api.callBindTargetServeSericeClass(DynamicService.class).callMethod("getTableName");

            assertEquals("dynamic_table", tableName);
            assertEquals(StaticService.class, api.getServeServiceClass());
        }
    }

    @Test
    void callMethodReturnsNullWhenMethodIsMissing() {
        StaticService staticService = new StaticService();
        api.staticBindServeServiceClass(StaticService.class);

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(StaticService.class)).thenReturn(staticService);

            assertNull(api.callMethod("missingMethod"));
        }
    }

    static class TestApi extends GXBaseServeApiImpl<GXBusinessService> {
    }

    static class StaticService {
        private GXBaseQueryParamInnerDto lastPaginateParam;

        public String getTableName() {
            return "static_table";
        }

        public List<Dict> findByCondition(List<GXCondition<?>> condition, Object extraData) {
            return List.of(Dict.create().set("name", "static"));
        }

        public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto param) {
            lastPaginateParam = param;
            return new GXPaginationResDto<>(List.of(Dict.create().set("name", "page")), 1, 1, 10, 1);
        }

        public Integer deleteCondition(List<GXCondition<?>> condition) {
            return 2;
        }

        public String deleteSoftCondition(List<GXCondition<?>> condition) {
            return "3";
        }

        public Long updateFieldByCondition(List<?> updateFields, List<GXCondition<?>> condition) {
            return 4L;
        }

        public Boolean checkRecordIsExists(List<GXCondition<?>> condition) {
            return true;
        }

        public String countByCondition(List<GXCondition<?>> condition) {
            return "5";
        }

        public String findSingleFieldByCondition(List<GXCondition<?>> condition, String column, Class<?> targetClazz) {
            return "field";
        }

        public List<String> findMultiFieldByCondition(List<GXCondition<?>> condition, Set<String> columns, Class<?> targetClazz) {
            return List.of("a", "b");
        }
    }

    static class DynamicService extends StaticService {
        @Override
        public String getTableName() {
            return "dynamic_table";
        }

        @Override
        public List<Dict> findByCondition(List<GXCondition<?>> condition, Object extraData) {
            return List.of(Dict.create().set("name", "dynamic"));
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class TestResDto extends GXBaseApiResDto {
        private String name;
    }
}
