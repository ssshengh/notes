package com.example.dynamic_datasource

import com.example.dynamic_datasource.service.UserService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class DynamicDatasourceApplicationTests @Autowired constructor(
    private val userService: UserService
) {

    @Test
    fun contextLoads() {
        userService.getAllUsers().forEach { println(it) }
    }

}
