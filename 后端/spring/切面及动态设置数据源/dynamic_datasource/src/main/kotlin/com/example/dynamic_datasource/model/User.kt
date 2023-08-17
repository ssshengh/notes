package com.example.dynamic_datasource.model

class User{
    var id: Int = 0
    var age: Int = 0
    var user_name: String? = null

    override fun toString(): String {
        return "------- User(id=$id, age=$age, userName=$user_name)"
    }
}