/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Vladislav Zablotsky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package cn.maple.core.datasource.handler.type.mybatis;

import cn.maple.core.framework.exception.GXBusinessException;
import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import tools.jackson.core.JacksonException;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Map JSON string as value container with JsonNode.
 * Should always return not null value.
 * Use JSON string representation as intermediate data format.
 *
 * @see GXJsonNodeValue
 */
@MappedTypes({GXJsonNodeValue.class})
public class GXJsonNodeValueTypeHandler extends BaseTypeHandler<GXJsonNodeValue> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, GXJsonNodeValue parameter, JdbcType jdbcType) throws SQLException {
        if (parameter.isPresent()) {
            String json;
            if (parameter.hasDbSource()) {
                json = parameter.getSource();
            } else {
                try {
                    json = GXReaderWriter.write(parameter.get());
                } catch (JacksonException ex) {
                    throw new GXBusinessException(ex.getMessage(), ex);
                }
            }
            ps.setString(i, json);
        } else {
            ps.setString(i, null);
        }
    }

    @Override
    public GXJsonNodeValue getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String jsonSource = rs.getString(columnName);
        return GXJsonNodeValue.fromDb(jsonSource);
    }

    @Override
    public GXJsonNodeValue getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String jsonSource = rs.getString(columnIndex);
        return GXJsonNodeValue.fromDb(jsonSource);
    }

    @Override
    public GXJsonNodeValue getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String jsonSource = cs.getString(columnIndex);
        return GXJsonNodeValue.fromDb(jsonSource);
    }

    /*
    Override BaseTypeHandler in such way that result will never be null
     */
    @Override
    public GXJsonNodeValue getResult(ResultSet rs, String columnName) throws SQLException {
        try {
            return getNullableResult(rs, columnName);
        } catch (Exception e) {
            throw new ResultMapException("Error attempting to get column '" + columnName + "' from result set.  Cause: " + e, e);
        }
    }

    @Override
    public GXJsonNodeValue getResult(ResultSet rs, int columnIndex) throws SQLException {
        try {
            return getNullableResult(rs, columnIndex);
        } catch (Exception e) {
            throw new ResultMapException("Error attempting to get column #" + columnIndex + " from result set.  Cause: " + e, e);
        }
    }

    @Override
    public GXJsonNodeValue getResult(CallableStatement cs, int columnIndex) throws SQLException {
        try {
            return getNullableResult(cs, columnIndex);
        } catch (Exception e) {
            throw new ResultMapException("Error attempting to get column #" + columnIndex + " from callable statement.  Cause: " + e, e);
        }
    }
}
