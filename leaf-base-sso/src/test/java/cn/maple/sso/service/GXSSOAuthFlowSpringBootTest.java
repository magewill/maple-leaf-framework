package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.metrics.GXSSOMetrics;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.support.GXSSOTestApplication;
import cn.maple.sso.support.GXSSOTestDoubles;
import cn.maple.sso.utils.GXSSOHelperUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end login flow over the real Spring MVC interceptor chain.
 */
@SpringBootTest(classes = GXSSOTestApplication.class,
        properties = {
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.import-check.enabled=false",
                "spring.main.banner-mode=off",
                "logging.level.root=WARN"
        })
@Import(GXSSOAuthFlowSpringBootTest.DemoController.class)
class GXSSOAuthFlowSpringBootTest {
    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private GXSSOService ssoService;

    private MockMvc mockMvc;

    private GXSSOTestDoubles.CountingSsoCache cache;

    private GXSSOTestDoubles.RecordingPlugin plugin;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        GXSSOMetrics.reset();
        cache = new GXSSOTestDoubles.CountingSsoCache();
        plugin = new GXSSOTestDoubles.RecordingPlugin();
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties()
                .setCache(cache)
                .setPluginList(List.of(plugin))
                .setLoginUrl("")
                .setCacheExpires(7200));
    }

    @AfterEach
    void tearDown() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
        GXSSOMetrics.reset();
    }

    @Test
    void anonymousRequestHasNoSideEffect() throws Exception {
        mockMvc.perform(get("/secure/me").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());

        assertEquals(0, plugin.logoutCount(), "anonymous request must not run plugin logout");
        assertEquals(0, cache.deleteCount(), "anonymous request must not delete cache entries");
    }

    @Test
    void anonymousPageRequestHasNoSideEffect() throws Exception {
        mockMvc.perform(get("/secure/me"))
                .andExpect(status().isUnauthorized());

        assertEquals(0, plugin.logoutCount(), "a page request without a token must not run plugin logout");
        assertEquals(0, cache.deleteCount(), "a page request without a token must not touch the cache");
    }

    @Test
    void rejectedParseableTokenPageRequestHasNoSideEffect() throws Exception {
        mockMvc.perform(get("/secure/me").header("token", craftedToken(99L)))
                .andExpect(status().isUnauthorized());

        assertEquals(0, cache.deleteCount(), "a rejected token must not delete the user's cache entry");
        assertEquals(0, plugin.logoutCount(), "a rejected token must not trigger plugin logout");
    }

    @Test
    void rejectedRequestLogsNoInfo() throws Exception {
        LogCapture supportCapture = LogCapture.attach(GXSSOSupportService.class);
        LogCapture serviceCapture = LogCapture.attach(GXAbstractSSOService.class);
        try {
            mockMvc.perform(get("/secure/me").header("token", craftedToken(99L)))
                    .andExpect(status().isUnauthorized());
        } finally {
            supportCapture.close();
            serviceCapture.close();
        }

        assertEquals(List.of(), supportCapture.levelsAtLeastInfo(), "the rejection path must stay at DEBUG");
        assertEquals(List.of(), serviceCapture.levelsAtLeastInfo(), "the rejection path must stay at DEBUG");
    }

    @Test
    void loginThenValidateThenKickThenLogout() throws Exception {
        String firstToken = login(9L, 1000L);

        mockMvc.perform(get("/secure/me").header("token", firstToken))
                .andExpect(status().isOk());

        String secondToken = login(9L, 2000L);
        mockMvc.perform(get("/secure/me").header("token", secondToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/secure/me").header("token", firstToken).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());

        MockHttpServletRequest logoutRequest = new MockHttpServletRequest();
        logoutRequest.addHeader("token", secondToken);
        MockHttpServletResponse logoutResponse = new MockHttpServletResponse();
        assertTrue(ssoService.clearLogin(logoutRequest, logoutResponse), "logout must be idempotent");

        mockMvc.perform(get("/secure/me").header("token", secondToken).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceInterfaceExposesLogoutAndAttrToken() throws Exception {
        GXSSOService service = ssoService;

        Dict token = Dict.create()
                .set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 13L)
                .set(GXTokenConstant.LOGIN_AT_FIELD_NAME, 4000L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, token);

        assertEquals(13L, service.attrSSOToken(request).getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME),
                "attrSSOToken must be callable through the GXSSOService interface without a downcast");

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.logout(request, response);

        assertEquals(1, plugin.logoutCount(), "logout must be callable through the GXSSOService interface");
        assertTrue(cache.deleteCount() >= 1, "logout must delete the server side entry");
    }

    private String craftedToken(Long userId) {
        Dict payload = Dict.create()
                .set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, userId)
                .set(GXTokenConstant.LOGIN_AT_FIELD_NAME, 1700000000L);
        return GXAuthCodeUtils.authCodeEncode(JSONUtil.toJsonStr(payload),
                GXSSOTestApplication.TOKEN_CONFIG.getTokenSecret(), 7200);
    }

    private String login(Long userId, Long loginAt) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ssoService.setCookie(new MockHttpServletRequest(), response,
                Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, userId)
                        .set(GXTokenConstant.LOGIN_AT_FIELD_NAME, loginAt));

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie != null && setCookie.startsWith("uid="), "login must write the token cookie");
        int end = setCookie.indexOf(';');
        return setCookie.substring("uid=".length(), end < 0 ? setCookie.length() : end);
    }

    /**
     * Captures log events of one logger and restores its level on close.
     */
    private static final class LogCapture implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger logger;

        private final Level previousLevel;

        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        private final AbstractAppender appender;

        private LogCapture(org.apache.logging.log4j.core.Logger logger) {
            this.logger = logger;
            this.previousLevel = logger.getLevel();
            this.appender = new AbstractAppender("capture-" + logger.getName(), null,
                    PatternLayout.createDefaultLayout(), false, null) {
                @Override
                public void append(LogEvent event) {
                    events.add(event.toImmutable());
                }
            };
            this.appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);
        }

        private static LogCapture attach(Class<?> type) {
            return new LogCapture((org.apache.logging.log4j.core.Logger) LogManager.getLogger(type));
        }

        private List<String> levelsAtLeastInfo() {
            return events.stream()
                    .filter(event -> event.getLevel().intLevel() <= Level.INFO.intLevel())
                    .map(event -> event.getLevel().name() + " " + event.getMessage().getFormattedMessage())
                    .toList();
        }

        @Override
        public void close() {
            logger.removeAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @RestController
    static class DemoController {
        @GetMapping("/secure/me")
        public String me() {
            return "ok";
        }
    }
}
