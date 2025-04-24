package cn.maple.sso.utils;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.sso.enums.GXRandomType;

import java.awt.*;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

/**
 * <p>
 * 随机数工具类
 * </p>
 * 
 * <p>
 * 该工具类提供各种随机数据生成功能，包括：
 * 1. 随机字母数字混合字符串 - 用于验证码、临时密码等
 * 2. 纯数字随机字符串 - 用于数字验证码、随机ID等
 * 3. 随机汉字生成 - 用于中文验证码、测试数据等
 * 4. 随机颜色生成 - 用于图形验证码背景、UI元素等
 * 5. UUID生成 - 用于唯一标识符、会话ID等
 * </p>
 * 
 * <p>
 * 安全特性：
 * 1. 提供标准Random和SecureRandom两种实现
 * 2. 安全敏感场景（密码、令牌等）建议使用Secure系列方法
 * 3. 支持自定义字符集，增强灵活性和安全性
 * 4. 完善的参数验证，防止非法输入
 * 5. 线程安全设计，可在并发环境下使用
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 生成6位数字验证码
 * String verificationCode = GXRandomUtil.getNumber(6);
 * // 或使用安全版本
 * String secureCode = GXRandomUtil.getSecureNumber(6);
 * 
 * // 2. 生成8位随机密码（字母数字混合）
 * String password = GXRandomUtil.getCharacterAndNumber(8);
 * 
 * // 3. 生成不带连字符的UUID
 * String uuid = GXRandomUtil.get32UUID();
 * 
 * // 4. 生成4个随机汉字
 * String chineseChars = GXRandomUtil.getChinese(null, 4);
 * 
 * // 5. 生成随机颜色（用于验证码背景）
 * int[][] rgbArr = {{255,200,200}, {200,255,200}, {200,200,255}};
 * Color randomColor = GXRandomUtil.getColor(rgbArr);
 * </pre>
 * </p>
 *
 * @author britton birtton@126.com
 * @since 2021-09-16
 */
