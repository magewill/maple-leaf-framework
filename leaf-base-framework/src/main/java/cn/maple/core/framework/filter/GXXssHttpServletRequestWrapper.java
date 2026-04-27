package cn.maple.core.framework.filter;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XSS request wrapper.
 *
 * <p>Only JSON request bodies are consumed and cached. The cached body is already filtered,
 * so repeated calls to {@link #getInputStream()} or {@link #getReader()} do not rebuild large
 * intermediate strings and byte arrays.</p>
 */
public class GXXssHttpServletRequestWrapper extends HttpServletRequestWrapper {
    private static final GXHTMLFilter htmlFilter = new GXHTMLFilter();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Max JSON request body size: 50MB.
     */
    private static final int MAX_REQUEST_SIZE = 50 * 1024 * 1024;

    @Getter
    private final HttpServletRequest orgRequest;

    private final boolean jsonRequest;

    private byte[] cacheRequestBody;

    public GXXssHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        orgRequest = request;
        jsonRequest = isJsonRequest(request.getHeader(HttpHeaders.CONTENT_TYPE));

        if (!jsonRequest) {
            cacheRequestBody = new byte[0];
            return;
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_REQUEST_SIZE) {
            throw new IllegalArgumentException("Request body is too large, max size: " + MAX_REQUEST_SIZE + " bytes");
        }

        try {
            byte[] rawBody = readBytesLimited(request.getInputStream(), MAX_REQUEST_SIZE);
            if (rawBody.length == 0) {
                cacheRequestBody = new byte[0];
            } else {
                cacheRequestBody = filterJsonBody(rawBody);
            }
        } catch (IOException e) {
            cacheRequestBody = new byte[0];
            throw e;
        }
    }

    public static HttpServletRequest getOrgRequest(HttpServletRequest request) {
        if (request instanceof GXXssHttpServletRequestWrapper) {
            return ((GXXssHttpServletRequestWrapper) request).getOrgRequest();
        }
        return request;
    }

    private static boolean isJsonRequest(String contentType) {
        if (CharSequenceUtil.isBlank(contentType)) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            String subtype = mediaType.getSubtype();
            return MediaType.APPLICATION_JSON.includes(mediaType) || subtype.endsWith("+json");
        } catch (InvalidMediaTypeException e) {
            return CharSequenceUtil.containsIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE);
        }
    }

    private byte[] filterJsonBody(byte[] rawBody) throws IOException {
        if (isBlank(rawBody)) {
            return rawBody;
        }

        try (JsonParser parser = OBJECT_MAPPER.getFactory().createParser(rawBody);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream(rawBody.length);
             JsonGenerator generator = OBJECT_MAPPER.getFactory().createGenerator(outputStream)) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                writeFilteredToken(parser, generator, token);
            }
            generator.flush();
            return outputStream.toByteArray();
        } catch (IOException e) {
            String json = new String(rawBody, StandardCharsets.UTF_8);
            return xssEncode(json).getBytes(StandardCharsets.UTF_8);
        }
    }

    private static boolean isBlank(byte[] body) {
        for (byte b : body) {
            if (!Character.isWhitespace((char) b)) {
                return false;
            }
        }
        return true;
    }

    private void writeFilteredToken(JsonParser parser, JsonGenerator generator, JsonToken token) throws IOException {
        switch (token) {
            case START_OBJECT:
                generator.writeStartObject();
                break;
            case END_OBJECT:
                generator.writeEndObject();
                break;
            case START_ARRAY:
                generator.writeStartArray();
                break;
            case END_ARRAY:
                generator.writeEndArray();
                break;
            case FIELD_NAME:
                generator.writeFieldName(parser.currentName());
                break;
            case VALUE_STRING:
                generator.writeString(xssEncode(parser.getValueAsString()));
                break;
            case VALUE_NUMBER_INT:
                generator.writeNumber(parser.getBigIntegerValue());
                break;
            case VALUE_NUMBER_FLOAT:
                generator.writeNumber(parser.getDecimalValue());
                break;
            case VALUE_TRUE:
                generator.writeBoolean(true);
                break;
            case VALUE_FALSE:
                generator.writeBoolean(false);
                break;
            case VALUE_NULL:
                generator.writeNull();
                break;
            case VALUE_EMBEDDED_OBJECT:
                generator.writeObject(parser.getEmbeddedObject());
                break;
            default:
                generator.copyCurrentEvent(parser);
        }
    }

    private static byte[] readBytesLimited(InputStream inputStream, int maxSize) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.min(maxSize, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int readLength;
        while ((readLength = inputStream.read(buffer)) != -1) {
            total += readLength;
            if (total > maxSize) {
                throw new IllegalArgumentException("Request body is too large, max size: " + maxSize + " bytes");
            }
            outputStream.write(buffer, 0, readLength);
        }
        return outputStream.toByteArray();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (!jsonRequest) {
            return super.getReader();
        }
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cacheRequestBody), StandardCharsets.UTF_8));
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (!jsonRequest) {
            return super.getInputStream();
        }
        return new CachedBodyServletInputStream(cacheRequestBody);
    }

    @Override
    public String getParameter(String name) {
        String filteredName = xssEncode(name);
        String value = super.getParameter(filteredName);
        if (CharSequenceUtil.isNotBlank(value)) {
            value = xssEncode(value);
        }
        return value;
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] parameters = super.getParameterValues(name);
        if (parameters == null || parameters.length == 0) {
            return null;
        }

        String[] filteredParameters = Arrays.copyOf(parameters, parameters.length);
        for (int i = 0; i < filteredParameters.length; i++) {
            if (filteredParameters[i] != null) {
                filteredParameters[i] = xssEncode(filteredParameters[i]);
            }
        }
        return filteredParameters;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> map = new LinkedHashMap<>();
        Map<String, String[]> parameters = super.getParameterMap();

        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            String[] values = entry.getValue();
            String[] filteredValues = Arrays.copyOf(values, values.length);
            for (int i = 0; i < filteredValues.length; i++) {
                if (filteredValues[i] != null) {
                    filteredValues[i] = xssEncode(filteredValues[i]);
                }
            }
            map.put(entry.getKey(), filteredValues);
        }
        return map;
    }

    @Override
    public String getHeader(String name) {
        String filteredName = xssEncode(name);
        String value = super.getHeader(filteredName);
        if (CharSequenceUtil.isNotBlank(value)) {
            value = xssEncode(value);
        }
        return value;
    }

    private String xssEncode(String input) {
        if (input == null) {
            return null;
        }
        return htmlFilter.filter(input);
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream inputStream;

        private CachedBodyServletInputStream(byte[] body) {
            inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Cached byte arrays are read synchronously.
        }

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return inputStream.read(b, off, len);
        }
    }
}
