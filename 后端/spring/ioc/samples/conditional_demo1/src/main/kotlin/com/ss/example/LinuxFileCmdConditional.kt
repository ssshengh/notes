package com.ss.example

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class LinuxFileCmdConditional: Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val osName = context.environment.getProperty("os.name")
        return osName != null && osName.lowercase().contains("linux")
    }
}