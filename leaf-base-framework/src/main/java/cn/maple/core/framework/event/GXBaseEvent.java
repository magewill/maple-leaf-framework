package cn.maple.core.framework.event;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

import java.lang.reflect.Type;

/**
 * 基础事件类
 * <p>
 * 该类是框架中所有事件的基类，继承自Spring的ApplicationEvent，并实现了ResolvableTypeProvider接口，
 * 用于支持泛型事件类型的解析。提供了事件类型、事件名称和附加参数的支持，使事件处理更加灵活。
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建自定义事件类
 * public class UserRegisteredEvent extends GXBaseEvent<User> {
 *     public UserRegisteredEvent(User user) {
 *         super(user, "REGISTER");
 *     }
 * }
 * 
 * // 2. 发布事件
 * User newUser = new User("username", "email@example.com");
 * applicationContext.publishEvent(new UserRegisteredEvent(newUser));
 * 
 * // 3. 使用附加参数
 * Dict params = Dict.create().set("ip", "192.168.1.1").set("timestamp", System.currentTimeMillis());
 * applicationContext.publishEvent(new GXBaseEvent<>(newUser, "REGISTER", params, "USER_REGISTERED"));
 * 
 * // 4. 监听事件
 * @EventListener
 * public void handleUserRegistered(UserRegisteredEvent event) {
 *     User user = event.getSource();
 *     String eventType = event.getEventType(); // "REGISTER"
 *     Dict params = event.getParam();
 *     // 处理用户注册逻辑
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 事件处理说明：
 * 1. 事件类型(eventType)：用于区分同一事件的不同场景，如"CREATE"、"UPDATE"、"DELETE"等
 * 2. 事件名称(eventName)：用于更细粒度地标识事件，如"USER_REGISTERED"、"ORDER_CREATED"等
 * 3. 附加参数(param)：用于传递事件相关的额外信息，如操作人、IP地址、时间戳等
 * </p>
 * 
 * @param <T> 事件源的类型参数，表示事件关联的数据类型
 * @author maple
 */
@Getter
public class GXBaseEvent<T> extends ApplicationEvent implements ResolvableTypeProvider {
    /**
     * 附加参数
     * <p>
     * 用于存储事件相关的额外信息，如操作人ID、IP地址、时间戳等。
     * 使用Dict类型提供灵活的键值对存储方式。
     * </p>
     */
    private final Dict param;

    /**
     * 事件名字
     * <p>
     * 用于标识事件的具体名称，便于在日志和监控中识别事件。
     * 例如："USER_REGISTERED"、"ORDER_CREATED"、"PAYMENT_COMPLETED"等。
     * </p>
     */
    private final String eventName;

    /**
     * 事件类型
     * <p>
     * 用于区分同一个事件的不同使用场景或操作类型。
     * 例如："CREATE"、"UPDATE"、"DELETE"、"QUERY"等。
     * </p>
     */
    private final String eventType;

    /**
     * 创建基础事件对象，使用默认的空事件类型、空事件名称和空参数
     *
     * @param source 事件源对象，不能为null
     * @throws IllegalArgumentException 如果source为null
     */
    public GXBaseEvent(T source) {
        this(source, "", Dict.create(), "");
    }

    /**
     * 创建基础事件对象，指定事件类型，使用默认的空事件名称和空参数
     *
     * @param source    事件源对象，不能为null
     * @param eventType 事件类型，用于区分同一事件的不同场景
     * @throws IllegalArgumentException 如果source为null
     */
    public GXBaseEvent(T source, String eventType) {
        this(source, eventType, Dict.create(), "");
    }

    /**
     * 创建基础事件对象，指定事件类型和事件名称，使用默认的空参数
     *
     * @param source    事件源对象，不能为null
     * @param eventType 事件类型，用于区分同一事件的不同场景
     * @param eventName 事件名称，用于标识具体的事件
     * @throws IllegalArgumentException 如果source为null
     */
    public GXBaseEvent(T source, String eventType, String eventName) {
        this(source, eventType, Dict.create(), eventName);
    }

    /**
     * 创建基础事件对象，指定事件类型和附加参数，使用默认的空事件名称
     *
     * @param source    事件源对象，不能为null
     * @param eventType 事件类型，用于区分同一事件的不同场景
     * @param param     附加参数，用于传递事件相关的额外信息
     * @throws IllegalArgumentException 如果source为null
     */
    public GXBaseEvent(T source, String eventType, Dict param) {
        this(source, eventType, param, "");
    }

    /**
     * 创建基础事件对象，指定所有属性
     *
     * @param source    事件源对象，不能为null
     * @param eventType 事件类型，用于区分同一事件的不同场景
     * @param param     附加参数，用于传递事件相关的额外信息
     * @param eventName 事件名称，用于标识具体的事件
     * @throws IllegalArgumentException 如果source为null
     */
    public GXBaseEvent(T source, String eventType, Dict param, String eventName) {
        super(source);
        this.param = param != null ? param : Dict.create();
        this.eventName = eventName;
        this.eventType = eventType;
    }

    /**
     * 获取事件名称
     * <p>
     * 该方法返回事件的名称，用于标识具体的事件。
     * 注意：此方法返回类型为Object，但实际上返回的是String类型，
     * 保留此方法是为了向后兼容。
     * </p>
     *
     * @return 事件名称
     */
    public Object getEventName() {
        return eventName;
    }

    /**
     * 获取事件的可解析类型
     * <p>
     * 该方法实现了ResolvableTypeProvider接口，用于支持泛型事件类型的解析。
     * Spring事件机制使用此方法确定事件的实际类型，以便正确路由到对应的监听器。
     * </p>
     *
     * @return 事件的可解析类型
     */
    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClass(getClass());
    }

    /**
     * 获取事件源对象
     * <p>
     * 重写父类的getSource方法，将事件源对象转换为泛型类型T。
     * 使用Hutool的Convert工具进行类型转换，确保返回正确的类型。
     * </p>
     *
     * @return 泛型类型的事件源对象
     */
    @Override
    public T getSource() {
        return Convert.convert((Type) source.getClass(), super.getSource());
    }
}
