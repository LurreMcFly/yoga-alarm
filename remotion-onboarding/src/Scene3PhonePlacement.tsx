import {
  AbsoluteFill,
  Easing,
  Img,
  Interactive,
  interpolate,
  staticFile,
  useCurrentFrame,
} from "remotion";

const FrameCorner: React.FC<{
  left: number;
  top: number;
  rotate: string;
}> = ({left, top, rotate}) => {
  const frame = useCurrentFrame();

  return (
    <div
      style={{
        position: "absolute",
        left,
        top,
        width: 78,
        height: 78,
        borderTop: "10px solid #65e8a7",
        borderLeft: "10px solid #65e8a7",
        borderRadius: "18px 0 0 0",
        opacity: interpolate(frame, [52, 72, 145, 174], [0, 0.95, 0.95, 0], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: Easing.bezier(0.16, 1, 0.3, 1),
        }),
        scale: interpolate(frame, [52, 78], [0.78, 1], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: Easing.spring({damping: 16}),
          output: "perceptual-scale",
        }),
        rotate,
        filter: "drop-shadow(0 3px 10px rgba(25, 115, 83, 0.36))",
      }}
    />
  );
};

const Footstep: React.FC<{
  delay: number;
  left: number;
  top: number;
  rotate: string;
}> = ({delay, left, top, rotate}) => {
  const frame = useCurrentFrame();

  return (
    <div
      style={{
        position: "absolute",
        left,
        top,
        width: 22,
        height: 46,
        borderRadius: "50% 50% 44% 44%",
        backgroundColor: "rgba(255, 255, 255, 0.88)",
        opacity: interpolate(
          frame,
          [delay, delay + 8, 112, 138],
          [0, 0.9, 0.9, 0],
          {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          },
        ),
        scale: interpolate(frame, [delay, delay + 10], [0.65, 1], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: Easing.spring({damping: 14}),
          output: "perceptual-scale",
        }),
        rotate,
        boxShadow: "0 3px 12px rgba(87, 52, 71, 0.2)",
      }}
    />
  );
};

export const Scene3PhonePlacement: React.FC = () => {
  const frame = useCurrentFrame();

  return (
    <AbsoluteFill style={{backgroundColor: "#efd1b4", overflow: "hidden"}}>
      <Img
        name="Phone placement illustration"
        src={staticFile("scene-3-phone-placement.png")}
        style={{
          position: "absolute",
          inset: -24,
          width: 1128,
          height: 1128,
          objectFit: "cover",
          scale: interpolate(
            frame,
            [0, 18, 72, 142, 179],
            [1.09, 1.09, 1.01, 1.01, 1.09],
            {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(0.45, 0, 0.55, 1),
              output: "perceptual-scale",
            },
          ),
          translate: interpolate(
            frame,
            [0, 18, 72, 142, 179],
            ["-42px 22px", "-42px 22px", "0px 0px", "0px 0px", "-42px 22px"],
            {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(0.45, 0, 0.55, 1),
            },
          ),
          filter: `brightness(${interpolate(
            frame,
            [0, 72, 142, 179],
            [0.94, 1.04, 1.04, 0.94],
            {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(0.45, 0, 0.55, 1),
            },
          )}) saturate(1.02)`,
        }}
      />

      <Interactive.Div
        name="Phone focus glow"
        style={{
          position: "absolute",
          left: 880,
          top: 530,
          width: 190,
          height: 220,
          borderRadius: 64,
          background:
            "radial-gradient(circle, rgba(255, 209, 105, 0.5) 0%, rgba(255, 188, 65, 0.2) 46%, rgba(255, 188, 65, 0) 74%)",
          opacity: interpolate(frame, [0, 12, 42, 66, 145, 176], [0.3, 0.95, 0.95, 0.28, 0.28, 0.3], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
          scale: interpolate(frame % 30, [0, 15, 29], [0.92, 1.08, 0.92], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.45, 0, 0.55, 1),
            output: "perceptual-scale",
          }),
          filter: "blur(10px)",
        }}
      />

      <svg
        width="1080"
        height="1080"
        viewBox="0 0 1080 1080"
        style={{position: "absolute", inset: 0}}
      >
        <polygon
          points="982,625 244,58 220,1020"
          fill="rgba(101, 232, 167, 0.10)"
          opacity={interpolate(frame, [30, 62, 145, 176], [0, 1, 1, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          })}
        />
        <path
          d="M 982 625 L 244 58 M 982 625 L 220 1020"
          fill="none"
          stroke="rgba(101, 232, 167, 0.72)"
          strokeWidth="6"
          strokeLinecap="round"
          strokeDasharray="1200"
          strokeDashoffset={interpolate(frame, [30, 72], [1200, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          })}
          opacity={interpolate(frame, [28, 42, 145, 176], [0, 1, 1, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          })}
        />
        <path
          d="M 884 810 C 790 900, 700 932, 590 942"
          fill="none"
          stroke="rgba(255, 255, 255, 0.82)"
          strokeWidth="8"
          strokeLinecap="round"
          strokeDasharray="4 24"
          strokeDashoffset={interpolate(frame, [18, 70], [330, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          })}
          opacity={interpolate(frame, [18, 34, 112, 140], [0, 0.9, 0.9, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          })}
        />
      </svg>

      <Footstep delay={28} left={772} top={840} rotate="58deg" />
      <Footstep delay={40} left={700} top={887} rotate="68deg" />
      <Footstep delay={52} left={616} top={916} rotate="78deg" />

      <FrameCorner left={218} top={42} rotate="0deg" />
      <FrameCorner left={594} top={42} rotate="90deg" />
      <FrameCorner left={218} top={934} rotate="-90deg" />
      <FrameCorner left={594} top={934} rotate="180deg" />

      <Interactive.Div
        name="Full body confirmed"
        style={{
          position: "absolute",
          left: 592,
          top: 76,
          width: 92,
          height: 92,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          borderRadius: "50%",
          color: "white",
          backgroundColor: "#38c98b",
          border: "8px solid rgba(255, 255, 255, 0.92)",
          boxShadow: "0 10px 28px rgba(27, 122, 86, 0.34)",
          fontFamily: "Arial, sans-serif",
          fontSize: 58,
          fontWeight: 800,
          lineHeight: 1,
          opacity: interpolate(frame, [88, 102, 145, 174], [0, 1, 1, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
          scale: interpolate(frame, [88, 108], [0.45, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({damping: 12}),
            output: "perceptual-scale",
          }),
        }}
      >
        ✓
      </Interactive.Div>

      <Interactive.Div
        name="Camera lens pulse"
        style={{
          position: "absolute",
          left: 965,
          top: 604,
          width: 34,
          height: 34,
          borderRadius: "50%",
          border: "7px solid white",
          backgroundColor: "#38c98b",
          boxShadow: "0 0 0 8px rgba(56, 201, 139, 0.28)",
          opacity: interpolate(frame, [24, 38, 145, 176], [0, 1, 1, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          scale: interpolate(frame % 30, [0, 15, 29], [0.88, 1.12, 0.88], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.45, 0, 0.55, 1),
            output: "perceptual-scale",
          }),
        }}
      />

      <AbsoluteFill
        style={{
          boxShadow: "inset 0 0 90px rgba(74, 48, 58, 0.14)",
          pointerEvents: "none",
        }}
      />
    </AbsoluteFill>
  );
};
