package com.ss.example

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Profile

class JavaConfig {
    @Bean
    @Conditional(WinFileCmdConditional::class)
    fun winShowFileCmd(): ShowFileCmd {
        return WinShowFileCmd()
    }

    @Bean
    @Conditional(LinuxFileCmdConditional::class)
    fun linuxShowFileCmd(): ShowFileCmd {
        return LinuxShowFileCmd()
    }

    @Bean
    @Profile("dev")
    fun devDataSource(): DataSource {
        val devDataSource = DataSource()
        devDataSource.url = "jdbc:mysql://localhost:3306/dev"
        devDataSource.userName = "dev"
        devDataSource.password = "dev"
        return devDataSource
    }

    @Bean
    @Profile("prod")
    fun prodDataSource(): DataSource {
        val devDataSource = DataSource()
        devDataSource.url = "jdbc:mysql://localhost:3306/prod"
        devDataSource.userName = "prod"
        devDataSource.password = "prod"
        return devDataSource
    }
}