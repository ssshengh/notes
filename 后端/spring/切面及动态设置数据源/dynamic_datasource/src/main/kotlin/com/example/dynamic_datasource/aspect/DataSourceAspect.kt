package com.example.dynamic_datasource.aspect

import com.example.dynamic_datasource.annotation.DataSource
import com.example.dynamic_datasource.datasource.DataSourceContextHolder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Aspect
@Order(11)  // 数字越小，优先级越高，越先执行，我们期望这个切面在 GlobalDataSourceAspect 之后执行，可以实现如果有注解就使用注解的数据源，没有注解就使用全局数据源
class DataSourceAspect {

    /**
     * 切点: 所有配置 DataSource 注解的方法都会被拦截
     *  @within(com.example.dynamic_datasource.annotation.DataSource) 或者类上有 DataSource 注解，将其中的方法拦截下来
     */
    @Pointcut("@annotation(com.example.dynamic_datasource.annotation.DataSource) || @within(com.example.dynamic_datasource.annotation.DataSource)")
    fun pointCut() {
        println("========  pointCut =======")
    }

    /**
     * 环绕通知, 确定使用哪个数据源
     */
    @Around("pointCut()")
    fun around(pdj: ProceedingJoinPoint): Any? {
        // 获取方法上的注解
        val dataSource = getDataSource(pdj)
        dataSource?.let {
            // 获取数据源的名称
            val dataSourceName = dataSource.value
            DataSourceContextHolder.setDataSourceType(dataSourceName)
        }

        try {
            return pdj.proceed()
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        } finally {
            DataSourceContextHolder.clearDataSourceType()
        }
        return null
    }

    private fun getDataSource(pdj: ProceedingJoinPoint): DataSource? {
        val signature = pdj.signature as MethodSignature
        // 获取方法上的注解
        val annotation = AnnotationUtils.findAnnotation(signature.method, DataSource::class.java)
        if (annotation != null) {
            // 方法上有注解
            return annotation
        }
        // 方法上没有注解，获取类上的注解
        return AnnotationUtils.findAnnotation(signature.declaringType, DataSource::class.java)
    }
}