-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }

-assumenosideeffects public class androidx.compose.runtime.ComposerKt {
    void sourceInformation(androidx.compose.runtime.Composer,java.lang.String);
    void sourceInformationMarkerStart(androidx.compose.runtime.Composer,int,java.lang.String);
    void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
    boolean isTraceInProgress();
    void traceEventStart(int, java.lang.String);
    void traceEventEnd();
}

# Kotlinx Coroutines Rules
# https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/resources/META-INF/proguard/coroutines.pro

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.Signal
-dontwarn java.lang.ClassValue
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# https://github.com/Kotlin/kotlinx.coroutines/issues/2046
-dontwarn android.annotation.SuppressLint

# https://github.com/JetBrains/compose-jb/issues/2393
-dontnote kotlin.coroutines.jvm.internal.**
-dontnote kotlin.internal.**
-dontnote kotlin.jvm.internal.**
-dontnote kotlin.reflect.**
-dontnote kotlinx.coroutines.debug.internal.**
-dontnote kotlinx.coroutines.internal.**
-keep class kotlin.coroutines.Continuation
-keep class kotlinx.coroutines.CancellableContinuation
-keep class kotlinx.coroutines.channels.Channel
-keep class kotlinx.coroutines.CoroutineDispatcher
-keep class kotlinx.coroutines.CoroutineScope
# this is a weird one, but breaks build on some combinations of OS and JDK (reproduced on Windows 10 + Corretto 16)
-dontwarn org.graalvm.compiler.core.aarch64.AArch64NodeMatchRules_MatchStatementSet*

-dontnote com.sun.javafx.**

-keep class io.ktor.serialization.** { *; }
-keep class org.slf4j.** { *; }
-keep class org.slf4j2.** { *; }
-keep class coil3.** { *; }
-keep class org.apache.logging.log4j.** { *; } # class org.apache.logging.log4j.spi.StandardLevel not an enum

-keep class kotlinx.coroutines.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class androidx.datastore.** { *; }

-keep class uk.co.caprica.vlcj.** { *; } # native binding
-keep class com.sun.jna.** { *; } # native binding

-keep class ** extends me.him188.ani.datasources.api.subject.SubjectProvider { *; }
-keep class ** extends me.him188.ani.datasources.api.source.MediaSource { *; }
-keep class ** extends me.him188.ani.datasources.api.source.MediaSourceFactory { *; }
-keep class ** extends io.ktor.client.HttpClientEngineContainer { *; }

# Service loaders

-keep class me.him188.ani.datasources.** { *; } # has service config
-keep class ** extends uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider { *; }
-keep class org.apache.logging.slf4j.SLF4JServiceProvider { *; }
-keep class ** extends org.slf4j.spi.SLF4JServiceProvider { *; }

# Ktor related

-keep class me.him188.ani.danmaku.ani.client.AniDanmakuSenderImpl { *; } # Caused by: kotlinx.serialization.json.internal.JsonDecodingException: Expected class kotlinx.serialization.json.JsonObject as the serialized body of kotlinx.serialization.Polymorphic<List>, but had class kotlinx.serialization.json.JsonArray

-keep class io.ktor.** { *; } 
-keep class kotlin.reflect.jvm.internal.** { *; } 


# Access to compileOnly dependencies
-dontwarn aQute.bnd.**
-dontwarn okhttp3.internal.**
-dontwarn org.apache.logging.log4j.**	
-dontwarn reactor.blockhound.**
-dontwarn com.ctc.wstx.**
-dontwarn com.lmax.disruptor.**
-dontwarn com.sun.jna.internal.**
-dontwarn **

-dontnote ** # the configuration keeps the entry point...

-keep class me.him188.ani.app.data.persistent.database.AniDatabase_Impl # ClassNotFoundError
-keep class androidx.compose.runtime.SnapshotStateKt__DerivedStateKt { *; } # VerifyError
-keep class okio.Okio__JvmOkioKt { *; } # VerifyError
-keep class okio.Okio__OkioKt { *; } # VerifyError
-keep class okio.** # VerifyError
-keep class kotlinx.serialization.json.** { *; } # SerializationException: Serializer for class 'JsonLiteral' is not found.

-keep class kotlin.Metadata { *; }
-keepattributes Kotlin
-keepattributes Annotation
-keepattributes RuntimeVisibleAnnotations

-keep @kotlinx.serialization.Serializable class * {*;} # Somehow kotlinx-serialization 官方的规则仍然会导致 Serializer not found, 所以干脆直接都 keep

-keep class ** extends com.sun.jna.Structure { *; } # JNA C struct
-keep class ** extends com.sun.jna.Library { *; } # JNA

-verbose
