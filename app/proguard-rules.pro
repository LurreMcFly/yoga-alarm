# Optional MediaPipe graph profiling/template APIs are not used by this app.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate

# Protobuf lite accesses generated message fields by name through reflection.
# Keep them in optimized releases so MediaPipe can initialize its pose detector.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
