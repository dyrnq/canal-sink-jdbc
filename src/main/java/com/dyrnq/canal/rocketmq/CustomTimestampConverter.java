package com.dyrnq.canal.rocketmq;

import com.github.wz2cool.canal.utils.converter.DefaultTimestampConverter;

//import com.github.wz2cool.canal.utils.helper.DateHelper;
import com.github.wz2cool.canal.utils.model.MysqlDataType;

public class CustomTimestampConverter extends DefaultTimestampConverter {
    public CustomTimestampConverter() {
    }

    public String convert(MysqlDataType mysqlDataType, String value) {
        //return DateHelper.getCleanDateTime(value);
        return value;
    }
}
