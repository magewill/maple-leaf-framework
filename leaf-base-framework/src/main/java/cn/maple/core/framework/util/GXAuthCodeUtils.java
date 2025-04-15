package cn.maple.core.framework.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.codec.Base64Decoder;
import cn.hutool.core.codec.Base64Encoder;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 用于和PHP通信时的加解密，加解密双方的数据
 * 此类不可随便更改，否则可能导致双方加解密不成功
 *
 * @author zj chen <britton@126.com>
 */
public class GXAuthCodeUtils {
    /**
     * private static MD5 md5 = new MD5();
     * private static BASE64 base64 = new BASE64();
     * 从字符串的指定位置截取指定长度的子字符串
     *
     * @param str        原字符串
     * @param startIndex 子字符串的起始位置
     * @param length     子字符串的长度
     * @return 子字符串
     */
    private static String cutString(String str, int startIndex, int length) {
        if (startIndex >= 0) {
            if (length < 0) {
                length = length * -1;
                if (startIndex - length < 0) {
                    length = startIndex;
                    startIndex = 0;
                } else {
                    startIndex = startIndex - length;
                }
            }
            if (startIndex > str.length()) {
                return "";
            }
        } else {
            if (length < 0) {
                return "";
            }
            if (length + startIndex <= 0) {
                return "";
            }
            length = length + startIndex;
            startIndex = 0;
        }
        if (str.length() - startIndex < length) {
            length = str.length() - startIndex;
        }
        return str.substring(startIndex, startIndex + length);
    }

    /**
     * 从字符串的指定位置开始截取到字符串结尾的了符串
     *
     * @param str        原字符串
     * @param startIndex 子字符串的起始位置
     * @return 子字符串
     */
    private static String cutString(String str, int startIndex) {
        return cutString(str, startIndex, str.length());
    }

    /**
     * 检查指定文件是否存在
     * 注意：此方法不检查文件权限，只检查文件是否存在
     * 建议使用java.nio.file.Files.exists或hutool的FileUtil.exist方法替代
     *
     * @param filename 文件路径名
     * @return 如果文件存在返回true，否则返回false
     */
    public static boolean fileExists(String filename) {
        if (filename == null) {
            return false;
        }
        File f = new File(filename);
        return f.exists();
    }

    /**
     * MD5函数
     *
     * @param str 原始字符串
     * @return MD5结果
     */
    private static String md5(String str) {
        return SecureUtil.md5(str);
    }

    /**
     * 判断字符串是否为null或空字符串
     * 注意：该方法会对字符串进行trim()处理，因此包含空白字符的字符串也会被视为空
     * 建议使用hutool的CharSequenceUtil.isEmpty或isBlank方法替代
     *
     * @param str 待检查的字符串
     * @return 如果字符串为null或trim后为空字符串，返回true；否则返回false
     */
    public static boolean strIsNullOrEmpty(String str) {
        return str == null || str.trim().equals("");
    }

    /**
     * 用于RC4算法的密钥调度算法(KSA)，初始化S盒
     * 该方法实现了RC4算法的第一阶段，根据密钥生成初始的S盒
     * 过程包括：
     * 1. 初始化S盒为0到255的顺序排列
     * 2. 根据密钥对S盒进行置换
     *
     * @param pass 密钥字节数组
     * @param kLen S盒大小，标准RC4使用256
     * @return 初始化后的S盒
     */
    private static byte[] getKey(byte[] pass, int kLen) {
        // 初始化S盒
        byte[] mBox = new byte[kLen];
        for (int i = 0; i < kLen; i++) {
            mBox[i] = (byte) i;
        }
        // 根据密钥对S盒进行置换
        int j = 0;
        for (int i = 0; i < kLen; i++) {
            // 计算新位置，确保在0-255范围内
            j = (j + ((mBox[i] + 256) % 256) + pass[i % pass.length]) % kLen;
            // 交换S盒中的值
            byte temp = mBox[i];
            mBox[i] = mBox[j];
            mBox[j] = temp;
        }
        return mBox;
    }

    /**
     * 生成随机字符
     *
     * @param lens 随机字符长度
     * @return 随机字符
     */
    private static String randomString(int lens) {
        char[] chars = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        int cLens = chars.length;
        StringBuilder sCode = new StringBuilder();
        for (int i = 0; i < lens; i++) {
            sCode.append(chars[RandomUtil.randomInt(cLens)]);
        }
        return sCode.toString();
    }

