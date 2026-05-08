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

public class GXAuthCodeUtils {
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


    private static String cutString(String str, int startIndex) {
        return cutString(str, startIndex, str.length());
    }


    public static boolean fileExists(String filename) {
        if (filename == null) {
            return false;
        }
        File f = new File(filename);
        return f.exists();
    }


    private static String md5(String str) {
        return SecureUtil.md5(str);
    }


    public static boolean strIsNullOrEmpty(String str) {
        return str == null || str.trim().equals("");
    }


    private static byte[] getKey(byte[] pass, int kLen) {
        byte[] mBox = new byte[kLen];
        for (int i = 0; i < kLen; i++) {
            mBox[i] = (byte) i;
        }
        int j = 0;
        for (int i = 0; i < kLen; i++) {
            j = (j + ((mBox[i] + 256) % 256) + pass[i % pass.length]) % kLen;
            byte temp = mBox[i];
            mBox[i] = mBox[j];
            mBox[j] = temp;
        }
        return mBox;
    }

    private static String randomString(int lens) {
        char[] chars = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        int cLens = chars.length;
        StringBuilder sCode = new StringBuilder(lens);
        for (int i = 0; i < lens; i++) {
            sCode.append(chars[RandomUtil.randomInt(cLens)]);
        }
        return sCode.toString();
    }

    public static String authCodeEncode(String source, String key, int expiry) {
        String authCodeStr = authCode(source, key, GXAuthCodeMode.ENCODE, expiry);
        return Base64Encoder.encodeUrlSafe(authCodeStr.getBytes(StandardCharsets.UTF_8));
    }

    public static String authCodeEncode(String source, String key) {
        return authCodeEncode(source, key, 0);
    }

    public static String authCodeDecode(String source, String key) {
        try {
            if (CharSequenceUtil.isEmpty(source)) {
                return "{}";
            }
            String base64DecodeStr = Base64Decoder.decodeStr(source);
            return authCode(base64DecodeStr, key, GXAuthCodeMode.DECODE, 0);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String authCode(String source, String key, GXAuthCodeMode operation, int expiry) {
        try {
            if (CharSequenceUtil.isEmpty(source) || CharSequenceUtil.isEmpty(key)) {
                return "{}";
            }
            int cKeyLength = 4;
            String keyA;
            String keyB;
            String keyC;
            String cryptKey;
            String result;

            key = md5(key);
            keyA = md5(cutString(key, 0, 16));
            keyB = md5(cutString(key, 16, 16));
            keyC = operation == GXAuthCodeMode.DECODE ? cutString(source, 0, cKeyLength) : randomString(cKeyLength);
            cryptKey = keyA + md5(keyA + keyC);

            if (operation == GXAuthCodeMode.DECODE) {
                if (source.length() <= cKeyLength) {
                    return "{}";
                }
                byte[] temp;
                temp = Base64.decode(cutString(source, cKeyLength).getBytes(StandardCharsets.UTF_8));
                result = new String(rc4(temp, cryptKey), StandardCharsets.UTF_8);
                if ((result.indexOf("0000000000") == 0 || Long.parseLong(cutString(result, 0, 10)) - DateUtil.currentSeconds() > 0) &&
                        cutString(result, 10, 16).equals(cutString(md5(cutString(result, 26) + keyB), 0, 16))) {
                    return cutString(result, 26);
                }
                return "{}";
            } else {
                long expiryTime = expiry > 0 ? DateUtil.currentSeconds() + expiry : 0;
                source = String.format("%010d", expiryTime) + cutString(md5(source + keyB), 0, 16) + source;
                byte[] temp = rc4(source.getBytes(StandardCharsets.UTF_8), cryptKey);
                return String.format("%s%s", keyC, Base64.encode(temp));
            }
        } catch (Exception e) {
            return "{}";
        }
    }

    private static byte[] rc4(byte[] input, String pass) {
        if (input == null || pass == null) {
            return new byte[0];
        }
        byte[] output = new byte[input.length];
        byte[] mBox = getKey(pass.getBytes(StandardCharsets.UTF_8), 256);
        int i = 0;
        int j = 0;
        for (int offset = 0; offset < input.length; offset++) {
            i = (i + 1) % mBox.length;
            j = (j + ((mBox[i] + 256) % 256)) % mBox.length;
            byte temp = mBox[i];
            mBox[i] = mBox[j];
            mBox[j] = temp;
            byte a = input[offset];
            byte b = mBox[(toInt(mBox[i]) + toInt(mBox[j])) % mBox.length];
            output[offset] = (byte) ((int) a ^ toInt(b));
        }
        return output;
    }

    private static int toInt(byte b) {
        return (b + 256) % 256;
    }

    private enum GXAuthCodeMode {
        ENCODE, DECODE
    }
}
