package cn.maple.canal.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.service.GXCanalMessageParseService;
import cn.maple.canal.service.GXProcessCanalDataService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Canal消息解析服务实现类
 * <p>
 * 该类负责解析从Canal服务接收到的数据库变更消息，并将其分发到相应的处理服务。
 * 解析流程包括：
 * 1. 验证消息格式（确保是有效的JSON）
 * 2. 将消息转换为GXCanalDataDto对象
 * 3. 根据数据库名和表名动态查找对应的处理服务
 * 4. 根据操作类型（INSERT/UPDATE/DELETE）调用相应的处理方法
 * </p>
 * <p>
 * 服务命名约定：
 * 处理特定表的服务Bean名称应遵循 "数据库名_表名_Service" 的命名规则，
 * 例如处理user数据库中的account表的服务应命名为："user_account_Service"
 * </p>
 * <p>
 * 安全性考虑：
 * 1. 对输入消息进行严格验证，防止非法JSON导致解析异常
 * 2. 使用Optional和null检查避免空指针异常
 * 3. 对异常情况进行日志记录，便于问题排查
 * 4. 提供默认处理服务作为兜底，增强系统健壮性
 * </p>
 */
@Slf4j
@Service
public class GXCanalMessageParseServiceImpl implements GXCanalMessageParseService {
    /**
     * 解析RabbitMQ中canal的消息信息
     * <p>
     * 该方法接收从RabbitMQ队列中获取的原始Canal消息，进行解析和处理。
     * 解析过程包括：
     * 1. 验证消息是否为有效的JSON格式
     * 2. 将JSON转换为GXCanalDataDto对象
     * 3. 根据数据库名和表名动态查找对应的处理服务
     * 4. 根据操作类型分发到相应的处理方法
     * </p>
     *
     * @param message 从RabbitMQ接收的原始消息字符串，通常为JSON格式
     * @return Dict 处理结果，包含处理状态和相关业务数据
     */
    @Override
    public Dict parseMessage(String message) {
        String operator = "operator";
        Dict dict = Dict.create();
        
        // 1. 验证消息格式
        if (CharSequenceUtil.isBlank(message)) {
            log.error("接收到空消息，无法处理");
            return dict.set("status", "error").set("message", "消息内容为空");
        }
        
        if (!JSONUtil.isTypeJSON(message)) {
            log.error("消息格式错误，请传递JSON字符串: {}", CharSequenceUtil.maxLength(message, 100));
            return dict.set("status", "error").set("message", "消息格式不是有效的JSON");
        }
        
        // 2. 解析消息内容
        GXCanalDataDto canalDataDto;
        try {
            canalDataDto = JSONUtil.toBean(message, GXCanalDataDto.class);
        } catch (JSONException e) {
            log.error("JSON解析异常: {}", e.getMessage());
            return dict.set("status", "error").set("message", "JSON解析失败: " + e.getMessage());
        }
        
        // 验证关键字段
        if (CharSequenceUtil.isBlank(canalDataDto.getDatabase()) || CharSequenceUtil.isBlank(canalDataDto.getTable())) {
            log.error("数据库名或表名为空，无法确定处理服务");
            return dict.set("status", "error").set("message", "数据库名或表名为空");
        }
        
        // 3. 查找处理服务
        final String serviceName = CharSequenceUtil.toCamelCase(CharSequenceUtil.format("{}_{}_Service", 
                canalDataDto.getDatabase(), canalDataDto.getTable()));
        log.debug("尝试查找处理服务: {}", serviceName);
        
        Object bean = GXSpringContextUtils.getBean(serviceName);
        if (Objects.isNull(bean)) {
            log.debug("未找到指定服务[{}]，尝试使用默认处理服务", serviceName);
            bean = GXSpringContextUtils.getBean("defaultProcessCanalDataService");
            if (Objects.isNull(bean)) {
                log.warn("{}不存在，且默认处理服务也未配置，请提供实现了{}接口的类型", 
                        serviceName, GXProcessCanalDataService.class.getSimpleName());
                return dict.set("status", "error").set("message", "未找到合适的处理服务");
            }
        }
        
        if (!(bean instanceof GXProcessCanalDataService)) {
            log.error("{}必须是{}的子类", serviceName, GXProcessCanalDataService.class.getSimpleName());
            return dict.set("status", "error").set("message", "处理服务类型错误");
        }
        
        // 4. 根据操作类型分发处理
        GXProcessCanalDataService processCanalDataService = (GXProcessCanalDataService) bean;
        String type = Optional.ofNullable(canalDataDto.getType()).orElse("");
        
        try {
            switch (type) {
                case "UPDATE":
                    log.debug("处理UPDATE操作: 数据库[{}]表[{}]", canalDataDto.getDatabase(), canalDataDto.getTable());
                    dict = processCanalDataService.processUpdate(canalDataDto, Dict.create().set(operator, "update"));
                    break;
                case "INSERT":
                    log.debug("处理INSERT操作: 数据库[{}]表[{}]", canalDataDto.getDatabase(), canalDataDto.getTable());
                    dict = processCanalDataService.processInsert(canalDataDto, Dict.create().set(operator, "insert"));
                    break;
                case "DELETE":
                    log.debug("处理DELETE操作: 数据库[{}]表[{}]", canalDataDto.getDatabase(), canalDataDto.getTable());
                    dict = processCanalDataService.processDelete(canalDataDto, Dict.create().set(operator, "delete"));
                    break;
                default:
                    log.warn("未知的操作类型: {}", type);
                    dict.set("status", "error").set("message", "未知的操作类型: " + type);
                    break;
            }
            
            // 确保返回结果不为null
            if (dict == null) {
                log.warn("处理服务返回了null结果，已替换为空Dict");
                dict = Dict.create().set("status", "warning").set("message", "处理服务返回了null结果");
            }
            
            return dict;
        } catch (Exception e) {
            log.error("处理Canal消息时发生异常: {}", e.getMessage(), e);
            return Dict.create()
                    .set("status", "error")
                    .set("message", "处理异常: " + e.getMessage())
                    .set("type", type);
        }
    }
}
