package com.ss.example

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata
import java.util.*

class WinFileCmdConditional: Condition {
    /**
     * 作为条件判断的方法，返回true则表示满足条件，返回false则表示不满足条件，不满足条件的bean不会被注册到容器中
     */
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val osName = context.environment.getProperty("os.name")
        return osName != null && osName.lowercase(Locale.getDefault()).contains("windows")
    }
}