plugins {
    kotlin("jvm")
    `java-library`
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    // 本地生成 VIP 收款地址二维码，避免把钱包地址交给第三方二维码图片服务。
    implementation("com.google.zxing:core:3.5.3")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
}