public class GXRandomUtil {
    /**
     * 常用汉字
     */
    public static final String CHINESE = "\u4e00\u4e8c\u4e09\u5341\u6728\u79be\u4e0a\u4e0b\u571f\u4e2a\u516b\u5165\u5927\u5929\u4eba\u706b\u6587\u516d\u4e03\u513f\u4e5d\u65e0\u53e3\u65e5\u4e2d\u4e86\u5b50\u95e8\u6708\u4e0d\u5f00\u56db\u4e94\u76ee\u8033\u5934\u7c73\u89c1\u767d\u7530\u7535\u4e5f\u957f\u5c71\u51fa\u98de\u9a6c\u9e1f\u4e91\u516c\u8f66\u725b\u7f8a\u5c0f\u5c11\u5dfe\u7259\u5c3a\u6bdb\u535c\u53c8\u5fc3\u98ce\u529b\u624b\u6c34\u5e7f\u5347\u8db3\u8d70\u65b9\u534a\u5df4\u4e1a\u672c\u5e73\u4e66\u81ea\u5df2\u4e1c\u897f\u56de\u7247\u76ae\u751f\u91cc\u679c\u51e0\u7528\u9c7c\u4eca\u6b63\u96e8\u4e24\u74dc\u8863\u6765\u5e74\u5de6\u53f3\u4e07\u767e\u4e01\u9f50\u51ac\u8bf4\u53cb\u8bdd\u6625\u670b\u9ad8\u4f60\u7eff\u4eec\u82b1\u7ea2\u8349\u7237\u4eb2\u8282\u7684\u5c81\u884c\u53e4\u5904\u58f0\u77e5\u591a\u5fd9\u6d17\u771f\u8ba4\u7236\u626b\u6bcd\u7238\u5199\u5168\u5b8c\u5173\u5bb6\u770b\u7b11\u7740\u5174\u753b\u4f1a\u5988\u5408\u5976\u653e\u5348\u6536\u5973\u6c14\u592a\u65e9\u53bb\u4eae\u548c\u674e\u8bed\u79c0\u5343\u9999\u542c\u8fdc\u5531\u5b9a\u8fde\u5411\u4ee5\u66f4\u540e\u610f\u4e3b\u603b\u5148\u8d77\u5e72\u660e\u8d76\u51c0\u540c\u4e13\u5de5\u624d\u7ea7\u961f\u8682\u8681\u524d\u623f\u7a7a\u7f51\u8bd7\u9ec4\u6797\u95ed\u7ae5\u7acb\u662f\u6211\u6735\u53f6\u7f8e\u673a\u5979\u8fc7\u4ed6\u65f6\u9001\u8ba9\u5417\u5f80\u5427\u5f97\u866b\u5f88\u6cb3\u501f\u59d0\u5462\u5440\u54ea\u8c01\u51c9\u6015\u91cf\u8ddf\u6700\u56ed\u8138\u56e0\u9633\u4e3a\u5149\u53ef\u6cd5\u77f3\u627e\u529e\u8bb8\u522b\u90a3\u5230\u90fd\u5413\u53eb\u518d\u505a\u8c61\u70b9\u50cf\u7167\u6c99\u6d77\u6865\u519b\u7af9\u82d7\u4e95\u9762\u4e61\u5fd8\u60f3\u5ff5\u738b\u8fd9\u4ece\u8fdb\u8fb9\u9053\u8d1d\u7537\u539f\u7231\u867e\u8dd1\u5439\u4e50\u5730\u8001\u5feb\u5e08\u77ed\u6de1\u5bf9\u70ed\u51b7\u60c5\u62c9\u6d3b\u628a\u79cd\u7ed9\u5403\u7ec3\u5b66\u4e60\u975e\u82e6\u5e38\u95ee\u4f34\u95f4\u5171\u4f19\u6c7d\u5206\u8981\u6ca1\u5b69\u4f4d\u9009\u5317\u6e56\u5357\u79cb\u6c5f\u53ea\u5e2e\u661f\u8bf7\u96ea\u5c31\u7403\u8df3\u73a9\u6843\u6811\u521a\u5170\u5ea7\u5404\u5e26\u5750\u6025\u540d\u53d1\u6210\u52a8\u665a\u65b0\u6709\u4e48\u5728\u53d8\u4ec0\u6761\u5b9c\u5b9e\u8272\u534e\u8c37\u91d1\u5c3d\u5c42\u4e30\u58ee\u6ce2\u6d6a\u706f\u4f5c\u5b57\u82f9\u4e3d\u52b3\u5c24\u5176\u533a\u5de8\u5b83\u5b89\u5757\u7ad9\u5df2\u7532\u8c46\u8bc6\u7eb7\u7ecf\u5982\u597d\u5a03\u6d3c\u4e8e\u9996\u679d\u67ab\u8bb0\u5218\u80e1\u620f\u68cb\u94a2\u89c2\u5f39\u7434\u517b\u4f11\u4f38\u751c\u6b4c\u9662\u9664\u606f\u60a8\u7275\u56f0\u5458\u9752\u5b81\u5ba4\u6837\u6821\u5207\u6559\u54cd\u73ed\u6b20\u5143\u5305\u949f\u53f9\u54c8\u8fdf\u95f9\u53ca\u8eab\u4ed4\u7ec6\u6b21\u5916\u8ba1\u6026\u793c\u52a0\u5915\u4e0e\u5ddd\u5dde\u53f0\u4e89\u6c11\u65cf\u4ebf\u6d01\u6b22\u7956\u65d7\u5e1c\u5e86\u66f2\u592e\u4ea4\u5e02\u65c1\u4f18\u9634\u575b\u57ce\u56fd\u56fe\u7533\u5339\u4e92\u4eac\u6cea\u6d0b\u62e5\u62b1\u76f8\u626c\u8bb2\u6253\u6307\u63a5\u60ca\u6545\u4faf\u5947\u5bf8\u843d\u8865\u62d4\u529f\u52a9\u53d6\u6240\u4fe1\u6cbf\u62fe\u9645\u86d9\u9519\u7b54\u8fd8\u8a00\u6bcf\u6cbb\u68f5\u6302\u54c7\u602a\u6162\u600e\u601d\u7a7f\u5f2f\u6bd4\u670d\u6d45\u6f02\u5566\u554a\u592b\u8868\u793a\u53f7\u6c57\u4f24\u5438\u6781\u4e32\u514d\u544a\u8bc9\u72d0\u72f8\u7334\u9897\u65a4\u6298\u6311\u6839\u72ec\u6ee1\u5bb9\u6613\u91c7\u80cc\u677f\u6905\u4f46\u508d\u6e05\u6d88\u7531\u672f\u5410\u6ce8\u8bfe\u94c5\u7b14\u684c\u666f\u62ff\u574f\u677e\u624e\u6293\u795d\u798f\u53e5\u5e78\u4e4b\u4ee4\u5e03\u76f4\u5f53\u7b2c\u73b0\u671f\u8f6e\u8def\u4e11\u6c38\u9965\u9971\u6e29\u8d2b\u5bcc\u6237\u4e9a\u89d2\u5468\u5e8a\u75c5\u59cb\u5f20\u5bfb\u54ed\u826f\u98df\u53cc\u4f53\u64cd\u573a\u4efd\u7c89\u6628\u6674\u59d1\u5a18\u59b9\u8bfb\u821f\u4e58\u97f3\u5ba2\u4f55\u6c6a\u4e1b\u7262\u62cd\u62a4\u4fdd\u7269\u9e21\u732b\u7fbd\u9886\u6349\u7406\u8dc3\u8e66\u7075\u6668\u5931\u89c9\u6254\u6389\u773c\u775b\u7eb8\u8239\u4e45\u4e4e\u81f3\u6b7b\u8170\u6361\u7c92\u88ab\u5e76\u591c\u559c\u91cd\u5473\u8f7b\u523b\u7fa4\u536b\u8fd0\u5b87\u5b99\u822a\u8230\u51b2\u6652\u6c60\u6d6e\u707e\u5bb3\u9ed1\u5668\u5cb8\u7eb9\u6d1e\u5f71\u5012\u6e38\u5706\u56f4\u676f\u4ef6\u4f4f\u987b\u80fd\u98d8\u5fc5\u4e8b\u5386\u53f2\u706d\u514b\u5316\u4ee3\u5b59\u690d\u5382\u4ea7\u4ecb\u519c\u79d1\u6280\u7eba\u7ec7\u8131\u51bb\u6eaa\u68c9\u63a2\u6447\u91ce\u8eb2\u89e3\u672a\u8ffd\u5e97\u67af\u5f90\u70e7\u8363\u83dc\u5bbf\u5188\u4e16\u754c\u8f70\u7b0b\u82bd\u558a\u547c\u5524\u5f1f\u54e5\u9aa8\u62bd\u62d0\u6d47\u7ec8\u9759\u8eba\u8c22\u6e10\u5fae\u74e6\u6cc9\u7136\u7ed3\u80a1\u8106\u5854\u675c\u9e43\u5192\u96f7\u8fc8\u8ff7\u8ff9\u53d4\u950b\u6ef4\u6d12\u6ce5\u6cde\u6251\u6258\u6478\u5229\u94c3\u5f31\u672b\u82ac\u82b3\u590f\u5e94\u8be5\u5c9b\u5c55\u5efa\u7eb1\u73af\u7ed5\u80dc\u9690\u7ea6\u7701\u8302\u76db\u543e\u5b63\u7559\u674f\u5bc6\u871c\u5761\u642d\u6458\u9489\u6c9f\u9f99\u5bff\u6cfc\u656c\u811a\u51e4\u675f\u5e9c\u593a\u626e\u4f1f\u591f\u6069\u67cf\u7279\u9c9c\u5ea6\u51f0\u8272\u52fe\u5355\u5bab\u96c4\u70c1\u8f89\u53e6\u9898\u6f2b\u54c4\u9a97\u5c14\u4ecd\u4fbf\u7968\u5f0f\u4e14\u714c\u5fd7\u63d0\u6717\u559d\u5200\u6c42\u4f7f\u82f1\u6574\u800c\u4e39\u4e4c\u663e\u4e1d\u7728\u9648\u659c\u542b\u7089\u9e23\u94f6\u6cca\u67f3\u827a\u5ffd\u6746\u6d9b\u8f6c\u5434\u7a97\u5cad\u7edd\u70df\u6d41\u5782\u4e71\u538b\u8d8a\u5f69\u8749\u86db\u5ca9\u8db4\u5228\u9646\u8d28\u539a\u6c89\u9003\u9635\u8679\u8718\u86db\u518c\u5b9d\u5370\u57cb\u94c1\u5e95\u5bfc\u76cf\u7a20\u9488\u7a00\u78b0\u5144\u5546\u6324\u51b3\u94b1\u6279\u62a5\u7834\u788e\u6ed1\u7ee7\u7eed\u5c01\u9a84\u50b2\u62ce\u6876\u505c\u806a\u80f3\u818a\u7538\u6643\u8361\u53ed\u73b2\u72d7\u7cdf\u68af\u697c\u80af\u8111\u7b4b\u8bb6\u8c08\u671b\u7b97\u6b64\u6869\u80a5\u7070\u8ba8\u538c\u51b0\u86cb\u58f3\u9e2d\u6b3a\u8d1f\u9e45\u7fc5\u8180\u52fa\u6597\u7389\u7ec4\u73cd\u73e0\u6570\u94bb\u7814\u7761\u8ddd\u79bb\u6cb9\u68c0\u67e5\u56e2\u65a5\u8d23\u708e\u5938\u5956\u4ea1\u8089\u8010\u8c1c\u4f20\u67d3\u7c7b\u4e25\u5bd2";