    /**
     * 使用 DisCuz authCode 方法对字符串加密
     *
     * @param source 原始字符串
     * @param key    密钥
     * @param expiry 加密字串有效时间，单位是秒
     * @return 加密结果
     */
    public static String authCodeEncode(String source, String key, int expiry) {
        String authCodeStr = authCode(source, key, GXAuthCodeMode.ENCODE, expiry);
        //return Base64Utils.encodeToUrlSafeString(authCodeStr.getBytes(StandardCharsets.UTF_8));
        return Base64Encoder.encodeUrlSafe(authCodeStr.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 使用 DisCuz authCode 方法对字符串加密
     *
     * @param source 原始字符串
     * @param key    密钥
     * @return 加密结果
     */
    public static String authCodeEncode(String source, String key) {
        return authCodeEncode(source, key, 0);
    }

    /**
     * 使用 DisCuz authCode 方法对字符串解密
     *
     * @param source 原始字符串
     * @param key    密钥
     * @return 解密结果
     */
    public static String authCodeDecode(String source, String key) {
        //String base64DecodeStr = new String(Base64Utils.decodeFromUrlSafeString(source));
        String base64DecodeStr = Base64Decoder.decodeStr(source);
        return authCode(base64DecodeStr, key, GXAuthCodeMode.DECODE, 0);
    }

    /**
     * 使用变形的RC4编码方法对字符串进行加密或者解密
     * 该方法实现了与Discuz论坛系统兼容的加密解密算法
     * 加密过程：
     * 1. 对密钥进行MD5处理
     * 2. 分割密钥并生成加密所需的密钥组合
     * 3. 添加过期时间和校验信息
     * 4. 使用RC4算法加密
     * 5. Base64编码
     * 
     * 解密过程：
     * 1. 提取密钥信息
     * 2. Base64解码
     * 3. RC4解密
     * 4. 验证有效期和校验和
     * 5. 提取原始数据
     *
     * @param source    原始字符串
     * @param key       密钥
     * @param operation 操作类型：加密或解密
     * @param expiry    加密字串过期时间（秒），仅在加密时有效，0表示永不过期
     * @return 加密或者解密后的字符串，失败时返回"{}"
     */
    private static String authCode(String source, String key, GXAuthCodeMode operation, int expiry) {
        try {
            if (CharSequenceUtil.isEmpty(source) || CharSequenceUtil.isEmpty(key)) {
                return "{}";
            }
            // 密钥前缀长度
            int cKeyLength = 4;
            String keyA;
            String keyB;
            String keyC;
            String cryptKey;
            String result;
            
            // 对密钥进行MD5处理
            key = md5(key);
            // 分割密钥的前16位进行MD5
            keyA = md5(cutString(key, 0, 16));
            // 分割密钥的后16位进行MD5
            keyB = md5(cutString(key, 16, 16));
            // 解密时从密文提取密钥前缀，加密时随机生成
            keyC = operation == GXAuthCodeMode.DECODE ? cutString(source, 0, cKeyLength) : randomString(cKeyLength);
            // 组合加密密钥
            cryptKey = keyA + md5(keyA + keyC);
            
            if (operation == GXAuthCodeMode.DECODE) {
                // 解密过程
                byte[] temp;
                // 从密文中提取实际数据并Base64解码
                temp = Base64.decode(cutString(source, cKeyLength).getBytes(StandardCharsets.UTF_8));
                // 使用RC4算法解密
                result = new String(rc4(temp, cryptKey));
                // 验证时间戳和数据完整性
                // 检查是否为永不过期(0000000000开头)或者是否在有效期内，同时验证校验和
                if ((result.indexOf("0000000000") == 0 || Integer.parseInt(cutString(result, 0, 10)) - DateUtil.currentSeconds() > 0) && 
                    cutString(result, 10, 16).equals(cutString(md5(cutString(result, 26) + keyB), 0, 16))) {
                    // 提取原始数据
                    return cutString(result, 26);
                }
                return "{}";
            } else {
                // 加密过程
                // 处理过期时间，0表示永不过期
                expiry = expiry > 0 ? (int) (DateUtil.currentSeconds() + expiry) : 0;
                // 组合数据格式：过期时间(10位) + 校验和(16位) + 原始数据
                source = String.format("%010d", expiry) + cutString(md5(source + keyB), 0, 16) + source;
                // 使用RC4算法加密
                byte[] temp = rc4(source.getBytes(StandardCharsets.UTF_8), cryptKey);
                // 组合最终密文：密钥前缀 + Base64编码的加密数据
                return String.format("%s%s", keyC, Base64.encode(temp));
            }
        } catch (Exception e) {
            // 捕获所有异常，确保加解密过程不会抛出异常
            return "{}";
        }
    }

    /**
     * RC4 加密/解密算法实现
     * RC4是一种对称加密算法，加密和解密使用相同的密钥和算法
     * 该实现遵循标准RC4算法流程：
     * 1. 初始化S盒(通过getKey方法)
     * 2. 伪随机生成密钥流
     * 3. 将密钥流与输入数据进行XOR操作
     *
     * @param input 原始字节数组
     * @param pass  密钥字符串
     * @return 处理后的字节数组，如果输入为null则返回空数组
     */
    private static byte[] rc4(byte[] input, String pass) {
        if (input == null || pass == null) {
            return new byte[0];
        }
        byte[] output = new byte[input.length];
        // 初始化S盒
        byte[] mBox = getKey(pass.getBytes(), 256);
        // 加密/解密过程
        int i = 0;
        int j = 0;
        for (int offset = 0; offset < input.length; offset++) {
            // 生成密钥流
            i = (i + 1) % mBox.length;
            j = (j + ((mBox[i] + 256) % 256)) % mBox.length;
            // 交换S盒中的值
            byte temp = mBox[i];
            mBox[i] = mBox[j];
            mBox[j] = temp;
            // 获取输入字节
            byte a = input[offset];
            // 生成密钥流字节
            // 注释说明：原代码中有错误的计算方式，已修正
            // byte b = mBox[(mBox[i] + mBox[j] % mBox.Length) % mBox.Length];
            // mBox[j] 一定比 mBox.Length 小，不需要在取模
            byte b = mBox[(toInt(mBox[i]) + toInt(mBox[j])) % mBox.length];
            // 使用XOR操作加密/解密
            output[offset] = (byte) ((int) a ^ toInt(b));
        }
        return output;
    }

    /**
     * 将字节转换为无符号整数(0-255)
     * 在Java中，byte是有符号的(-128到127)，此方法将其转换为无符号值(0-255)
     * 这在RC4算法中非常重要，因为它需要处理无符号字节值
     *
     * @param b byte参数
     * @return 转换后的无符号整数(0-255)
     */
    private static int toInt(byte b) {
        return (b + 256) % 256;
    }

    /**
     * 操作类型
     */
    private enum GXAuthCodeMode {
        ENCODE, DECODE
    }
}
