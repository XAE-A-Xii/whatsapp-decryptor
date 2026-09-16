plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runDecrypt") {
    group = "application"
    description = "Runs the Crypt15 Decryption & Schema Inspection Runner"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.privacy.whatsappdecryptor.core.crypto.DecryptRunner")
    workingDir = rootProject.projectDir.parentFile
    args = listOf(
        File(workingDir, "msgstore.db.crypt15").absolutePath,
        File(workingDir, "backup_key.txt").absolutePath,
        File(workingDir, "msgstore_decrypted.db").absolutePath
    )
}

tasks.register<JavaExec>("runInventory") {
    group = "application"
    description = "Runs the Real Estate Inventory Extraction on the decrypted database"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.privacy.whatsappdecryptor.core.inventory.InventoryRunner")
    workingDir = rootProject.projectDir.parentFile
    args = listOf(
        File(workingDir, "msgstore_decrypted.db").absolutePath,
        File(workingDir, "Master_Important_Dealer_Inventory_test.csv").absolutePath
    )
}

kotlin {
    jvmToolchain(21)
}
