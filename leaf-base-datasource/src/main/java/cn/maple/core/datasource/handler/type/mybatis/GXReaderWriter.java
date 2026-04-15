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

import cn.maple.core.framework.util.GXSpringContextUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TreeNode;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;

final class GXReaderWriter {
    private static final ObjectReader READER;

    private static final ObjectWriter WRITER;

    static {
        ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
        assert objectMapper != null;
        READER = objectMapper.reader()
                .with(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)    // 允许未引号的字段名
                .with(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS) // 允许数字前导零
                .with(JsonReadFeature.ALLOW_SINGLE_QUOTES);           // 允许单引号

        WRITER = objectMapper.writer();
    }

    private GXReaderWriter() {
    }

    static JsonNode readTree(String json) throws JacksonException {
        return READER.readTree(json);
    }

    static String write(TreeNode tree) throws JacksonException {
        return WRITER.writeValueAsString(tree);
    }
}
