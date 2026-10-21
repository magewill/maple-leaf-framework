package cn.maple.core.datasource.it.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Records asynchronous (after-commit) listener callbacks for assertions.
 */
@Component
public class AsyncUserChangeRecorder implements GXMybatisListenerService<Dict> {
    public static final List<Dict> SAVE_EVENTS = new CopyOnWriteArrayList<>();
    public static final AtomicReference<String> EXECUTING_THREAD = new AtomicReference<>();

    public static void reset() {
        SAVE_EVENTS.clear();
        EXECUTING_THREAD.set(null);
    }

    @Override
    public void saveEntityListener(Dict data) {
        EXECUTING_THREAD.set(Thread.currentThread().getName());
        SAVE_EVENTS.add(data);
    }
}
