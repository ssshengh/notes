package com.example.dynamic_datasource.datasource

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.stereotype.Component

@Component
class DynamicDataSource @Autowired constructor(
    loadDataSource: LoadDataSource): AbstractRoutingDataSource() {

    init {
        // 设置所有数据源
        val allDs = loadDataSource.loadDataSource()
        super.setTargetDataSources(allDs as Map<Any?, Any?>)
        // 当没有 @DataSource 注解时，使用默认数据源
        allDs[DataSourceUtil.DEFAULT_DATASOURCE_NAME]?.let { super.setDefaultTargetDataSource(it) }

        super.afterPropertiesSet()
    }

    /**
     * 这个方法能够返回数据源的名称，系统通过该名称来尝试获取到数据源
     */
    override fun determineCurrentLookupKey(): Any? {
        return DataSourceContextHolder.getDataSourceType()
    }
}