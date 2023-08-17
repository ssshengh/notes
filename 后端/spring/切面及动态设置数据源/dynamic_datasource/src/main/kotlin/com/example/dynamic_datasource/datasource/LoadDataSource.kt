package com.example.dynamic_datasource.datasource

import com.alibaba.druid.pool.DruidDataSourceFactory
import com.example.dynamic_datasource.annotation.DruidProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
@EnableConfigurationProperties(DruidProperties::class)
class LoadDataSource @Autowired constructor(
    private val druidProperties: DruidProperties) {

    fun loadDataSource(): Map<String, DataSource> {
        val ds = druidProperties.ds
        val dataSources = mutableMapOf<String, DataSource>()
        ds?.forEach { (key, value) ->
            val druidDataSource = DruidDataSourceFactory.createDataSource(value)
            dataSources[key] = druidDataSource
        }
        return dataSources
    }
}