package com.podsadowski.warehouse.config;

import com.podsadowski.warehouse.api.dto.SystemMode;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSourceModeHolder dataSourceModeHolder(Environment environment) {
        boolean demoProfile = Arrays.asList(environment.getActiveProfiles()).contains("demo");
        return new DataSourceModeHolder(demoProfile ? SystemMode.DEMO : SystemMode.REAL);
    }

    @Bean
    @ConfigurationProperties("warehouse.datasource.real")
    public JdbcConnectionSettings realDataSourceSettings() {
        return new JdbcConnectionSettings();
    }

    @Bean
    @ConfigurationProperties("warehouse.datasource.demo")
    public JdbcConnectionSettings demoDataSourceSettings() {
        return new JdbcConnectionSettings();
    }

    @Bean
    public DataSource realDataSource(
            @Qualifier("realDataSourceSettings") JdbcConnectionSettings settings
    ) {
        return createHikari(settings, "warehouse-real");
    }

    @Bean
    public DataSource demoDataSource(
            @Qualifier("demoDataSourceSettings") JdbcConnectionSettings settings
    ) {
        return createHikari(settings, "warehouse-demo");
    }

    @Bean
    @Primary
    public DataSource dataSource(
            DataSourceModeHolder modeHolder,
            @Qualifier("realDataSource") DataSource realDataSource,
            @Qualifier("demoDataSource") DataSource demoDataSource
    ) {
        RoutingDataSource routing = new RoutingDataSource(modeHolder);
        Map<Object, Object> targets = new HashMap<>();
        targets.put(SystemMode.REAL, realDataSource);
        targets.put(SystemMode.DEMO, demoDataSource);
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(
                modeHolder.isDemo() ? demoDataSource : realDataSource
        );
        routing.afterPropertiesSet();
        return routing;
    }

    private static DataSource createHikari(JdbcConnectionSettings settings, String poolName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(settings.getUrl());
        dataSource.setUsername(settings.getUsername());
        dataSource.setPassword(settings.getPassword());
        dataSource.setPoolName(poolName);
        return dataSource;
    }

    @Getter
    @Setter
    public static class JdbcConnectionSettings {
        private String url;
        private String username;
        private String password;
    }
}
