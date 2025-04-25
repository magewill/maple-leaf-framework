package cn.maple.core.framework.code;

import lombok.Getter;

/**
 * 业务结果状态码枚举类
 * <p>
 * 该枚举类定义了系统中常用的业务状态码，包括正常状态、删除状态、锁定状态等。
 * 每个状态码都包含了代码值、提示信息和描述信息。
 * 实现了GXResultStatusCode接口，可以在业务逻辑中统一处理状态码。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在业务逻辑中使用状态码
 * GXResultStatusCode statusCode = GXBusinessResultStatusCode.NORMAL;
 * int code = statusCode.getCode(); // 获取状态码值
 * String message = statusCode.getMsg(); // 获取提示信息
 * 
 * // 2. 在条件判断中使用
 * if (entity.getStatus() == GXBusinessResultStatusCode.DELETED.getCode()) {
 *     // 处理已删除的情况
 * }
 * 
 * // 3. 在异常处理中使用
 * throw new GXBusinessException(GXBusinessResultStatusCode.LOCKED);
 * </pre>
 *
 * @author maple-leaf-framework
 */
@Getter
public enum GXBusinessResultStatusCode implements GXResultStatusCode {
    /** 正常状态 - 通用状态 */
    NORMAL(0x0, "正常状态", "公用状态"),
    
    /** 删除状态 - 通用状态 */
    DELETED(0x10, "删除", "公用状态"),
    
    /** 账号锁定状态 */
    LOCKED(0x11, "账号锁定", "账号锁定"),
    
    /** 账号过期状态 */
    ACCOUNT_OVERDUE(0x12, "账号过期", "账号过期"),
    
    /** 密码过期状态 */
    PASSWORD_OVERDUE(0x13, "密码过期", "密码过期"),
    
    /** 等待审核状态 */
    WAIT_REVIEW(0x20, "等待审核", "等待系统或管理员审核"),
    
    /** 审核通过状态 */
    APPROVE(0x21, "审核通过", "审核已通过"),
    
    /** 审核拒绝状态 */
    DECLINE(0x22, "审核拒绝", "审核被拒绝"),
    
    /** 商品上架状态 */
    GOODS_PUT_AWAY(0x30, "上架", "商品上架"),
    
    /** 商品下架状态 */
    GOODS_SOLD_OUT(0x31, "下架", "商品下架"),
    
    /** 商品售完状态 */
    GOODS_SELL_OUT(0x32, "售完", "商品售完"),
    
    /** 订单待付款状态 */
    ORDER_PAYMENT_WAITING(0x40, "待付款", "订单等待付款中"),
    
    /** 订单支付成功状态 */
    ORDER_PAYMENT_SUCCESS(0x41, "支付成功", "订单支付已成功"),
    
    /** 订单支付失败状态 */
    ORDER_PAYMENT_FAILURE(0x42, "支付失败", "订单支付失败"),
    
    /** 订单待发货状态 */
    ORDER_WAITING_SHIPMENT(0x43, "待发货", "订单等待商家发货"),
    
    /** 订单待收货状态 */
    ORDER_WAITING_RECEIVING(0x44, "待收货状态", "订单已发货等待收货"),
    
    /** 订单已收货状态 */
    ORDER_RECEIVED(0x45, "已收货", "已收货"),
    
    /** 订单已评价状态 */
    ORDER_APPRAISE(0x46, "已评价", "评价"),
    
    /** 订单已完成状态 */
    ORDER_ACCOMPLISH(0x47, "订单已经完成", "订单已经完成"),
    
    /** 活动进行中状态 */
    ON_GOING(0x50, "进行中", "活动或任务正在进行中"),
    
    /** 活动未开始状态 */
    NOT_STARTED(0x51, "未开始", "活动或任务尚未开始"),
    
    /** 活动已结束状态 */
    FINISH(0x52, "已结束", "活动或任务已结束"),
    
    /** 过期状态 */
    EXPIRE(0x60, "过期", "已过期");

    /**
     * 状态提示信息
     */
    private final String msg;

    /**
     * 状态码值
     */
    private final int code;

    /**
     * 状态描述信息
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param code 状态码值，建议使用十六进制表示，便于分类管理
     * @param msg  状态提示信息，用于前端展示
     * @param desc 状态描述信息，用于开发人员理解
     */
    GXBusinessResultStatusCode(int code, String msg, String desc) {
        this.code = code;
        this.msg = msg;
        this.desc = desc;
    }
}