    /**
     * 静态随机对象
     * 注意：对于安全敏感场景，建议使用SECURE_RANDOM
     */
    public static final Random RANDOM = new Random();
    
    /**
     * 安全随机数生成器
     * 用于安全敏感场景，如生成密码、会话标识等
     */
    public static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private GXRandomUtil() {
    }

    /**
     * <p>
     * 生成长度为length的随机字母数字混合字符串
     * </p>
     * <p>
     * 生成的字符串包含大小写字母和数字，适用于：
     * - 验证码生成
     * - 临时密码生成
     * - 随机标识符生成
     * </p>
     *
     * @param length 指定字符串长度，建议不小于6位以保证安全性
     * @return 生成的随机字符串
     */
    public static String getCharacterAndNumber(int length) {
        return getText(GXRandomType.MIX, length);
    }

    /**
     * 根据指定的随机类型生成随机文本
     * <p>
     * 支持生成纯字母、纯数字或混合类型的随机字符串
     * 对于混合类型，会随机决定每个位置是字母还是数字
     * 字母类型会随机生成大写或小写字母
     * </p>
     *
     * @param randomType 随机类型，支持CHARACTER(字母)、NUMBER(数字)、MIX(混合)
     * @param length 生成的字符串长度
     * @return 生成的随机字符串
     */
    public static String getText(final GXRandomType randomType, final int length) {
        if (length <= 0) {
            return "";
        }
        
        StringBuilder out = new StringBuilder(length);
        GXRandomType rt = randomType;
        
        for (int i = 0; i < length; i++) {
            // 输出字母还是数字
            if (randomType == GXRandomType.MIX) {
                rt = RANDOM.nextInt(2) % 2 == 0 ? GXRandomType.CHARACTER : GXRandomType.NUMBER;
            }
            
            switch (rt) {
                case CHARACTER:
                    // 取得大写字母还是小写字母
                    int choice = RANDOM.nextInt(2) % 2 == 0 ? 65 : 97;
                    out.append((char) (choice + RANDOM.nextInt(26)));
                    break;
                case NUMBER:
                    out.append(RANDOM.nextInt(10));
                    break;
                default:
                    break;
            }
        }
        return out.toString();
    }
    
