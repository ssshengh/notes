package com.example.dynamic_datasource.controller

import com.example.dynamic_datasource.datasource.DataSourceUtil
import com.example.dynamic_datasource.model.User
import com.example.dynamic_datasource.service.UserService
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DataSourceController @Autowired constructor(val userService: UserService) {
    companion object {
        private val logger = LoggerFactory.getLogger(DataSourceController::class.java)
    }

    /**
     * 修改数据源
     */
    @PostMapping("/setDsType")
    fun setDsType(dsType: String, session: HttpSession) {
        session.setAttribute(DataSourceUtil.DS_SESSION_KEY, dsType)
        logger.info("切换数据源：{}", dsType)
    }

    @GetMapping("/users")
    fun getUsers(): List<User> {
        logger.info("获取所有用户")
        return userService.getAllUsers()
    }
}