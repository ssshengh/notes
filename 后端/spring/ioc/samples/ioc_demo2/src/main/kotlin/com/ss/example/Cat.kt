package com.ss.example

class Cat {
    var name: String = ""

    var age: Int = 0

    var weight: Double = 0.0

    constructor() {
        println("Cat constructor")
    }

    override fun toString(): String {
        return "Cat(name='$name', age=$age, weight=$weight)"
    }
}