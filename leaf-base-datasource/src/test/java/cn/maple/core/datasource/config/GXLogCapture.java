package cn.maple.core.datasource.config;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试用日志捕获器：直接挂在目标 logger 上，避免依赖控制台重定向。
 */
final class GXLogCapture extends AbstractAppender {
    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private final Logger logger;
    private final Level previousLevel;

    private GXLogCapture(Logger logger) {
        super("gx-log-capture-" + System.nanoTime(), null, PatternLayout.createDefaultLayout(), false, null);
        this.logger = logger;
        this.previousLevel = logger.getLevel();
    }

    static GXLogCapture attach(Class<?> loggerType) {
        Logger logger = (Logger) LogManager.getLogger(loggerType);
        GXLogCapture capture = new GXLogCapture(logger);
        capture.start();
        logger.addAppender(capture);
        logger.setLevel(Level.DEBUG);
        return capture;
    }

    void detach() {
        logger.removeAppender(this);
        logger.setLevel(previousLevel);
        stop();
    }

    @Override
    public void append(LogEvent event) {
        events.add(event.toImmutable());
    }

    boolean hasEvent(Level level, String fragment) {
        return events.stream()
                .anyMatch(event -> event.getLevel().equals(level)
                        && event.getMessage().getFormattedMessage().contains(fragment));
    }

    long count(Level level) {
        return events.stream().filter(event -> event.getLevel().equals(level)).count();
    }
}
