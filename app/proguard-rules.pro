# Optional MediaPipe graph profiling/template APIs are not used by this app.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate

# Protobuf lite accesses generated message fields by name through reflection.
# Keep them in optimized releases so MediaPipe can initialize its pose detector.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# MediaPipe initializes Flogger from Graph's static initializer. Flogger discovers
# its caller from the runtime stack; inlining/merging its methods breaks startup
# with "no caller found on the stack" (ExceptionInInitializerError).
-keep class com.google.common.flogger.** { *; }
