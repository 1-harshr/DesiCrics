plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.harsh.desicrics"
version = "1.0.0"
application {
    mainClass = "com.harsh.desicrics.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverAuth)
    // Brings java-jwt and jwks-rsa, used to verify Firebase ID tokens against Google's public keys.
    implementation(libs.ktor.serverAuthJwt)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    runtimeOnly(libs.h2)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.ktor.clientContentNegotiation)
}
