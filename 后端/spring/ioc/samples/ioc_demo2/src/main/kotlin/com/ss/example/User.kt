package com.ss.example

class User {
    var name: String = ""

    var age: Int = 0

    var cat2: Cat? = null

    constructor() {
        println("User constructor")
    }

    override fun toString(): String {
        return "User(name='$name', age=$age, cat=$cat2)"
    }
}