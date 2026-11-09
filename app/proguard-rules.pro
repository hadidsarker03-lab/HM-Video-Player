# HM Player — R8 নিয়ম
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { **[] $VALUES; public *; }

# Manifest-এ নাম দিয়ে ডাকা ক্লাস (widget, service, activity)
-keep class com.example.videoplayer.HMWidget { *; }
-keep class com.example.videoplayer.PlaybackService { *; }
-keep class * extends android.app.Activity

# লিরিক্স/ডেটা ক্লাস নিরাপদ রাখা
-keep class com.example.videoplayer.Lyrics$Line { *; }
-keep class com.example.videoplayer.Video { *; }

# ঐচ্ছিক লাইব্রেরির অনুপস্থিত ক্লাস নিয়ে অভিযোগ বন্ধ
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
