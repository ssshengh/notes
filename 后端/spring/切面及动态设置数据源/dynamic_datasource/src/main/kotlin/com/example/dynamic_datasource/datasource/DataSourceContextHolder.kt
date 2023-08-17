package com.example.dynamic_datasource.datasource

/**
 * 存储当前线程使用的数据源名称
 */
object DataSourceContextHolder {
    val contextHolder = ThreadLocal<String>()

    fun setDataSourceType(dataSourceType: String) {
        contextHolder.set(dataSourceType)
    }

    fun getDataSourceType(): String? {
        return contextHolder.get()
    }

    fun clearDataSourceType() {
        contextHolder.remove()
    }
}