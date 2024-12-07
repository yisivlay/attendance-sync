package com.base.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author YISivlay
 */
@Configuration
@EnableTransactionManagement
public class Connection {
    private final static Logger logger = LoggerFactory.getLogger(Connection.class);

    private final Dotenv env = Dotenv.configure().directory("./").ignoreIfMissing().load();

    @Bean
    public DataSource dataSource() {

        String address = env.get("ADDRESS", "localhost");
        String port = env.get("PORT", "3306");
        String protocol = env.get("PROTOCOL", "jdbc");
        String subProtocol = env.get("SUB_PROTOCOL", "mysql");
        String driverClassName = env.get("DRIVER_CLASS_NAME", "com.mysql.cj.jdbc.Driver");
        String database = env.get("DATABASE", "xyz");
        String username = env.get("DATABASE_USERNAME", "root");
        String password = env.get("DATABASE_PASSWORD", "");
        String url = protocol + ":" + subProtocol + "://" + address + ":" + port + "/" + database;

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(url);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        try {
            dataSource.getConnection();
            logger.info("Successfully connected to database at {}", url);
        } catch (Exception e) {
            logger.error("Failed to connect to database at {}", url, e);
        }
        return dataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.base");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.format_sql", "true");
        jpaProperties.put("hibernate.current_session_context_class", "thread");
        jpaProperties.put("hibernate.hbm2ddl.auto", "update");
        jpaProperties.put("hibernate.c3p0.min_size", "5");
        jpaProperties.put("hibernate.c3p0.max_size", "20");
        jpaProperties.put("hibernate.c3p0.timeout", "300");
        jpaProperties.put("hibernate.c3p0.max_statements", "50");
        jpaProperties.put("hibernate.c3p0.idle_test_period", "3000");

        em.setJpaProperties(jpaProperties);
        return em;
    }
}
