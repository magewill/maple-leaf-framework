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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Value container that transfer JSON from/into DB.
 * Main feature of this container is lazy initializing while reading values from DB.
 * It will build JsonNode object only at first call of {@link GXJsonNodeValue#get()}
 * and sometimes {@link GXJsonNodeValue#isEmpty()}.
 */
public class GXJsonNodeValue implements Serializable {

    /**
     * Value container without any content.
     * You will not be able to call get() methods on this object.
     */
    public static final GXJsonNodeValue EMPTY = new GXJsonNodeValue();
    private static final long serialVersionUID = 745861884668365334L;
    private String source;

    private boolean dbSource;

    private transient JsonNode value;

    private GXJsonNodeValue() {
        this.source = null;
        this.value = null;
    }

    private GXJsonNodeValue(String source) {
        if (source != null) {
            source = source.trim();
            this.source = source.isEmpty() ? null : source;
        } else {
            this.source = null;
        }
        this.value = null;
    }

    private GXJsonNodeValue(JsonNode value) {
        this.value = value;
        this.source = null;
    }

    /**
     * Build value container from JsonNode object.
     * In this case {@link GXJsonNodeValue#get()} will never throw any exception.
     *
     * @param node JSON node or null
     */
    public static GXJsonNodeValue from(JsonNode node) {
        return node == null ? EMPTY : new GXJsonNodeValue(node);
    }

    /**
     * Build value container from JSON string.
     * NOTE if input is not valid JSON than exception in {@link GXJsonNodeValue#get()} will be thrown.
     *
     * @param json JSON string or null
     */
    public static GXJsonNodeValue from(String json) {
        if (json == null || json.isEmpty()) {
            return EMPTY;
        }
        json = json.trim();
        return json.isEmpty() ? EMPTY : new GXJsonNodeValue(json);
    }

    static GXJsonNodeValue fromDb(String json) {
        GXJsonNodeValue v = from(json);
        if (v.isPresent()) {
            v.dbSource = true;
        }
        return v;
    }

    /**
     * Test input value and return not null - value from input or empty object.
     */
    public static GXJsonNodeValue orEmpty(GXJsonNodeValue node) {
        return node == null || node.isNotPresent() ? EMPTY : node;
    }

    /**
     * Check if nested value is present (not null or empty JSON string).
     */
    public boolean isPresent() {
        return value != null || (source != null && !source.isEmpty());
    }

    /**
     * Opposite to {@link GXJsonNodeValue#isPresent()}.
     */
    public boolean isNotPresent() {
        return !isPresent();
    }

    /**
     * Return true if value is not present or if underlying JSON is empty object, array or null.
     * WARNING this method can throw same exceptions as {@link GXJsonNodeValue#get()} in a case if
     * source is invalid JSON string.
     */
    public boolean isEmpty() {
        if (!isPresent()) {
            return true;
        }
        JsonNode n = get();
        return (((n.isObject() || n.isArray()) && n.size() == 0) || n.isNull());
    }

    /**
     * Opposite to {@link GXJsonNodeValue#isEmpty()}.
     */
    public boolean isNotEmpty() {
        return !isEmpty();
    }

    /**
     * Return COPY of JSON node value (will parse node from string at first call).
     * WARNING if object constructed with invalid JSON string, exception will be thrown.
     *
     * @return Copy of valid JsonNode or MissingNode if no data.
     * @throws GXBusinessException On JSON parsing errors.
     */
    public JsonNode get() {
        if (!isPresent()) {
            return MissingNode.getInstance();
        }
        try {
            value = getNodeInstance();
        } catch (Exception ex) {
            throw new GXBusinessException("Can not parse JSON string. " + ex.getMessage(), ex);
        }
        return value.deepCopy();
    }

    /**
     * Same as {@link GXJsonNodeValue#get()}.
     * Created for compatibility with frameworks that works with object properties,
     * thus require get* methods.
     */
    public JsonNode getValue() {
        return get();
    }

    boolean hasDbSource() {
        return this.dbSource && this.source != null;
    }

    String getSource() {
        return this.source;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        if (this.source == null && this.value != null) {
            this.source = GXReaderWriter.write(this.value);
        }
        oos.defaultWriteObject();
    }

    private synchronized JsonNode getNodeInstance() throws IOException {
        if (value == null) {
            value = GXReaderWriter.readTree(source);
        }
        return value;
    }
}
