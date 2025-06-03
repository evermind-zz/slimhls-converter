# slimhls-converter
A lightweight Android library for converting HLS (TS) segments to MP4 using
a minimal FFmpeg binary (~3.7 MB total for four architectures).
The AAR size is approximately 3 MB.

## Supported Platforms

- **Build**: Linux (Windows not tested, may work)
- **Target**: Android (ARMv7-A, ARM64-v8a, x86, x86_64)

## Quick Usage Example
```java
package com.example.myapp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.github.evermindzz.slimhls.converter.RunFFmpeg;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Thread(() -> {
            String[] params = new String[] {
                "-err_detect",
                "ignore_err",
                "-i \"concat:/sdcard/input_hls/segments/seg0.ts|/sdcard/input_hls/segments/seg1.ts|/sdcard/input_hls/segments/seg2.ts|/sdcard/input_hls/segments/seg3.ts|/sdcard/input_hls/segments/seg4.ts\",
                "-c:v copy",
                "-c:a copy",
                "/sdcard/output.mp4"
            };
            RunFFmpeg runFFmpeg = new RunFFmpeg(this);
            try {
                int exitCode = runFFmpeg.execute(params);
                if (exitCode == 0) {
                    // Handle success
                } else {
                    // Handle failure (check logs)
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }
}
```

### Add as a Dependency
#### Gradle
Include slimhls-converter in your `build.gradle` file:

```gradle
dependencies {
    implementation 'com.evermind-zz:slimhls-converter:1.0.0' // Replace with the latest version
}
```

#### Repository
Ensure the Maven Central repository is in your project:

```gradle
repositories {
    mavenCentral()
}
```

## Building from Source

The library should also work on Android SDK 19 (KitKat). To use it with SDK 19, ensure
desugaring is enabled in your app, as some Java 8 time classes are utilized.

## Setup
1. Clone the repository.
2. Set the `ANDROID_NDK_HOME` environment variable to your NDK path.
3. Run `./gradlew assembleRelease` to build the AAR.

## Contributing
Contributions are welcome! Follow these steps:
1. Fork the repository.
2. Create a new branch for your changes.
3. Submit a pull request with clear commit messages.

## License
This project is licensed under the GPLv3. See the [LICENSE](LICENSE)
file for details.

## Contact
For issues, feature requests, or questions, please open an issue on the
[GitHub repository](https://github.com/evermind-zz/slimhls-converter).
