const crypto = require('crypto');

class GXAuthCodeUtils {
    /**
     * 从字符串中截取指定长度的子串，支持正向和反向截取。
     * @param {string} str - 原始字符串
     * @param {number} startIndex - 起始索引
     * @param {number} [length] - 截取长度（可选）
     * @returns {string} 截取后的子串
     */
    static cutString(str, startIndex, length) {
        if (length === undefined) {
            return str.substring(startIndex);
        }

        if (startIndex >= 0) {
            if (length < 0) {
                length = Math.abs(length);
                startIndex = Math.max(0, startIndex - length);
            }
            if (startIndex > str.length) {
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

        length = Math.min(length, str.length - startIndex);
        return str.substring(startIndex, startIndex + length);
    }

    /**
     * 计算字符串的 MD5 哈希值。
     * @param {string} str - 输入字符串
     * @returns {string} MD5 哈希值（16进制）
     */
    static md5(str) {
        return crypto.createHash('md5').update(str).digest('hex');
    }

    /**
     * 生成 RC4 加密的密钥盒。
     * @param {Buffer} key - 密钥
     * @param {number} boxLength - 密钥盒长度
     * @returns {number[]} 密钥盒数组
     */
    static getKey(key, boxLength) {
        const keyBox = Array.from({ length: boxLength }, (_, i) => i);
        let swapIndex = 0;

        for (let i = 0; i < boxLength; i++) {
            swapIndex = (swapIndex + keyBox[i] + key[i % key.length]) % boxLength;
            [keyBox[i], keyBox[swapIndex]] = [keyBox[swapIndex], keyBox[i]];
        }

        return keyBox;
    }

    /**
     * 生成指定长度的随机字符串。
     * @param {number} length - 字符串长度
     * @returns {string} 随机字符串
     */
    static randomString(length) {
        const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
        let result = '';
        for (let i = 0; i < length; i++) {
            result += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        return result;
    }

    /**
     * RC4 加密或解密数据。
     * @param {Buffer} input - 输入数据
     * @param {string} key - 密钥
     * @returns {Buffer} 加密或解密后的数据
     */
    static rc4(input, key) {
        if (!input || !key) {
            return Buffer.alloc(0);
        }

        const output = Buffer.alloc(input.length);
        const keyBox = this.getKey(Buffer.from(key), 256);
        let i = 0;
        let j = 0;

        for (let offset = 0; offset < input.length; offset++) {
            i = (i + 1) % 256;
            j = (j + keyBox[i]) % 256;
            [keyBox[i], keyBox[j]] = [keyBox[j], keyBox[i]];
            const keyByte = keyBox[(keyBox[i] + keyBox[j]) % 256];
            output[offset] = input[offset] ^ keyByte;
        }

        return output;
    }

    /**
     * 加解密核心方法。
     * @param {string} source - 原始数据或加密数据
     * @param {string} key - 密钥
     * @param {string} operation - 操作类型（'ENCODE' 或 'DECODE'）
     * @param {number} [expiry=0] - 过期时间（秒）
     * @returns {string} 加解密结果
     */
    static authCode(source, key, operation, expiry = 0) {
        try {
            if (!source || !key) {
                return "{}";
            }

            const cKeyLength = 4;
            const hashedKey = this.md5(key);
            const keyA = this.md5(this.cutString(hashedKey, 0, 16));
            const keyB = this.md5(this.cutString(hashedKey, 16, 16));
            const keyC = operation === 'DECODE' ? this.cutString(source, 0, cKeyLength) : this.randomString(cKeyLength);
            const cryptKey = keyA + this.md5(keyA + keyC);

            if (operation === 'DECODE') {
                const encryptedData = Buffer.from(this.cutString(source, cKeyLength), 'base64');
                const decrypted = this.rc4(encryptedData, cryptKey).toString('utf8');
                const timestamp = parseInt(this.cutString(decrypted, 0, 10)) || 0;
                const hash = this.cutString(decrypted, 10, 16);
                const data = this.cutString(decrypted, 26);

                const expectedHash = this.cutString(this.md5(data + keyB), 0, 16);
                const isValid = (timestamp === 0 || timestamp > Math.floor(Date.now() / 1000)) && hash === expectedHash;
                return isValid ? data : "{}";
            } else {
                const timestamp = expiry > 0 ? Math.floor(Date.now() / 1000) + expiry : 0;
                const timestampStr = String(timestamp).padStart(10, '0');
                const hash = this.cutString(this.md5(source + keyB), 0, 16);
                const plaintext = timestampStr + hash + source;
                const encrypted = this.rc4(Buffer.from(plaintext, 'utf8'), cryptKey);
                const base64 = encrypted.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
                return keyC + base64;
            }
        } catch (e) {
            return "{}";
        }
    }

    /**
     * 编码数据。
     * @param {string} source - 原始数据
     * @param {string} key - 密钥
     * @param {number} [expiry=0] - 过期时间（秒）
     * @returns {string} 编码结果
     */
    static authCodeEncode(source, key, expiry = 0) {
        return this.base64EncodeUnicode(this.authCode(source, key, 'ENCODE', expiry));
    }

    /**
     * 解码数据。
     * @param {string} source - 编码数据
     * @param {string} key - 密钥
     * @returns {string} 解码结果
     */
    static authCodeDecode(source, key) {
        let base64Str = source.replace(/-/g, '+').replace(/_/g, '/');
        const pad = base64Str.length % 4;
        if (pad) {
            base64Str += '='.repeat(4 - pad);
        }
        const decoded = this.base64DecodeUnicode(base64Str);
        return this.authCode(decoded, key, 'DECODE');
    }

    /**
     * Unicode 安全的 Base64 编码。
     * @param {string} str - 输入字符串
     * @returns {string} Base64 编码结果
     */
    static base64EncodeUnicode(str) {
        const utf8Bytes = new TextEncoder().encode(str);
        return btoa(String.fromCharCode(...utf8Bytes));
    }

    /**
     * Unicode 安全的 Base64 解码。
     * @param {string} base64Encoded - Base64 编码字符串
     * @returns {string} 解码结果
     */
    static base64DecodeUnicode(base64Encoded) {
        const binaryStr = atob(base64Encoded);
        const utf8Bytes = Uint8Array.from(binaryStr, c => c.charCodeAt(0));
        return new TextDecoder().decode(utf8Bytes);
    }
}

// 测试验证
const original = "hello world 枫叶思源 子曦";
const key = "mysecret";
const encrypted = "N2FnMkhuWXlBWStaOVJWeWlWaDhvUWlMcWJkUDZMU0NFQzhLYXIydkJDM2RjQUxJQWVWUWpJeHRGYVVqYXlZRkYvSmppcVlsV29JTW5McldtdnJ4d2c9PQ";

console.log("=== 加密测试 ===");
const encoded = GXAuthCodeUtils.authCodeEncode(original, key,30);
console.log('加密结果:', encoded);
console.log('是否与预期一致:', encoded === encrypted);

console.log("=== 解密测试 ===");
const decoded = GXAuthCodeUtils.authCodeDecode(encrypted, key);
console.log('解密结果:', decoded);
console.log('是否与原始一致:', decoded === original);