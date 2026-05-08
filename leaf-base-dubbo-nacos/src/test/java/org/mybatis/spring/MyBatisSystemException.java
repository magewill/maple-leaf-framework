package org.mybatis.spring;

public class MyBatisSystemException extends RuntimeException {
    public MyBatisSystemException(String message) {
        super(message);
    }
}
