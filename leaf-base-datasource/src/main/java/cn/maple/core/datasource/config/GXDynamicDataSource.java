package cn.maple.core.datasource.config;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class GXDynamicDataSource extends AbstractRoutingDataSource implements DisposableBean {
    private final List<DruidDataSource> managedDataSources = new ArrayList<>();

    @Override
    protected Object determineCurrentLookupKey() {
        return GXDynamicContextHolder.peek();
    }

    public void setManagedDataSources(Collection<DruidDataSource> dataSources) {
        managedDataSources.clear();
        if (dataSources != null) {
            managedDataSources.addAll(dataSources);
        }
    }

    @Override
    public void destroy() {
        for (DruidDataSource dataSource : managedDataSources) {
            try {
                dataSource.close();
            } catch (RuntimeException e) {
                log.warn("Failed to close managed datasource", e);
            }
        }
        managedDataSources.clear();
    }
}