    /**
     * 使用安全随机数生成器生成随机文本
     * <p>
     * 此方法适用于安全敏感场景，如生成密码、会话标识等
     * 使用SecureRandom提供更高的随机性和安全性
     * </p>
     *
     * @param randomType 随机类型，支持CHARACTER(字母)、NUMBER(数字)、MIX(混合)
     * @param length 生成的字符串长度
     * @return 生成的安全随机字符串
     */
    public static String getSecureText(final GXRandomType randomType, final int length) {
        if (length <= 0) {
            return "";
        }
        
        StringBuilder out = new StringBuilder(length);
        GXRandomType rt = randomType;
        
        for (int i = 0; i < length; i++) {
            // 输出字母还是数字
            if (randomType == GXRandomType.MIX) {
                rt = SECURE_RANDOM.nextInt(2) % 2 == 0 ? GXRandomType.CHARACTER : GXRandomType.NUMBER;
            }
            
            switch (rt) {
                case CHARACTER:
                    // 取得大写字母还是小写字母
                    int choice = SECURE_RANDOM.nextInt(2) % 2 == 0 ? 65 : 97;
                    out.append((char) (choice + SECURE_RANDOM.nextInt(26)));
                    break;
                case NUMBER:
                    out.append(SECURE_RANDOM.nextInt(10));
                    break;
                default:
                    break;
            }
        }
        return out.toString();
    }

