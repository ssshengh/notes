package com.example.dynamic_datasource.annotation

import com.alibaba.druid.pool.DruidDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import java.sql.Driver
import javax.sql.DataSource

/**
 * 读取配置文件中的数据源配置，初始化DruidDataSource对象作为实际的数据源
 */
@ConfigurationProperties(prefix = "spring.datasource")
class DruidProperties {
    var type: String? = null
    var driverClassName: String? = null
    var ds: Map<String, Map<String, String>>? = null
    var initialSize = 0
    var minIdle = 0
    var maxActive = 0
    var maxWait: Long = 0
    var connectTime: Long = 0
    var timeBetweenEvictionRunsMillis: Long = 0
    var minEvictableIdleTimeMillis: Long = 0
    var maxEvictableIdleTimeMillis: Long = 0
    var validationQuery: String? = null
    var testWhileIdle = true
    var testOnBorrow = false
    var testOnReturn = false
}