package cn.maple.feign.config;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.cloud.openfeign.support.FeignEncoderProperties;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;

class GXFeignFallbackConfig {
    @Bean
    @ConditionalOnMissingBean(FeignHttpMessageConverters.class)
    FeignHttpMessageConverters feignHttpMessageConverters(
            ObjectProvider<@NonNull ClientHttpMessageConvertersCustomizer> customizers,
            ObjectProvider<@NonNull HttpMessageConverterCustomizer> cloudCustomizers) {
        return new FeignHttpMessageConverters(customizers, cloudCustomizers);
    }

    @Bean
    @ConditionalOnMissingBean(Encoder.class)
    Encoder feignEncoder(ObjectProvider<@NonNull FeignEncoderProperties> encoderPropertiesProvider,
                         ObjectProvider<@NonNull FeignHttpMessageConverters> messageConvertersProvider) {
        return new SpringEncoder(
                new SpringFormEncoder(),
                encoderPropertiesProvider.getIfAvailable(FeignEncoderProperties::new),
                messageConvertersProvider
        );
    }
}
