package cn.maple.core.datasource.it.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Synchronous listener that always fails, used to verify the propagate-exception semantics on the
 * real delivery path.
 */
@Component
public class ThrowingUserListener implements GXMybatisListenerService<Dict> {
    public static final AtomicInteger CALLS = new AtomicInteger();

    public static void reset() {
        CALLS.set(0);
    }

    @Override
    public void saveEntityListener(Dict data) {
        CALLS.incrementAndGet();
        throw new IllegalStateException("throwing listener boom");
    }
}
