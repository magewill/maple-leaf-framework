package cn.maple.core.datasource.config;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试用日志捕获器：直接挂在目标 logger 上，避免依赖控制台重定向。
 */
public final class GXLogCapture extends AbstractAppender {
    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private final Logger logger;
    private final Level previousLevel;

    private GXLogCapture(Logger logger) {
        super("gx-log-capture-" + System.nanoTime(), null, PatternLayout.createDefaultLayout(), false, null);
        this.logger = logger;
        this.previousLevel = logger.getLevel();
    }

    public static GXLogCapture attach(Class<?> loggerType) {
        return attach(loggerType.getName());
    }

    /**
     * Attaches by logger name so non-public types (for example package-private listener
     * interfaces) can be captured from other test packages.
     */
    public static GXLogCapture attach(String loggerName) {
        Logger logger = (Logger) LogManager.getLogger(loggerName);
        GXLogCapture capture = new GXLogCapture(logger);
        capture.start();
        // Logger.setLevel is a no-op when the logger shares the root LoggerConfig; Configurator
        // creates a dedicated LoggerConfig so DEBUG events actually reach the appender. Set the
        // level before attaching so a rebuilt LoggerConfig cannot drop the capture appender.
        Configurator.setLevel(loggerName, Level.DEBUG);
        logger.addAppender(capture);
        return capture;
    }

    public void detach() {
        logger.removeAppender(this);
        // A null level restores inheritance, so the capture never leaves a pinned level behind.
        Configurator.setLevel(logger.getName(), previousLevel);
        stop();
    }

    @Override
    public void append(LogEvent event) {
        events.add(event.toImmutable());
    }

    public boolean hasEvent(Level level, String fragment) {
        return events.stream()
                .anyMatch(event -> event.getLevel().equals(level)
                        && event.getMessage().getFormattedMessage().contains(fragment));
    }

    public long count(Level level) {
        return events.stream().filter(event -> event.getLevel().equals(level)).count();
    }
}
