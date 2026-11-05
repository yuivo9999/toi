# ToImg release (R8) 规则

# 保留行号与反射元数据
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# ---- Room 数据库全部组件防混淆（Room 运行时通过反射实例化 ToImgDatabase_Impl）----
-keep class * extends androidx.room.RoomDatabase {
    <init>();
    *;
}
-keep class * extends androidx.room.migration.Migration { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.toimg.app.data.local.** { *; }
-dontwarn androidx.room.paging.**

# ---- 核心业务实体与模型防混淆（保留字段与 getter/setter）----
-keep class com.toimg.app.domain.model.** { *; }
-keepclassmembers enum com.toimg.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Android 四大组件与前台服务 ----
-keep class com.toimg.app.ToImgApp { *; }
-keep class com.toimg.app.MainActivity { *; }
-keep class com.toimg.app.core.service.RecognitionService { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
    *;
}

# ---- OkHttp / Okio ----
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- ML Kit / Vision ----
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ---- Kotlin 协程与 Flow ----
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.debug.**
