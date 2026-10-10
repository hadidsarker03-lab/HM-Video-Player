# ভিডিও প্লেয়ার – সেটআপ

## app/build.gradle (dependencies)

```gradle
dependencies {
    implementation "androidx.core:core-ktx:1.13.1"
    implementation "androidx.appcompat:appcompat:1.7.0"
    implementation "androidx.activity:activity-ktx:1.9.0"
    implementation "com.google.android.material:material:1.12.0"
    implementation "androidx.recyclerview:recyclerview:1.3.2"
    implementation "androidx.media3:media3-exoplayer:1.3.1"
    implementation "androidx.media3:media3-ui:1.3.1"
    implementation "com.github.bumptech.glide:glide:4.16.0"
}
```

`compileSdk 34`, `minSdk 24` বা তার বেশি রাখুন।

## মূল কোডে যেসব ভুল ছিল

1. **AndroidManifest-এ পারমিশন নেই** – `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE` ও `INTERNET` লাগবে।
2. **অ্যাপে ফিরে এলে কালো স্ক্রিন** – `onStop`-এ প্লেয়ার release হতো, কিন্তু `onStart`-এ আবার তৈরি হতো না। এখন `onStart`-এ তৈরি ও `onStop`-এ release হয়, আগের পজিশনসহ।
3. **সোয়াইপ জেসচার কাজ করত না** – `PlayerView` টাচ ইভেন্ট নিজেই নিয়ে নিত। এখন `PlayerView`-এ `GestureDetector` বসানো হয়েছে।
4. **শুধু প্রথম ভিডিও চলত** – এখন সব ভিডিওর লিস্ট (থাম্বনেইলসহ) দেখায়।
5. **মেইন থ্রেডে ডাটাবেস কুয়েরি** – এখন ব্যাকগ্রাউন্ড থ্রেডে চলে।
6. **ডেপ্রিকেটেড `onRequestPermissionsResult`** – `ActivityResultContracts.RequestPermission` ব্যবহার করা হয়েছে।
7. **স্ক্রিন ঘোরালে প্লেয়ার রিসেট** – `configChanges` যোগ করা হয়েছে।
8. **ভিডিওর শেষের পরে seek** – `coerceIn(0, duration)` দিয়ে সীমা ঠিক করা হয়েছে।

## নতুন ফিচার

- ডাবল ট্যাপ: বামে ১০s পিছনে, ডানে ১০s সামনে
- অনুভূমিক সোয়াইপ: সিক
- বাম পাশে উপর-নিচ সোয়াইপ: ব্রাইটনেস
- ডান পাশে উপর-নিচ সোয়াইপ: ভলিউম
- ফুলস্ক্রিন (immersive) প্লেয়ার, ডার্ক থিম
