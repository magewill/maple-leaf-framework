const crypto = require('crypto');

class GXAuthCodeUtils {
    static cutString(str, startIndex, length) {
        if (length === undefined) {
            return str.substring(startIndex);
        }

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

        if (str.length - startIndex < length) {
            length = str.length - startIndex;
        }

        return str.substring(startIndex, startIndex + length);
    }

    static md5(str) {
        return crypto.createHash('md5').update(str).digest('hex');
    }

    static getKey(pass, kLen) {
        const mBox = new Array(kLen);
        for (let i = 0; i < kLen; i++) {
            mBox[i] = i;
        }

        let j = 0;
        for (let i = 0; i < kLen; i++) {
            j = (j + ((mBox[i] + 256) % 256) + pass[i % pass.length]) % kLen;
            const temp = mBox[i];
            mBox[i] = mBox[j];
            mBox[j] = temp;
        }

        return mBox;
    }

    static randomString(lens) {
        const chars = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];
        const cLens = chars.length;
        let sCode = '';

        for (let i = 0; i < lens; i++) {
            sCode += chars[Math.floor(Math.random() * cLens)];
        }

        return sCode;
    }

    static rc4(input, pass) {
        if (!input || !pass) {
            return Buffer.alloc(0);
        }

        const output = Buffer.alloc(input.length);
        const mBox = this.getKey(Buffer.from(pass), 256);

        let i = 0;
        let j = 0;
        for (let offset = 0; offset < input.length; offset++) {
            i = (i + 1) % mBox.length;
            j = (j + ((mBox[i] + 256) % 256)) % mBox.length;
            const temp = mBox[i];
            mBox[i] = mBox[j];
            mBox[j] = temp;
            const a = input[offset];
            const b = mBox[(this.toInt(mBox[i]) + this.toInt(mBox[j])) % mBox.length];
            output[offset] = a ^ b;
        }

        return output;
    }

    static toInt(b) {
        return (b + 256) % 256;
    }

    static authCode(source, key, operation, expiry = 0) {
        try {
            if (!source || !key) {
                return "{}";
            }

            const cKeyLength = 4;
            key = this.md5(key);
            const keyA = this.md5(this.cutString(key, 0, 16));
            const keyB = this.md5(this.cutString(key, 16, 16));
            const keyC = operation === 'DECODE' ? this.cutString(source, 0, cKeyLength) : this.randomString(cKeyLength);
            const cryptKey = keyA + this.md5(keyA + keyC);

            if (operation === 'DECODE') {
                const temp = Buffer.from(this.cutString(source, cKeyLength), 'base64');
                const result = this.rc4(temp, cryptKey).toString('utf8');

                if ((result.indexOf("0000000000") === 0 ||
                     parseInt(this.cutString(result, 0, 10)) - Math.floor(Date.now() / 1000) > 0) &&
                    this.cutString(result, 10, 16) === this.cutString(this.md5(this.cutString(result, 26) + keyB), 0, 16)) {
                    return this.cutString(result, 26);
                }
                return "{}";
            } else {
                expiry = expiry > 0 ? Math.floor(Date.now() / 1000) + expiry : 0;
                const expiryStr = expiry.toString().padStart(10, '0');
                const md5Str = this.md5(source + keyB);
                const sourceStr = expiryStr + this.cutString(md5Str, 0, 16) + source;
                const temp = this.rc4(Buffer.from(sourceStr, 'utf8'), cryptKey);
                return keyC + temp.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
            }
        } catch (e) {
            return "{}";
        }
    }

    static authCodeEncode(source, key, expiry = 0) {
        return this.base64EncodeUnicode(this.authCode(source, key, 'ENCODE', expiry));
    }

    static authCodeDecode(source, key) {
        // 处理URL安全的Base64
        let base64Str = source.replace(/-/g, '+').replace(/_/g, '/');
        const pad = base64Str.length % 4;
        if (pad) {
            base64Str += '='.repeat(4 - pad);
        }

        const base64DecodeStr = Buffer.from(base64Str, 'base64').toString('utf8');
        return this.authCode(base64DecodeStr, key, 'DECODE');
    }

    static base64EncodeUnicode(str) {
      const utf8Bytes = new TextEncoder().encode(str);
      const base64Encoded = btoa(String.fromCharCode(...utf8Bytes));
      return base64Encoded;
    }

    static base64DecodeUnicode(base64Encoded) {
      const utf8Bytes = Uint8Array.from(atob(base64Encoded), c => c.charCodeAt(0));
      const decodedStr = new TextDecoder().decode(utf8Bytes);
      return decodedStr;
    }
}

// 测试验证
const original = "hello world 城陈";
const key = "mysecret";
// const encrypted = "anJtOGczR3NoSGhGenpkSEx4NkRaQVBXS3RFSWNZTnB2a0d1ZmtJK2FYNEt1SFRFT3NtQTFwT3BpN0EySUxZVGNBN1UxYW89";
const encrypted = "c3I5dEhuQ3R1bTRTWHpCakhVa3NPZlU5cEwzZlcwdUpoc3NVVUVIOTJaY1c2N0RVWm0zOFJCSEVmSEpzNTNUbGdYQkM1TE9STEpVR2QrOWc";

// 加密测试
const encoded = GXAuthCodeUtils.authCodeEncode(original, key);
console.log('加密结果:', encoded);
console.log('是否一致:', encoded === encrypted);

// 解密测试
const decoded = GXAuthCodeUtils.authCodeDecode(encrypted, key);
console.log('解密结果:', decoded);
console.log('是否一致:', decoded === original);