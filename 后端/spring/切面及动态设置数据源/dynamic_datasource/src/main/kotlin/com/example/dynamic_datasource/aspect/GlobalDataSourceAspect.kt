package com.example.dynamic_datasource.aspect

import com.example.dynamic_datasource.datasource.DataSourceContextHolder
import com.example.dynamic_datasource.datasource.DataSourceUtil
import com.example.dynamic_datasource.datasource.DynamicDataSource
import jakarta.servlet.http.HttpSession
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Aspect
@Component
@Order(10)
class GlobalDataSourceAspect @Autowired constructor(val session: HttpSession){

    // 通过切面来设置数据源, 其中对 service 包下的所有类与方法进行切面
    @Pointcut("execution(* com.example.dynamic_datasource.service.*.*(..))")
    fun pc() {}

    @Around("pc()")
    fun around(pjd: ProceedingJoinPoint): Any?{
        DataSourceContextHolder.setDataSourceType(session.getAttribute(DataSourceUtil.DS_SESSION_KEY) as String)
        try {
            return pjd.proceed()
        }catch (throwable: Throwable){
            throwable.printStackTrace()
        }finally {
            DataSourceContextHolder.clearDataSourceType()
        }
        return null
    }
}