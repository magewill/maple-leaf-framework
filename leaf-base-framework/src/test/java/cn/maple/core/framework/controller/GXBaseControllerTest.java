package cn.maple.core.framework.controller;

import cn.maple.core.framework.dto.protocol.res.GXPaginationResProtocol;
import cn.maple.core.framework.dto.res.GXBaseResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXBaseControllerTest {
    private final TestController controller = new TestController();

    @Test
    void convertMethodsRejectNullTargetType() {
        assertThrows(IllegalArgumentException.class, () -> controller.convertSourceToTarget(new Object(), null));
        assertThrows(IllegalArgumentException.class, () -> controller.convertSourceListToTargetList(List.of(new Object()), null));
    }

    @Test
    void convertPaginationNullReturnsEmptyProtocol() {
        GXPaginationResProtocol<TestProtocol> protocol = controller.convertPaginationResToProtocol(null, TestProtocol.class);

        assertTrue(protocol.getRecords().isEmpty());
        assertEquals(0, protocol.getTotal());
        assertEquals(0, protocol.getPages());
    }

    @Test
    void convertPaginationRejectsNullTargetType() {
        GXPaginationResDto<TestResDto> pagination = new GXPaginationResDto<>(List.of(new TestResDto()), 1, 10, 1);

        assertThrows(IllegalArgumentException.class, () -> controller.convertPaginationResToProtocol(pagination, null));
    }

    @Test
    void userIdHelpersRejectBlankSecretKey() {
        assertThrows(GXBusinessException.class, () -> controller.getFrontEndUserId("", Long.class));
        assertThrows(GXBusinessException.class, () -> controller.getManagerUserId("", Long.class));
    }

    static class TestController implements GXBaseController {
    }

    static class TestResDto extends GXBaseResDto {
    }

    static class TestProtocol extends cn.maple.core.framework.dto.protocol.res.GXBaseResProtocol {
    }
}
