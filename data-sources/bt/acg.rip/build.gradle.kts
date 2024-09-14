/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
    `flatten-source-sets`
}

dependencies {
    api(projects.dataSources.api)

    api(libs.kotlinx.coroutines.core)
    api(libs.ktor.client.core)

    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.jsoup)
    implementation(libs.slf4j.api)
    implementation(projects.utils.logging)
}


//kotlin {
//    sourceSets.commonMain {
//        dependencies {
//            api(projects.dataSources.api)
//            implementation(projects.utils.ktorClient)
//            api(libs.kotlinx.coroutines.core)
//            implementation(libs.kotlinx.serialization.json)
//            implementation(libs.jsoup)
//            implementation(projects.utils.logging)
//        }
//    }
//}


tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // why is there a duplicate?
}