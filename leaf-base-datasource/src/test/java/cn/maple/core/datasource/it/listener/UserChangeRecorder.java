package cn.maple.core.datasource.it.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records synchronous listener callbacks for assertions.
 */
@Component
public class UserChangeRecorder implements GXMybatisListenerService<Dict> {
    public static final List<Dict> SAVE_EVENTS = new CopyOnWriteArrayList<>();
    public static final List<UpdateEvent> UPDATE_EVENTS = new CopyOnWriteArrayList<>();
    public static final List<Dict> DELETE_EVENTS = new CopyOnWriteArrayList<>();
    public static final List<Dict> DELETE_SOFT_EVENTS = new CopyOnWriteArrayList<>();

    public static void reset() {
        SAVE_EVENTS.clear();
        UPDATE_EVENTS.clear();
        DELETE_EVENTS.clear();
        DELETE_SOFT_EVENTS.clear();
    }

    @Override
    public void saveEntityListener(Dict data) {
        SAVE_EVENTS.add(data);
    }

    @Override
    public void updateEntityListener(Dict data, Dict keyValuePairs, Dict keyOperatorPairs, String rawWhereSql) {
        UPDATE_EVENTS.add(new UpdateEvent(data, keyValuePairs, keyOperatorPairs, rawWhereSql));
    }

    @Override
    public void deleteListener(Dict data) {
        DELETE_EVENTS.add(data);
    }

    @Override
    public void deleteSoftListener(Dict data) {
        DELETE_SOFT_EVENTS.add(data);
    }

    public record UpdateEvent(Dict data, Dict keyValuePairs, Dict keyOperatorPairs, String rawWhereSql) {
    }
}
