package cn.maple.extension;

/**
 * BizScenario（业务场景）= bizId + useCase + scenario,
 * which can uniquely identify a user scenario.
 * <p>
 * 业务场景由三部分组成：
 * 1. bizId - 业务标识，用于区分不同的业务领域，如订单、用户、商品等
 * 2. useCase - 用例标识，用于区分同一业务领域下的不同用例，如下单、支付、退款等
 * 3. scenario - 场景标识，用于区分同一用例下的不同场景，如普通下单、促销下单、秒杀下单等
 * <p>
 * 该类是扩展点框架的核心类之一，用于唯一标识一个业务场景，从而找到对应的扩展点实现。
 * 该类是不可变的，所有字段在创建后不可修改，因此是线程安全的。
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个完整的业务场景
 * GXBizScenario scenario1 = GXBizScenario.valueOf("order", "place", "normal");
 * 
 * // 创建一个只有业务ID和用例的业务场景，场景使用默认值
 * GXBizScenario scenario2 = GXBizScenario.valueOf("order", "place");
 * 
 * // 创建一个只有业务ID的业务场景，用例和场景使用默认值
 * GXBizScenario scenario3 = GXBizScenario.valueOf("order");
 * 
 * // 创建一个默认的业务场景，业务ID、用例和场景都使用默认值
 * GXBizScenario scenario4 = GXBizScenario.newDefault();
 * 
 * // 获取业务场景的唯一标识
 * String identity = scenario1.getUniqueIdentity(); // 返回 "order.place.normal"
 * </pre>
 *
 * @author britton
 */
public class GXBizScenario {
    /**
     * 默认业务ID
     * <p>
     * 当没有指定业务ID时使用此默认值
     */
    public static final String DEFAULT_BIZ_ID = "#defaultBizId#";

    /**
     * 默认用例
     * <p>
     * 当没有指定用例时使用此默认值
     */
    public static final String DEFAULT_USE_CASE = "#defaultUseCase#";

    /**
     * 默认场景
     * <p>
     * 当没有指定场景时使用此默认值
     */
    public static final String DEFAULT_SCENARIO = "#defaultScenario#";

    /**
     * 分隔符
     * <p>
     * 用于连接业务ID、用例和场景，形成唯一标识
     */
    private static final String DOT_SEPARATOR = ".";

    /**
     * 业务ID，用于标识一个业务领域
     * <p>
     * 例如：订单(order)、用户(user)、商品(product)等
     * 如果系统中只有一个业务，可以使用默认值
     */
    private String bizId = DEFAULT_BIZ_ID;

    /**
     * 用例，用于标识同一业务领域下的不同用例
     * <p>
     * 例如：下单(placeOrder)、支付(payment)、退款(refund)等
     * 不能为空，如果未指定则使用默认值
     */
    private String useCase = DEFAULT_USE_CASE;

    /**
     * 场景，用于标识同一用例下的不同场景
     * <p>
     * 例如：普通下单(normal)、促销下单(promotion)、秒杀下单(seckill)等
     * 不能为空，如果未指定则使用默认值
     */
    private String scenario = DEFAULT_SCENARIO;

    /**
     * 创建业务场景对象
     * <p>
     * 该方法用于创建一个完整的业务场景对象，包含业务ID、用例和场景三个维度
     * <p>
     * 线程安全性：该方法创建并返回一个新的对象，不修改任何共享状态，因此是线程安全的
     *
     * @param bizId    业务ID，用于标识业务领域
     * @param useCase  用例，用于标识同一业务领域下的不同用例
     * @param scenario 场景，用于标识同一用例下的不同场景
     * @return 业务场景对象
     */
    public static GXBizScenario valueOf(String bizId, String useCase, String scenario) {
        GXBizScenario bizScenario = new GXBizScenario();
        bizScenario.bizId = bizId;
        bizScenario.useCase = useCase;
        bizScenario.scenario = scenario;
        return bizScenario;
    }

    /**
     * 创建业务场景对象（使用默认场景）
     * <p>
     * 该方法用于创建一个包含业务ID和用例的业务场景对象，场景使用默认值
     * <p>
     * 线程安全性：该方法调用线程安全的valueOf方法，因此也是线程安全的
     *
     * @param bizId   业务ID，用于标识业务领域
     * @param useCase 用例，用于标识同一业务领域下的不同用例
     * @return 业务场景对象
     */
    public static GXBizScenario valueOf(String bizId, String useCase) {
        return GXBizScenario.valueOf(bizId, useCase, DEFAULT_SCENARIO);
    }

    /**
     * 创建业务场景对象（使用默认用例和默认场景）
     * <p>
     * 该方法用于创建一个只包含业务ID的业务场景对象，用例和场景使用默认值
     * <p>
     * 线程安全性：该方法调用线程安全的valueOf方法，因此也是线程安全的
     *
     * @param bizId 业务ID，用于标识业务领域
     * @return 业务场景对象
     */
    public static GXBizScenario valueOf(String bizId) {
        return GXBizScenario.valueOf(bizId, DEFAULT_USE_CASE, DEFAULT_SCENARIO);
    }

    /**
     * 创建默认业务场景对象
     * <p>
     * 该方法用于创建一个使用默认值的业务场景对象，业务ID、用例和场景都使用默认值
     * <p>
     * 线程安全性：该方法调用线程安全的valueOf方法，因此也是线程安全的
     *
     * @return 默认业务场景对象
     */
    public static GXBizScenario newDefault() {
        return GXBizScenario.valueOf(DEFAULT_BIZ_ID, DEFAULT_USE_CASE, DEFAULT_SCENARIO);
    }

    /**
     * 获取业务场景的唯一标识
     * <p>
     * 该方法返回业务场景的完整唯一标识，格式为：bizId.useCase.scenario
     * 例如：order.payment.alipay
     * <p>
     * 线程安全性：该方法不修改对象状态，只读取字段值，因此是线程安全的
     *
     * @return 业务场景的唯一标识
     */
    public String getUniqueIdentity() {
        return bizId + DOT_SEPARATOR + useCase + DOT_SEPARATOR + scenario;
    }

    /**
     * 获取使用默认场景的业务场景标识
     * <p>
     * 该方法返回使用默认场景的业务场景标识，格式为：bizId.useCase.#defaultScenario#
     * 例如：order.payment.#defaultScenario#
     * <p>
     * 线程安全性：该方法不修改对象状态，只读取字段值，因此是线程安全的
     *
     * @return 使用默认场景的业务场景标识
     */
    public String getIdentityWithDefaultScenario() {
        return bizId + DOT_SEPARATOR + useCase + DOT_SEPARATOR + DEFAULT_SCENARIO;
    }

    /**
     * 获取使用默认用例和默认场景的业务场景标识
     * <p>
     * 该方法返回使用默认用例和默认场景的业务场景标识，格式为：bizId.#defaultUseCase#.#defaultScenario#
     * 例如：order.#defaultUseCase#.#defaultScenario#
     * <p>
     * 线程安全性：该方法不修改对象状态，只读取字段值，因此是线程安全的
     *
     * @return 使用默认用例和默认场景的业务场景标识
     */
    public String getIdentityWithDefaultUseCase() {
        return bizId + DOT_SEPARATOR + DEFAULT_USE_CASE + DOT_SEPARATOR + DEFAULT_SCENARIO;
    }
}
