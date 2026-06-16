package com.sakena

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SakenaApplication

fun main(args: Array<String>) {
    runApplication<SakenaApplication>(*args)
}
