package cn.maple.sso.captcha;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>
 * Gif 编码器
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public class Encoder {

    static final int BITS = 12;
    /**
     * 80% 占用率
     */
    static final int H_SIZE = 5003;
    private static final int EOF = -1;
    /**
     * number of bits/code
     */
    int nBits;
    /**
     * user settable max # bits/code
     */
    int maxBits = BITS;
    /**
     * maximum code, given n_bits
     */
    int maxCode;
    /**
     * should NEVER generate this code
     */
    int maxMaxCode = 1 << BITS;
    int[] hTab = new int[H_SIZE];
    int[] codeTab = new int[H_SIZE];
    int freeEnt = 0;
    boolean clearFlg = false;
    // Algorithm:  use open addressing double hashing (no chaining) on the
    // prefix code / next character combination.  We do a variant of Knuth's
    // algorithm D (vol. 3, sec. 6.4) along with G. Knott's relatively-prime
    // secondary probe.  Here, the modular division first probe is gives way
    // to a faster exclusive-or manipulation.  Also do block compression with
    // an adaptive reset, whereby the code table is cleared when the compression
    // ratio decreases, but after the table fills.  The variable-length output
    // codes are re-sized at this point, and a special CLEAR code is generated
    // for the decompressor.  Late addition:  construct the table according to
    // file size for noticeable speed improvement on small files.  Please direct
    // questions about this implementation to ames!jaw.
    int gInitBits;
    int clearCode;
    int eofCode;
    int curAccum = 0;
    int curBits = 0;
    int[] masks = {
            0x0000,
            0x0001,
            0x0003,
            0x0007,
            0x000F,
            0x001F,
            0x003F,
            0x007F,
            0x00FF,
            0x01FF,
            0x03FF,
            0x07FF,
            0x0FFF,
            0x1FFF,
            0x3FFF,
            0x7FFF,
            0xFFFF};
    /**
     * Number of characters so far in this 'packet'
     */
    int aCount;
    /**
     * Define the storage for the packet accumulator
     */
    byte[] accum = new byte[256];

    // output
    //
    // Output the given code.
    // Inputs:
    //      code:   A n_bits-bit integer.  If == -1, then EOF.  This assumes
    //              that n_bits =< wordsize - 1.
    // Outputs:
    //      Outputs code to the file.
    // Assumptions:
    //      Chars are 8 bits long.
    // Algorithm:
    //      Maintain a BITS character long buffer (so that 8 codes will
    // fit in it exactly).  Use the VAX insv instruction to insert each
    // code in turn.  When the buffer fills up empty it and start over.
    /**
     * 图片的宽高
     */
    private final int imgW;
    private final int imgH;
    private final byte[] pixAry;
    /**
     * 验证码位数
     */
    private final int initCodeSize;
    /**
     * 剩余数量
     */
    private int remaining;
    /**
     * 像素
     */
    private int curPixel;

    /**
     * <p>
     * 编码器
     * </p>
     *
     * @param width      宽度
     * @param height     高度
     * @param pixels     像素
     * @param colorDepth 颜色
     */
    Encoder(int width, int height, byte[] pixels, int colorDepth) {
        imgW = width;
        imgH = height;
        pixAry = pixels;
        initCodeSize = Math.max(2, colorDepth);
    }

    // Add a character to the end of the current packet, and if it is 254
    // characters, flush the packet to disk.

    /**
     * @param c    字节
     * @param outs 输出流
     * @throws IOException IO异常
     */
    void charOut(byte c, OutputStream outs) throws IOException {
        accum[aCount++] = c;
        if (aCount >= 254) {
            flushChar(outs);
        }
    }

    // Clear out the hash table

    // table clear for block compress

    void clBlock(OutputStream outs) throws IOException {
        clHash(H_SIZE);
        freeEnt = clearCode + 2;
        clearFlg = true;

        output(clearCode, outs);
    }

    // reset code table

    void clHash(int hSize) {
        for (int i = 0; i < hSize; ++i) {
            hTab[i] = -1;
        }
    }

    /**
     * <p>
     * 压缩
     * </p>
     *
     * @param initBits int
     * @param outs     输出流
     * @throws IOException IO异常
     */
    void compress(int initBits, OutputStream outs) throws IOException {
        int fcode;
        int i /* = 0 */;
        int c;
        int ent;
        int disp;
        int hSizeReg;
        int hshift;

        // Set up the globals:  g_init_bits - initial number of bits
        gInitBits = initBits;

        // Set up the necessary values
        clearFlg = false;
        nBits = gInitBits;
        maxCode = calMaxCode(nBits);

        clearCode = 1 << (initBits - 1);
        eofCode = clearCode + 1;
        freeEnt = clearCode + 2;

        // clear packet
        aCount = 0;

        ent = nextPixel();

        hshift = 0;
        for (fcode = H_SIZE; fcode < 65536; fcode *= 2) {
            ++hshift;
        }
        // set hash code range bound
        hshift = 8 - hshift;

        hSizeReg = H_SIZE;
        // clear hash table
        clHash(hSizeReg);

        output(clearCode, outs);

        outer_loop:
        while ((c = nextPixel()) != EOF) {
            fcode = (c << maxBits) + ent;
            // xor hashing
            i = (c << hshift) ^ ent;
            if (hTab[i] == fcode) {
                ent = codeTab[i];
                continue;
            }
            // non-empty slot
            else if (hTab[i] >= 0) {
                // secondary hash (after G. Knott)
                disp = hSizeReg - i;
                if (i == 0) {
                    disp = 1;
                }
                do {
                    if ((i -= disp) < 0) {
                        i += hSizeReg;
                    }
                    if (hTab[i] == fcode) {
                        ent = codeTab[i];
                        continue outer_loop;
                    }
                } while (hTab[i] >= 0);
            }
            output(ent, outs);
            ent = c;
            if (freeEnt < maxMaxCode) {
                // code -> hashtable
                codeTab[i] = freeEnt++;
                hTab[i] = fcode;
            } else {
                clBlock(outs);
            }
        }
        // Put out the final code.
        output(ent, outs);
        output(eofCode, outs);
    }

    void encode(OutputStream os) throws IOException {
        // write "initial code size" byte
        os.write(initCodeSize);

        // reset navigation variables
        remaining = imgW * imgH;
        curPixel = 0;

        // compress and write the pixel data
        compress(initCodeSize + 1, os);

        // write block terminator
        os.write(0);
    }

    void flushChar(OutputStream outs) throws IOException {
        if (aCount > 0) {
            outs.write(aCount);
            outs.write(accum, 0, aCount);
            aCount = 0;
        }
    }

    final int calMaxCode(int nBits) {
        return (1 << nBits) - 1;
    }

    //----------------------------------------------------------------------------
    // Return the next pixel from the image
    //----------------------------------------------------------------------------

    private int nextPixel() {
        if (remaining == 0) {
            return EOF;
        }
        --remaining;
        byte pix = pixAry[curPixel++];
        return pix & 0xff;
    }

    void output(int code, OutputStream outs) throws IOException {
        curAccum &= masks[curBits];
        if (curBits > 0) {
            curAccum |= (code << curBits);
        } else {
            curAccum = code;
        }
        curBits += nBits;
        while (curBits >= 8) {
            charOut((byte) (curAccum & 0xff), outs);
            curAccum >>= 8;
            curBits -= 8;
        }

        // If the next entry is going to be too big for the code size,
        // then increase it, if possible.
        if (freeEnt > maxCode || clearFlg) {
            if (clearFlg) {
                maxCode = calMaxCode(nBits = gInitBits);
                clearFlg = false;
            } else {
                ++nBits;
                if (nBits == maxBits) {
                    maxCode = maxMaxCode;
                } else {
                    maxCode = calMaxCode(nBits);
                }
            }
        }
        if (code == eofCode) {
            // At EOF, write the rest of the buffer.
            while (curBits > 0) {
                charOut((byte) (curAccum & 0xff), outs);
                curAccum >>= 8;
                curBits -= 8;
            }
            flushChar(outs);
        }
    }
}
