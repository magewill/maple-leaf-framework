package cn.maple.canal.properties;

import cn.maple.core.framework.exception.GXBusinessException;

/**
 * Fails fast when {@code canal.config-source} points to a source that is not reachable from the
 * current class path.
 *
 * <p>Only created for the {@code nacos} value when the Nacos configuration annotation is absent,
 * because otherwise the application would silently fall back to defaults.</p>
 */
public class GXCanalConfigSourceValidator {
    /**
     * Rejects the unreachable configuration source.
     *
     * @param requested requested source
     * @throws GXBusinessException always
     */
    public GXCanalConfigSourceValidator(String requested) {
        throw new GXBusinessException("canal.config-source=" + requested
                + " requires Nacos configuration support on the class path; "
                + "use canal.config-source=local or add the Nacos dependency");
    }
}
