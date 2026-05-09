package cn.maple.core.framework.web.advice;

import cn.maple.core.framework.dto.protocol.req.GXBaseReqProtocol;
import cn.maple.core.framework.util.GXResultUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = GXWebAdviceMvcTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
class GXWebAdviceMvcTest {
    private final MockMvc mockMvc;

    @Autowired
    GXWebAdviceMvcTest(WebApplicationContext webApplicationContext) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void requestBodyAdviceRunsInMvcRequestFlow() throws Exception {
        mockMvc.perform(post("/web-advice/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"maple\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            GXRequestBodyAdvice.class,
            GXResponseBodyAdvice.class,
            TestController.class
    })
    static class TestApplication {
    }

    @RestController
    static class TestController {
        @PostMapping("/web-advice/verify")
        GXResultUtils<Boolean> verify(@RequestBody TestRequest body) {
            return GXResultUtils.ok(body.verified);
        }
    }

    static class TestRequest extends GXBaseReqProtocol {
        public String name;
        public boolean verified;

        protected void verify() {
            verified = true;
        }
    }
}
