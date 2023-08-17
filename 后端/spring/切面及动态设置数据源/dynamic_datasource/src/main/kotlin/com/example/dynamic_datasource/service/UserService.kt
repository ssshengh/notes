package com.example.dynamic_datasource.service

import com.example.dynamic_datasource.annotation.DataSource
import com.example.dynamic_datasource.mapper.UserMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

//@DataSource("slave")
@Service
class UserService @Autowired constructor(
    private val userMapper: UserMapper,
) {
    fun getAllUsers() = userMapper.getAllUsers()
}
