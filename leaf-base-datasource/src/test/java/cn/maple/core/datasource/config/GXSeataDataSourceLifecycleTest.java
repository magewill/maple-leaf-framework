package cn.maple.core.datasource.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GXSeataDataSourceLifecycleTest {
    @Test
    void destroyClosesManagedPoolsExactlyOnce() {
        CountingDruidDataSource first = new CountingDruidDataSource();
        CountingDruidDataSource second = new CountingDruidDataSource();
        GXDynamicDataSource dynamicDataSource = new GXDynamicDataSource();
        dynamicDataSource.setManagedDataSources(List.of(first, second));

        dynamicDataSource.destroy();
        dynamicDataSource.destroy();

        assertThat(first.closeCount).isEqualTo(1);
        assertThat(second.closeCount).isEqualTo(1);
    }

    private static final class CountingDruidDataSource extends DruidDataSource {
        private int closeCount;

        @Override
        public void close() {
            closeCount++;
        }
    }
}
