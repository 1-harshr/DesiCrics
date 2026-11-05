package com.harsh.desicrics

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform