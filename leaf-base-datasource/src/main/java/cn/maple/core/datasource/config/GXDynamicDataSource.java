package cn.maple.core.datasource.config;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Slf4j
public class GXDynamicDataSource extends AbstractRoutingDataSource implements DisposableBean {
    private final List<DruidDataSource> managedDataSources = new ArrayList<>();
    @Getter
    @Setter
    private String defaultDataSourceName;

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

    public boolean isDefaultDataSource(String dataSourceName) {
        if (Objects.equals(defaultDataSourceName, dataSourceName)) {
            return true;
        }
        DataSource defaultDataSource = getResolvedDefaultDataSource();
        return defaultDataSource != null && defaultDataSource == getResolvedDataSources().get(dataSourceName);
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
