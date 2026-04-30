package cn.maple.core.framework.exception;

import java.sql.SQLException;

public class GXDBSQLException extends SQLException {
    public GXDBSQLException(String reason, String SQLState, int vendorCode) {
        super(reason, SQLState, vendorCode);
    }
}
