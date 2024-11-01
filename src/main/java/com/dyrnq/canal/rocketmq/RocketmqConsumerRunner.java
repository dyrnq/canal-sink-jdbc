package com.dyrnq.canal.rocketmq;

import cn.hutool.core.util.ReUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wz2cool.canal.utils.SqlUtils;
import com.github.wz2cool.canal.utils.generator.AbstractSqlTemplateGenerator;
import com.github.wz2cool.canal.utils.generator.MysqlSqlTemplateGenerator;
import com.github.wz2cool.canal.utils.generator.PostgresqlSqlTemplateGenerator;
import com.github.wz2cool.canal.utils.model.CanalRowChange;
import com.github.wz2cool.canal.utils.model.FlatMessage;
import com.github.wz2cool.canal.utils.model.SqlTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class RocketmqConsumerRunner implements ApplicationRunner {

    private final MysqlSqlTemplateGenerator mysqlSqlTemplateGenerator;
    private final PostgresqlSqlTemplateGenerator postgresqlSqlTemplateGenerator;
    private final JdbcTemplate jdbcTemplate;


    @Value("${rocketmq.name-server}")
    private String rocketmqNameSrv;

    @Value("${rocketmq.consumer.group}")
    private String consumerGroup;
    @Value("${rocketmq.consumer.subscribe}")
    private String consumerSubscribe;


    protected String getDatabaseType() {
        String dbType = null;
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            dbType = metaData.getDatabaseProductName();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return dbType;
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(rocketmqNameSrv);
        consumer.setMaxReconsumeTimes(2);


        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(consumerSubscribe, "*");
        String databaseType = getDatabaseType();

        AbstractSqlTemplateGenerator sqlTemplateGenerator;

        if (ReUtil.isMatch("(?i)my(sql)?", databaseType)) {
            sqlTemplateGenerator = mysqlSqlTemplateGenerator;
            sqlTemplateGenerator.setTimestampConverter(new CustomTimestampConverter());
        } else if (ReUtil.isMatch("(?i)postgres(ql)?|pg(sql)?", databaseType)) {
            sqlTemplateGenerator = postgresqlSqlTemplateGenerator;
            sqlTemplateGenerator.setTimestampConverter(new CustomTimestampConverter());
        } else {
            throw new IllegalAccessException("not support" + databaseType);
        }

        log.info("sqlTemplateGenerator use {}.", sqlTemplateGenerator.getClass().getCanonicalName());


        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                String messageBody = new String(msg.getBody());
                String messageId = msg.getMsgId();
                String brokerName = msg.getBrokerName();
                int queueId = msg.getQueueId();
                int sysFlag = msg.getSysFlag();
                boolean isCompressed = (sysFlag & 0x1) != 0;

                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


                    final FlatMessage flatMessage = objectMapper.readValue(messageBody, FlatMessage.class);

                    if (flatMessage.getIsDdl() != null && flatMessage.getIsDdl().booleanValue()) {

                        if (StringUtils.startsWith(StringUtils.upperCase(flatMessage.getSql()), "DROP")) {

                        } else {
                            //jdbcTemplate.execute(flatMessage.getSql());
                            // skip execute DDL , MessageListenerConcurrently can not Concurrently exec CREATE table A and insert A
                            // if insert A after create table A, Table 'mytest2.xx' doesn't exist
                        }

                    } else {

                        final CanalRowChange rowChange = SqlUtils.getRowChange(flatMessage);
                        final List<SqlTemplate> sqlTemplates = sqlTemplateGenerator.listDMLSqlTemplates(rowChange);

                        if (sqlTemplates != null) {
                            for (SqlTemplate sqlTemplate : sqlTemplates) {
                                int updateResult = jdbcTemplate.update(sqlTemplate.getExpression(), sqlTemplate.getParams());
                                log.debug("updateResult:{}", updateResult);
                            }
                        }
                    }

                } catch (JsonMappingException e) {
                    log.error(e.getMessage());
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                } catch (JsonProcessingException e) {
                    log.error(e.getMessage());
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                } catch (org.springframework.dao.DuplicateKeyException duplicateKeyException) {

                } catch (DataAccessException e) {
                    if (e.getCause() instanceof java.sql.SQLSyntaxErrorException) {

                        String errorMessage = e.getMessage();
                        String regex = "Table +'([^']+)' already exists";

                        if (errorMessage.matches(regex)) {
                            //TODO
                        } else {
                            log.error(e.getMessage(), e);
                            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                        }
                    }

                } catch (Exception e) {
                    //TODO
                    log.error(e.getMessage(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                Map<String, String> headers = msg.getProperties();

                log.debug("headers: {}, isCompressed: {}, storeHost: {}, Broker: {} , Queue ID: {} , Message ID: {} , Received message: {}", headers, isCompressed, msg.getStoreHost().toString(), brokerName, queueId, messageId, messageBody);
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        consumer.start();

    }
}