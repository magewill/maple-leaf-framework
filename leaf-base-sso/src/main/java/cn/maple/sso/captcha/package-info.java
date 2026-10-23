/**
 * Captcha generation and verification.
 *
 * <p>Rendering is implemented locally: {@link cn.maple.sso.captcha.Encoder},
 * {@link cn.maple.sso.captcha.GifEncoder} and {@link cn.maple.sso.captcha.Quant} are
 * inlined third-party GIF encoding code (animated-gif-lib-for-java lineage, Apache License
 * 2.0). They exist only to render captcha images and must not be reused as general purpose
 * image utilities.</p>
 *
 * <p>Instances of {@link cn.maple.sso.captcha.AbstractCaptcha} are mutable during setup and
 * frozen after the first render; share one instance per endpoint, never across endpoints
 * with different settings.</p>
 */
@org.jspecify.annotations.NullUnmarked
package cn.maple.sso.captcha;
