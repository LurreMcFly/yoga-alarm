# Optional MediaPipe graph profiling/template APIs are not used by this app.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate

# Protobuf lite accesses generated message fields by name through reflection.
# Keep them in optimized releases so MediaPipe can initialize its pose detector.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# MediaPipe's JNI code looks up framework fields and callbacks by name, including
# ProtoUtil.SerializedMessage.typeName and value when submitting camera frames.
# Preserve the Java/native interface, which R8 cannot infer from the shared library.
-keep class com.google.mediapipe.framework.** { *; }

# MediaPipe initializes Flogger from Graph's static initializer. Flogger discovers
# its caller from the runtime stack; inlining/merging its methods breaks startup
# with "no caller found on the stack" (ExceptionInInitializerError).
-keep class com.google.common.flogger.** { *; }
