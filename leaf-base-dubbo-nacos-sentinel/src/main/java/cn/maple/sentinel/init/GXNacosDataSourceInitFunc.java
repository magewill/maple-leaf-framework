package cn.maple.sentinel.init;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.sentinel.vo.ClusterGroupVO;
import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.transport.config.TransportConfig;
import com.alibaba.csp.sentinel.util.HostNameUtil;
import com.alibaba.nacos.api.PropertyKeyConst;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Sentinel Nacos数据源初始化功能类
 * <p>
 * 该类实现了Sentinel的InitFunc接口，用于从Nacos配置中心加载Sentinel的各种配置，
 * 包括流控规则、参数流控规则、集群客户端配置、集群服务端配置等。通过Nacos实现配置的动态更新和集群间的配置同步。
 * </p>
 * 
 * <p>
 * 主要功能：
 * <ul>
 *   <li>从Nacos加载流控规则和参数流控规则</li>
 *   <li>配置集群客户端（Token Client）相关参数</li>
 *   <li>配置集群服务端（Token Server）相关参数</li>
 *   <li>根据配置自动确定当前节点的集群角色（Token Server或Token Client）</li>
 *   <li>支持配置的动态更新和推送</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在项目的resources目录下创建配置文件：META-INF/services/com.alibaba.csp.sentinel.init.InitFunc
 * // 2. 在文件中添加本类的全限定名：cn.maple.sentinel.init.GXNacosDataSourceInitFunc
 * // 3. 在应用配置中设置必要的Nacos和应用参数
 * // application.yml示例：
 * spring:
 *   application:
 *     name: my-service  # 应用名称，用于生成配置ID
 * nacos:
 *   config:
 *     server-addr: 127.0.0.1:8848  # Nacos服务地址
 *     namespace: public            # 命名空间
 *     group: DEFAULT_GROUP         # 配置组
 * 
 * // 4. 在Nacos中创建对应的配置：
 * // - my-service-flow-rules：流控规则
 * // - my-service-param-rules：参数流控规则
 * // - my-service-cluster-client-config：客户端配置
 * // - my-service-cluster-map：集群映射关系
 * </pre>
 * </p>
 * 
 * <p>
 * 注意事项：
 * <ul>
 *   <li>确保Nacos服务可用且配置正确</li>
 *   <li>合理规划命名空间和配置组，避免配置冲突</li>
 *   <li>集群配置需要考虑网络环境，确保各节点之间可以正常通信</li>
 *   <li>配置变更时注意验证格式正确性，避免解析异常导致服务不可用</li>
 * </ul>
 * </p>
 *
 * @author britton
 */
public class GXNacosDataSourceInitFunc implements InitFunc {
    /**
     * 流控规则配置文件后缀
     */
    private static final String FLOW_POSTFIX = "-flow-rules";

    /**
     * 参数规则配置文件后缀
     */
    private static final String PARAM_FLOW_POSTFIX = "-param-rules";

    /**
     * 集群客户端规则配置文件后缀
     */
    private static final String CONFIG_POSTFIX = "-cluster-client-config";

    /**
     * 集群规则配置文件后缀
     */
    private static final String CLUSTER_MAP_POSTFIX = "-cluster-map";

    /**
     * 应用程序名字在配置文件中的KEY
     */
    private static final String APPLICATION_NAME_KEY = "spring.application.name";

    /**
     * 机器ID的分隔符
     */
    private static final String SEPARATOR = "@";

    /**
     * 初始化方法
     * <p>
     * 该方法在Sentinel启动时被调用，用于初始化所有数据源配置。
     * 初始化过程包括：
     * <ol>
     *   <li>初始化动态规则属性（流控规则和参数流控规则）</li>
     *   <li>初始化Token客户端相关配置</li>
     *   <li>初始化Token客户端服务器分配配置</li>
     *   <li>注册集群规则提供者</li>
     *   <li>初始化Token服务器传输配置</li>
     *   <li>初始化集群状态属性</li>
     * </ol>
     * </p>
     * 
     * @throws Exception 初始化过程中可能出现的异常
     */
    @Override
    public void init() throws Exception {
        try {
            // 注册客户端动态规则数据源
            initDynamicRuleProperty();

            // 注册Token客户端相关数据源
            // Token客户端通用配置:
            initClientConfigProperty();
            // Token客户端分配配置（例如目标Token服务器）从分配映射中获取:
            initClientServerAssignProperty();

            // 注册Token服务器相关数据源
            // 为Token服务器注册动态规则数据源提供者:
            registerClusterRuleSupplier();
            // 从分配映射中提取Token服务器传输配置:
            initServerTransportConfigProperty();

            // 初始化集群状态属性，用于从集群映射数据源中提取模式
            initStateProperty();
        } catch (Exception e) {
            // 记录初始化失败的详细信息，便于问题排查
            System.err.println("Sentinel Nacos数据源初始化失败: " + e.getMessage());
            e.printStackTrace();
            // 重新抛出异常，让Sentinel框架感知初始化失败
            throw e;
        }
    }

