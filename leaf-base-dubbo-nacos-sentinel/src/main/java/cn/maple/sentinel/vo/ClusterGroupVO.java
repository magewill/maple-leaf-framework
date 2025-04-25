package cn.maple.sentinel.vo;

import lombok.Data;
import lombok.ToString;

import java.util.Set;

/**
 * Sentinel集群分组值对象
 * <p>
 * 该类用于表示Sentinel集群中的服务器组配置信息，包含服务器标识、IP地址、端口以及关联的客户端集合。
 * 在Sentinel集群中，每个服务器组可以管理多个客户端，形成一个Token Server和多个Token Client的结构。
 * </p>
 * 
 * <p>
 * 在Sentinel集群限流中的应用场景：
 * <ul>
 *   <li>集群流控：作为集群流控规则的配置载体，定义Token Server和Token Client的关系</li>
 *   <li>动态配置：通过Nacos等配置中心动态调整集群限流配置</li>
 *   <li>服务发现：帮助Token Client自动发现并连接到正确的Token Server</li>
 *   <li>负载均衡：在多个Token Server场景下，合理分配客户端，避免单点压力</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个集群分组配置
 * ClusterGroupVO group = new ClusterGroupVO();
 * group.setMachineId("192.168.1.100@8720"); // 设置服务器标识（IP@CommandPort格式）
 * group.setIp("192.168.1.100");            // 设置服务器IP
 * group.setPort(8719);                     // 设置Token Server端口
 * 
 * // 添加客户端集合
 * Set<String> clients = new HashSet<>();
 * clients.add("192.168.1.101@8720");       // 添加客户端标识
 * clients.add("192.168.1.102@8720");
 * group.setClientSet(clients);
 * 
 * // 将配置发布到配置中心
 * String configJson = JSONUtil.toJsonStr(Collections.singletonList(group));
 * // 发布到配置中心的代码...
 * </pre>
 * </p>
 *
 * @author britton
 */
@Data
@ToString
public class ClusterGroupVO {
    /**
     * 机器ID
     * <p>
     * 格式为：IP@CommandPort，例如：192.168.1.100@8720
     * 其中CommandPort是暴露给Sentinel控制台的端口（transport模块）
     * 用于唯一标识集群中的一个节点
     * </p>
     */
    private String machineId;

    /**
     * IP地址
     * <p>
     * Token Server的IP地址，客户端将连接到此IP进行令牌请求
     * 在集群环境中，应确保此IP地址能被所有客户端访问到
     * </p>
     */
    private String ip;

    /**
     * 端口号
     * <p>
     * Token Server监听的端口，用于接收来自客户端的令牌请求
     * 此端口与CommandPort（用于控制台通信的端口）不同
     * 默认为8719，可根据实际情况配置
     * </p>
     */
    private Integer port;

    /**
     * 客户端集合
     * <p>
     * 当前Token Server管理的所有客户端标识集合
     * 每个客户端标识同样采用IP@CommandPort格式
     * 客户端将根据此配置自动连接到对应的Token Server
     * </p>
     */
    private Set<String> clientSet;
}