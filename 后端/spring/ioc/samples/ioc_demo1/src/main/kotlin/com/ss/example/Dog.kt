package com.ss.example

class Dog {
    var name: String = ""

    var age: Int = 0

    var weight: Double = 0.0

    constructor() {
        println("Dog constructor")
    }

    override fun toString(): String {
        return "Dog(name='$name', age=$age, weight=$weight)"
    }
}