    /**
     * <p>
     * 生成长度为length的随机数字字符串
     * </p>
     * <p>
     * 生成的字符串仅包含数字0-9，适用于：
     * - 数字验证码
     * - 随机数值ID
     * - 临时PIN码
     * </p>
     *
     * @param length 指定字符串长度
     * @return 生成的随机数字字符串
     */
    public static String getNumber(int length) {
        return getText(GXRandomType.NUMBER, length);
    }
    
    /**
     * <p>
     * 使用安全随机数生成器生成随机数字字符串
     * </p>
     * <p>
     * 此方法适用于安全敏感场景，如支付验证码、安全令牌等
     * </p>
     *
     * @param length 指定字符串长度
     * @return 生成的安全随机数字字符串
     */
    public static String getSecureNumber(int length) {
        return getSecureText(GXRandomType.NUMBER, length);
    }

    /**
     * <p>
     * 生成长度为length的随机汉字字符串
     * </p>
     * <p>
     * 可以指定汉字Unicode范围，如不指定则使用默认的常用汉字集
     * 适用于：
     * - 中文验证码
     * - 随机中文名称
     * - 测试数据生成
     * </p>
     *
     * @param chineseUnicode 指定的汉字Unicode字符集，为null或空时使用默认汉字集
     * @param length 指定字符串长度
     * @return 生成的随机汉字字符串
     */
    public static String getChinese(String chineseUnicode, int length) {
        if (length <= 0) {
            return "";
        }
        
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(getChinese(chineseUnicode));
        }
        return out.toString();
    }

    /**
     * 获取一个随机汉字
     * <p>
     * 如果提供了自定义汉字集，则从中随机选择
     * 否则使用默认的常用汉字集
     * </p>
     *
     * @param chineseUnicode 指定的汉字Unicode字符集，为null或空时使用默认汉字集
     * @return 随机选择的一个汉字
     */
    public static char getChinese(String chineseUnicode) {
        if (CharSequenceUtil.isNotEmpty(chineseUnicode)) {
            return chineseUnicode.charAt(RANDOM.nextInt(chineseUnicode.length()));
        }
        return CHINESE.charAt(RANDOM.nextInt(CHINESE.length()));
    }

    /**
     * <p>
     * 获取随机常用颜色
     * </p>
     * <p>
     * 从提供的RGB颜色数组中随机选择一个颜色
     * 适用于：
     * - 验证码背景色生成
     * - 随机UI元素颜色
     * - 图表数据可视化
     * </p>
     *
     * @param rgbArr RGB 颜色数组，每个元素为包含3个整数(R,G,B)的数组
     * @return 随机生成的Color对象
     * @throws IllegalArgumentException 如果rgbArr为null或空数组
     */
    public static Color getColor(int[][] rgbArr) {
        if (rgbArr == null || rgbArr.length == 0) {
            throw new IllegalArgumentException("RGB颜色数组不能为空");
        }
        
        int[] rgb = rgbArr[RANDOM.nextInt(rgbArr.length)];
        return new Color(rgb[0], rgb[1], rgb[2]);
    }

    /**
     * <p>
     * 获取去掉"-"的32位UUID字符串
     * </p>
     * <p>
     * 生成不带连字符的UUID，适用于：
     * - 数据库主键
     * - 唯一标识符
     * - 文件名生成
     * </p>
     * 
     * @return 32位UUID字符串（不含连字符）
     */
    public static String get32UUID() {
        return getUUID().replace("-", "");
    }

    /**
     * <p>
     * 获取标准格式的UUID字符串
     * </p>
     * <p>
     * 生成包含连字符的标准UUID，适用于：
     * - 符合RFC 4122标准的场景
     * - 需要与其他系统交互的唯一标识
     * </p>
     * 
     * @return 36位标准格式UUID字符串（含连字符）
     */
    public static String getUUID() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * <p>
     * 获取基于安全随机数的UUID
     * </p>
     * <p>
     * 使用SecureRandom作为随机源生成UUID，提供更高的安全性
     * 适用于安全敏感场景，如：
     * - 会话标识
     * - 安全令牌
     * - 加密密钥ID
     * </p>
     * 
     * @param removeDash 是否移除连字符
     * @return UUID字符串
     */
    public static String getSecureUUID(boolean removeDash) {
        UUID uuid = UUID.randomUUID();
        String uuidStr = uuid.toString();
        return removeDash ? uuidStr.replace("-", "") : uuidStr;
    }
}