    /**
     * 初始化动态规则属性
     * <p>
     * 从Nacos配置中心加载流控规则和参数流控规则，并注册到Sentinel的规则管理器中。
     * 这些规则将用于控制资源的访问频率和参数限制。
     * </p>
     * <p>
     * 流控规则格式示例：
     * <pre>
     * [
     *   {
     *     "resource": "资源名",
     *     "count": 10,
     *     "grade": 1,
     *     "limitApp": "default",
     *     "strategy": 0,
     *     "controlBehavior": 0
     *   }
     * ]
     * </pre>
     * </p>
     * 
     * @throws RuntimeException 如果Nacos配置无法访问或配置格式错误
     */
    private void initDynamicRuleProperty() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, getNacosServerAddress());
        properties.put(PropertyKeyConst.NAMESPACE, getNamespace());
        
        try {
            // 初始化流控规则数据源
            ReadableDataSource<String, List<FlowRule>> ruleSource = new NacosDataSource<>(properties,
                    getNacosGroupId(),
                    getFlowDataId(),
                    source -> JSONUtil.toBean(source, new TypeReference<>() {
                    }, true)
            );
            FlowRuleManager.register2Property(ruleSource.getProperty());
            
            // 初始化参数流控规则数据源
            ReadableDataSource<String, List<ParamFlowRule>> paramRuleSource = new NacosDataSource<>(properties,
                    getNacosGroupId(),
                    getParamDataId(),
                    source -> JSONUtil.toBean(source, new TypeReference<>() {
                    }, true)
            );
            ParamFlowRuleManager.register2Property(paramRuleSource.getProperty());
        } catch (Exception e) {
            String errorMsg = "初始化动态规则属性失败: " + e.getMessage();
            System.err.println(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * 初始化客户端配置
     *
     * @author britton
     */
    private void initClientConfigProperty() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, getNacosServerAddress());
        properties.put(PropertyKeyConst.NAMESPACE, getNamespace());
        ReadableDataSource<String, ClusterClientConfig> clientConfigDs = new NacosDataSource<>(properties,
                getNacosGroupId(),
                getConfigDataId(),
                source -> JSONUtil.toBean(source, new TypeReference<>() {
                }, true)
        );
        ClusterClientConfigManager.registerClientConfigProperty(clientConfigDs.getProperty());
    }

    /**
     * 初始化服务器传输配置
     *
     * @author britton
     */
    private void initServerTransportConfigProperty() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, getNacosServerAddress());
        properties.put(PropertyKeyConst.NAMESPACE, getNamespace());
        ReadableDataSource<String, ServerTransportConfig> serverTransportDs = new NacosDataSource<>(properties,
                getNacosGroupId(),
                getClusterMapDataId(),
                source -> {
                    List<ClusterGroupVO> groupList = JSONUtil.toBean(source, new TypeReference<>() {
                    }, true);
                    return Optional.ofNullable(groupList)
                            .flatMap(this::extractServerTransportConfig)
                            .orElse(null);
                }
        );
        ClusterServerConfigManager.registerServerTransportProperty(serverTransportDs.getProperty());
    }

    /**
     * 注册集群规则提供者
     * Register cluster flow rule property supplier which creates data source by namespace.
     * Flow rule dataId format: ${namespace}-flow-rules
     *
     * @author britton
     */
    private void registerClusterRuleSupplier() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, getNacosServerAddress());
        properties.put(PropertyKeyConst.NAMESPACE, getNamespace());
        ClusterFlowRuleManager.setPropertySupplier(namespace -> {
            ReadableDataSource<String, List<FlowRule>> ds = new NacosDataSource<>(properties,
                    getNacosGroupId(),
                    namespace + FLOW_POSTFIX,
                    source -> JSONUtil.toBean(source, new TypeReference<>() {
                    }, true)
            );
            return ds.getProperty();
        });
        // Register cluster parameter flow rule property supplier which creates data source by namespace.
        ClusterParamFlowRuleManager.setPropertySupplier(namespace -> {
            ReadableDataSource<String, List<ParamFlowRule>> ds = new NacosDataSource<>(properties,
                    getNacosGroupId(),
                    namespace + PARAM_FLOW_POSTFIX,
                    source -> JSONUtil.toBean(source, new TypeReference<>() {
                    }, true)
            );
            return ds.getProperty();
        });
    }

    /**
     * 初始化客户端服务分配属性
     * Cluster map format:
     * [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
     * machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard (transport module)
     *
     * @author britton
     */
    private void initClientServerAssignProperty() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, getNacosServerAddress());
        properties.put(PropertyKeyConst.NAMESPACE, getNamespace());
        ReadableDataSource<String, ClusterClientAssignConfig> clientAssignDs = new NacosDataSource<>(properties,
                getNacosGroupId(),
                getClusterMapDataId(),
                source -> {
                    List<ClusterGroupVO> groupList = JSONUtil.toBean(source, new TypeReference<>() {
                    }, true);
                    return Optional.ofNullable(groupList)
                            .flatMap(this::extractClientAssignment)
                            .orElse(null);
                }
        );
        ClusterClientConfigManager.registerServerAssignProperty(clientAssignDs.getProperty());
    }

    /**
     * 初始化状态属性
     * Cluster map format:
     * [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
     * machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard (transport module)
     *
     * @author britton
     */
    private void initStateProperty() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, getNacosServerAddress());
        properties.put(PropertyKeyConst.NAMESPACE, getNamespace());
        ReadableDataSource<String, Integer> clusterModeDs = new NacosDataSource<>(properties,
                getNacosGroupId(),
                getClusterMapDataId(),
                source -> {
                    List<ClusterGroupVO> groupList = JSONUtil.toBean(source, new TypeReference<>() {
                    }, true);
                    return Optional.ofNullable(groupList)
                            .map(this::extractMode)
                            .orElse(ClusterStateManager.CLUSTER_NOT_STARTED);
                }
        );
        ClusterStateManager.registerProperty(clusterModeDs.getProperty());
    }

    /**
     * 抽取集群模式
     * <p>
     * 根据集群分组配置，确定当前节点在集群中的角色：
     * <ul>
     *   <li>如果当前节点的machineId与任何服务器组的machineId匹配，则为Token Server</li>
     *   <li>如果当前节点的machineId在任何服务器组的客户端集合中，则为Token Client</li>
     *   <li>否则为未分配状态，不参与集群限流</li>
     * </ul>
     * </p>
     * <p>
     * 集群角色决定了节点的行为模式：
     * <ul>
     *   <li>Token Server: 负责为集群中的客户端分配令牌，维护集群级别的统计信息</li>
     *   <li>Token Client: 向指定的Token Server请求令牌，执行分布式限流</li>
     *   <li>未分配: 使用本地限流模式，不参与集群限流</li>
     * </ul>
     * </p>
     *
     * @param groupList 集群分组配置列表
     * @return 集群模式代码，对应ClusterStateManager中的常量
     */
    private int extractMode(List<ClusterGroupVO> groupList) {
        // 如果任何服务器组的machineId与当前匹配，则为Token Server
        if (groupList.stream().anyMatch(this::machineEqual)) {
            return ClusterStateManager.CLUSTER_SERVER;
        }
        // 如果当前机器属于任何Token服务器组的客户端集合，则为Token Client
        // 否则为未分配，应设置为NOT_STARTED
        boolean canBeClient = groupList.stream()
                .flatMap(e -> e.getClientSet().stream())
                .filter(Objects::nonNull)
                .anyMatch(e -> e.equals(getCurrentMachineId()));
        return canBeClient ? ClusterStateManager.CLUSTER_CLIENT : ClusterStateManager.CLUSTER_NOT_STARTED;
    }

    /**
     * 抽取服务端传输配置
     * <p>
     * 根据集群分组配置，提取当前节点（作为Token Server）的传输配置。
     * 该方法的逻辑是：
     * <ol>
     *   <li>在集群分组列表中查找machineId与当前节点匹配的分组</li>
     *   <li>如果找到匹配的分组，则创建一个ServerTransportConfig，设置端口和空闲超时时间</li>
     *   <li>如果没有找到匹配的分组，则返回空</li>
     * </ol>
     * </p>
     * <p>
     * 传输配置包含Token Server监听的端口和连接空闲超时时间，用于处理来自Token Client的请求。
     * 空闲超时时间设置为600秒，可以根据实际需求调整。
     * </p>
     *
     * @param groupList 集群分组配置列表
     * @return 服务端传输配置的Optional包装，如果当前节点不是Token Server，则为空
     */
    private Optional<ServerTransportConfig> extractServerTransportConfig(List<ClusterGroupVO> groupList) {
        return groupList.stream()
                .filter(this::machineEqual)
                .findAny()
                .map(e -> new ServerTransportConfig()
                        .setPort(e.getPort())  // 设置Token Server监听端口
                        .setIdleSeconds(600));  // 设置连接空闲超时时间为600秒
    }

    /**
     * 抽取客户端分配配置
     * <p>
     * 根据集群分组配置，确定当前节点（作为Token Client）应该连接的Token Server信息。
     * 该方法的逻辑是：
     * <ol>
     *   <li>如果当前节点是Token Server，则不需要分配配置，返回空</li>
     *   <li>如果当前节点在某个Token Server的客户端集合中，则返回该Token Server的连接信息</li>
     *   <li>如果当前节点不在任何Token Server的客户端集合中，则返回空</li>
     * </ol>
     * </p>
     * <p>
     * 返回的配置包含Token Server的IP地址和端口，Token Client将使用这些信息建立连接。
     * </p>
     *
     * @param groupList 集群分组配置列表
     * @return 客户端分配配置的Optional包装，如果当前节点是Token Server或未分配，则为空
     */
    private Optional<ClusterClientAssignConfig> extractClientAssignment(List<ClusterGroupVO> groupList) {
        // 如果当前节点是Token Server，则不需要分配配置
        if (groupList.stream().anyMatch(this::machineEqual)) {
            return Optional.empty();
        }
        // 从目标服务器组的客户端集合中构建客户端分配配置
        for (ClusterGroupVO group : groupList) {
            if (group.getClientSet().contains(getCurrentMachineId())) {
                String ip = group.getIp();
                Integer port = group.getPort();
                return Optional.of(new ClusterClientAssignConfig(ip, port));
            }
        }
        return Optional.empty();
    }

    /**
     * 判断机器是否相等
     * <p>
     * 比较当前节点的machineId与给定集群分组的machineId是否相等。
     * 这用于确定当前节点是否是某个Token Server。
     * </p>
     * <p>
     * machineId的格式为：IP@CommandPort，例如：192.168.1.100@8720
     * </p>
     *
     * @param group 集群分组对象
     * @return 如果当前节点的machineId与给定分组的machineId相等，则返回true；否则返回false
     */
    private boolean machineEqual(/*@Valid*/ ClusterGroupVO group) {
        return getCurrentMachineId().equals(group.getMachineId());
    }

    /**
     * 获取当前的机器ID
     * <p>
     * 生成当前节点的machineId，格式为：IP@CommandPort。
     * 其中IP是当前节点的IP地址，CommandPort是Sentinel控制台通信端口。
     * </p>
     * <p>
     * 注意：这种方式在容器化环境中可能不适用，因为容器内的IP可能与实际网络环境中的IP不同。
     * 在容器化环境中，可能需要通过环境变量或配置文件指定正确的IP地址。
     * </p>
     *
     * @return 当前节点的机器ID
     */
    private String getCurrentMachineId() {
        return HostNameUtil.getIp() + SEPARATOR + TransportConfig.getRuntimePort();
    }

    /**
     * 获取流控的nacos配置信息
     *
     * @return String
     */
    private String getFlowDataId() {
        return GXCommonUtils.getEnvironmentValue(APPLICATION_NAME_KEY, String.class) + FLOW_POSTFIX;
    }

    /**
     * 获取参数的nacos配置信息
     *
     * @return String
     */
    private String getParamDataId() {
        return GXCommonUtils.getEnvironmentValue(APPLICATION_NAME_KEY, String.class) + PARAM_FLOW_POSTFIX;
    }

    /**
     * 获取config的nacos配置信息
     *
     * @return String
     */
    private String getConfigDataId() {
        return GXCommonUtils.getEnvironmentValue(APPLICATION_NAME_KEY, String.class) + CONFIG_POSTFIX;
    }

    /**
     * 获取ClusterMap的nacos配置信息
     * {@code
     * Cluster map format:
     * [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
     * machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard (transport module)
     * }
     *
     * @return String
     */
    private String getClusterMapDataId() {
        return GXCommonUtils.getEnvironmentValue(APPLICATION_NAME_KEY, String.class) + CLUSTER_MAP_POSTFIX;
    }

    /**
     * 从当前项目的配置文件中获取nacos的主机地址
     *
     * @return String
     * @author britton
     */
    private String getNacosServerAddress() {
        return GXCommonUtils.getEnvironmentValue("nacos.config.server-addr", String.class);
    }

    /**
     * 从当前项目的配置文件中获取nacos的group配置
     *
     * @return String
     * @author britton
     */
    private String getNacosGroupId() {
        return GXCommonUtils.getEnvironmentValue("nacos.config.group", String.class, "DEFAULT_GROUP");
    }

    /**
     * 从当前项目的配置文件中获取nacos的命名空间
     *
     * @return String
     * @author britton
     */
    private String getNamespace() {
        return GXCommonUtils.getEnvironmentValue("nacos.config.namespace", String.class);
    }
}
