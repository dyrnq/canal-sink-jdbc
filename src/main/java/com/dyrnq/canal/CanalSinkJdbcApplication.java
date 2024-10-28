package com.dyrnq.canal;

import com.github.wz2cool.canal.utils.generator.MysqlSqlTemplateGenerator;
import com.github.wz2cool.canal.utils.generator.PostgresqlSqlTemplateGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class CanalSinkJdbcApplication {
    public static void main(String[] args) {
        SpringApplication.run(CanalSinkJdbcApplication.class, args);
    }

    @Bean
    public MysqlSqlTemplateGenerator getMysqlSqlTemplateGenerator() {
        return new MysqlSqlTemplateGenerator();
    }

    @Bean
    public PostgresqlSqlTemplateGenerator getPostgresqlSqlTemplateGenerator() {
        return new PostgresqlSqlTemplateGenerator();
    }
}
