package cn.maple.redisson.cache;

import javax.cache.CacheException;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDK序列化工具类
 * <p>
 * 该类提供基于JDK标准序列化机制的对象序列化和反序列化功能。
 * 使用ObjectOutputStream和ObjectInputStream进行对象的序列化和反序列化操作。
 * 所有方法都是线程安全的，适合在多线程环境下使用。
 * </p>
 * <p>
 * 安全说明：
 * 1. 该序列化机制仅适用于可信任的数据，不应用于处理不可信来源的数据
 * 2. 反序列化过程中存在潜在的安全风险，应确保序列化的类遵循安全的序列化实践
 * 3. 建议被序列化的类实现自定义的readObject和writeObject方法以控制序列化行为
 * </p>
 */
final class JDKSerializer {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = Logger.getLogger(JDKSerializer.class.getName());

    /**
     * 序列化缓冲区初始大小（字节）
     */
    private static final int BUFFER_SIZE = 1024;

    /**
     * 私有化构造函数，防止实例化
     * 工具类应使用静态方法，不应被实例化
     */
    private JDKSerializer() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 序列化对象为字节数组
     * <p>
     * 将Java对象序列化为字节数组，使用标准的JDK序列化机制。
     * 该方法使用try-with-resources确保资源正确关闭，避免内存泄漏。
     * </p>
     *
     * @param obj 待序列化的对象，可以为null
     * @return 序列化后的字节数组，如果输入为null则返回空数组
     * @throws CacheException 如果序列化过程中发生异常
     */
    static byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        try (
                ByteArrayOutputStream bao = new ByteArrayOutputStream(BUFFER_SIZE);
                ObjectOutputStream oos = new ObjectOutputStream(bao)
        ) {
            oos.writeObject(obj);
            oos.flush(); // 确保所有数据都写入底层流
            return bao.toByteArray();
        } catch (NotSerializableException e) {
            LOGGER.log(Level.SEVERE, "对象不可序列化: " + e.getMessage(), e);
            throw new CacheException("对象不可序列化: " + e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "序列化过程中发生IO异常: " + e.getMessage(), e);
            throw new CacheException("序列化失败: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "序列化过程中发生未知异常: " + e.getMessage(), e);
            throw new CacheException("序列化过程中发生未知异常", e);
        }
    }

    /**
     * 反序列化字节数组为对象
     * <p>
     * 将字节数组反序列化为Java对象，使用标准的JDK反序列化机制。
     * 该方法使用try-with-resources确保资源正确关闭，避免内存泄漏。
     * 对于空或null的字节数组，直接返回null而不进行反序列化操作。
     * </p>
     *
     * @param bytes 待反序列化的字节数组，可以为null
     * @return 反序列化后的对象，如果输入为null或空数组则返回null
     * @throws CacheException 如果反序列化过程中发生异常
     */
    static Object unSerialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (
                ByteArrayInputStream bai = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bai)
        ) {
            return ois.readObject();
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "找不到反序列化的类: " + e.getMessage(), e);
            throw new CacheException("找不到反序列化的类: " + e.getMessage(), e);
        } catch (InvalidClassException e) {
            LOGGER.log(Level.SEVERE, "反序列化的类无效: " + e.getMessage(), e);
            throw new CacheException("反序列化的类无效: " + e.getMessage(), e);
        } catch (StreamCorruptedException e) {
            LOGGER.log(Level.SEVERE, "反序列化的字节流已损坏: " + e.getMessage(), e);
            throw new CacheException("反序列化的字节流已损坏", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "反序列化过程中发生IO异常: " + e.getMessage(), e);
            throw new CacheException("反序列化失败: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "反序列化过程中发生未知异常: " + e.getMessage(), e);
            throw new CacheException("反序列化过程中发生未知异常", e);
        }
    }
}
