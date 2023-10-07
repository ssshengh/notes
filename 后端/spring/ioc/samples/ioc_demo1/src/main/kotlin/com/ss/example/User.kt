package com.ss.example

class User {
    var name: String = ""
        set(value) {
            field = value
            println("name set: $value")
        }

    var age: Int = 0

    var address: String = ""

    var dog: Dog? = null

    var cats = mutableListOf<Cat>()

    var favorites = mutableListOf<String>()

    var infos = mutableMapOf<String, String>()

    constructor(name: String, age: Int, address: String, cats: MutableList<Cat>) {
        println("User constructor")
        this.name = name
        this.age = age
        this.address = address
        this.cats = cats
        println(this.cats)
    }


    override fun toString(): String {
        return "User:{name=$name, age=$age, address=$address, dog=$dog, cats=$cats, favorites=$favorites, infos=$infos}"
    }
}