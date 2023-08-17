package com.example.dynamic_datasource.mapper

import com.example.dynamic_datasource.model.User
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Select


@Mapper
interface UserMapper {

    @Select("select * from user")
    fun getAllUsers(): List<User>
}