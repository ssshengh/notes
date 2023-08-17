package com.example.dynamic_datasource.annotation

import com.example.dynamic_datasource.datasource.DataSourceUtil

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
annotation class DataSource(
    val value: String = DataSourceUtil.DEFAULT_DATASOURCE_NAME,
)
