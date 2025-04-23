package cn.maple.core.framework.validator.group;

/**
 * 其他场景验证分组接口
 * <p>
 * 该接口用于在使用Bean Validation进行特殊场景或自定义场景的数据验证时，
 * 通过分组功能实现针对非标准场景的特定验证规则。当标准的验证分组（如更新、查询、登录等）
 * 无法满足特定业务需求时，可以使用此分组。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * public class SpecialOperationForm {
 *     @NotBlank(message = "操作类型不能为空", groups = {GXOtherGroup.class})
 *     private String operationType;
 *     
 *     @NotBlank(message = "操作人不能为空", groups = {GXOtherGroup.class})
 *     private String operator;
 *     
 *     @NotEmpty(message = "操作参数不能为空", groups = {GXOtherGroup.class})
 *     private Map&lt;String, Object&gt; parameters;
 *     
 *     // 自定义验证方法
 *     @AssertTrue(message = "操作参数验证失败", groups = {GXOtherGroup.class})
 *     public boolean isValidParameters() {
 *         if (parameters == null || parameters.isEmpty()) {
 *             return false;
 *         }
 *         // 根据不同的操作类型验证不同的参数
 *         switch (operationType) {
 *             case "export":
 *                 return parameters.containsKey("format") && parameters.containsKey("columns");
 *             case "import":
 *                 return parameters.containsKey("file") && parameters.containsKey("type");
 *             default:
 *                 return true;
 *         }
 *     }
 * }
 * 
 * // 在Controller中使用
 * @PostMapping("/special-operation")
 * public GXResultUtils<?> performSpecialOperation(@Validated(GXOtherGroup.class) @RequestBody SpecialOperationForm form) {
 *     // 执行特殊操作逻辑
 *     return specialService.performOperation(form.getOperationType(), form.getOperator(), form.getParameters());
 * }
 * </pre>
 * 
 * <p>
 * 通过使用GXOtherGroup分组，可以为不适合标准分组的特殊业务场景提供验证支持，
 * 增强系统的灵活性和扩展性，同时保持验证逻辑的清晰和一致。
 * </p>
 */
public interface GXOtherGroup {
}
